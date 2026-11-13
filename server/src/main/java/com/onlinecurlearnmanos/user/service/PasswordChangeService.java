package com.onlinecurlearnmanos.user.service;

import com.onlinecurlearnmanos.admin.service.AdminAuditService;
import com.onlinecurlearnmanos.common.api.PageResult;
import com.onlinecurlearnmanos.common.error.ErrorCode;
import com.onlinecurlearnmanos.common.exception.BusinessException;
import com.onlinecurlearnmanos.user.dto.PasswordChangeRequestResponse;
import com.onlinecurlearnmanos.user.dto.PasswordChangeReviewRequest;
import com.onlinecurlearnmanos.user.dto.PasswordChangeSubmitRequest;
import com.onlinecurlearnmanos.user.entity.PasswordChangeRequest;
import com.onlinecurlearnmanos.user.entity.SysUser;
import com.onlinecurlearnmanos.user.mapper.PasswordChangeRequestMapper;
import com.onlinecurlearnmanos.user.mapper.SysRoleMapper;
import com.onlinecurlearnmanos.user.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 密码变更审批：学生 / 教师只能提交申请，必须由管理员批准后新密码才生效。
 *
 * <p>申请里只保存新密码的 BCrypt 哈希；审批通过时原子替换 `password_hash` 并把
 * `token_version` 自增，使旧 token 立即失效。</p>
 */
@Service
@RequiredArgsConstructor
public class PasswordChangeService {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 72;

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final PasswordChangeRequestMapper requestMapper;
    private final PasswordEncoder passwordEncoder;
    private final AdminAuditService auditService;

    @Transactional
    public PasswordChangeRequestResponse submit(
            Long userId,
            PasswordChangeSubmitRequest request
    ) {
        SysUser user = requireUser(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "当前密码不正确");
        }
        String newPassword = request.newPassword();
        validateStrength(newPassword);
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "新密码不能与当前密码相同");
        }
        if (findPending(userId) != null) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "已有一条待审批的密码变更申请，请等待管理员处理"
            );
        }
        PasswordChangeRequest entity = new PasswordChangeRequest();
        entity.setUserId(userId);
        entity.setNewPasswordHash(passwordEncoder.encode(newPassword));
        entity.setStatus(PasswordChangeRequest.STATUS_PENDING);
        entity.setRequestedAt(LocalDateTime.now());
        requestMapper.insert(entity);
        return toResponses(List.of(requestMapper.selectById(entity.getId()))).get(0);
    }

    public PasswordChangeRequestResponse pending(Long userId) {
        PasswordChangeRequest pending = findPending(userId);
        return pending == null ? null : toResponses(List.of(pending)).get(0);
    }

    @Transactional
    public void cancel(Long userId, Long requestId) {
        int updated = requestMapper.update(
                null,
                new LambdaUpdateWrapper<PasswordChangeRequest>()
                        .eq(PasswordChangeRequest::getId, requestId)
                        .eq(PasswordChangeRequest::getUserId, userId)
                        .eq(PasswordChangeRequest::getStatus, PasswordChangeRequest.STATUS_PENDING)
                        .set(PasswordChangeRequest::getStatus, PasswordChangeRequest.STATUS_CANCELLED)
                        .set(PasswordChangeRequest::getReviewNote, "用户主动撤销")
                        .set(PasswordChangeRequest::getReviewedAt, LocalDateTime.now())
        );
        if (updated == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "申请不存在或已被处理");
        }
    }

    public PageResult<PasswordChangeRequestResponse> list(
            long page,
            long size,
            String status
    ) {
        long safePage = Math.max(page, 1);
        long safeSize = Math.min(Math.max(size, 1), 50);
        LambdaQueryWrapper<PasswordChangeRequest> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(status)) {
            wrapper.eq(
                    PasswordChangeRequest::getStatus,
                    status.trim().toUpperCase(Locale.ROOT)
            );
        }
        wrapper.orderByAsc(PasswordChangeRequest::getStatus)
                .orderByDesc(PasswordChangeRequest::getRequestedAt);
        Page<PasswordChangeRequest> result = requestMapper.selectPage(
                new Page<>(safePage, safeSize),
                wrapper
        );
        return new PageResult<>(
                toResponses(result.getRecords()),
                result.getTotal(),
                safePage,
                safeSize
        );
    }

    @Transactional
    public PasswordChangeRequestResponse review(
            Long adminId,
            Long requestId,
            PasswordChangeReviewRequest request
    ) {
        PasswordChangeRequest entity = requestMapper.selectById(requestId);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "密码变更申请不存在");
        }
        boolean approved = Boolean.TRUE.equals(request.approved());
        String note = normalizeNullable(request.note());
        int updated = requestMapper.update(
                null,
                new LambdaUpdateWrapper<PasswordChangeRequest>()
                        .eq(PasswordChangeRequest::getId, requestId)
                        .eq(PasswordChangeRequest::getStatus, PasswordChangeRequest.STATUS_PENDING)
                        .set(
                                PasswordChangeRequest::getStatus,
                                approved
                                        ? PasswordChangeRequest.STATUS_APPROVED
                                        : PasswordChangeRequest.STATUS_REJECTED
                        )
                        .set(PasswordChangeRequest::getReviewerId, adminId)
                        .set(PasswordChangeRequest::getReviewedAt, LocalDateTime.now())
                        .set(PasswordChangeRequest::getReviewNote, note)
        );
        if (updated == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "该申请已被处理");
        }
        if (approved) {
            int userUpdated = userMapper.update(
                    null,
                    new LambdaUpdateWrapper<SysUser>()
                            .eq(SysUser::getId, entity.getUserId())
                            .set(SysUser::getPasswordHash, entity.getNewPasswordHash())
                            .setSql("token_version = token_version + 1")
            );
            if (userUpdated == 0) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "申请对应的用户不存在");
            }
        }
        auditService.record(
                adminId,
                approved ? "PASSWORD_REQUEST_APPROVE" : "PASSWORD_REQUEST_REJECT",
                "SYS_USER",
                String.valueOf(entity.getUserId()),
                approved
                        ? "批准密码变更申请"
                        : "驳回密码变更申请：" + (note == null ? "未填写原因" : note)
        );
        return toResponses(List.of(requestMapper.selectById(requestId))).get(0);
    }

    private PasswordChangeRequest findPending(Long userId) {
        return requestMapper.selectOne(new LambdaQueryWrapper<PasswordChangeRequest>()
                .eq(PasswordChangeRequest::getUserId, userId)
                .eq(PasswordChangeRequest::getStatus, PasswordChangeRequest.STATUS_PENDING)
                .orderByDesc(PasswordChangeRequest::getRequestedAt)
                .last("LIMIT 1"));
    }

    private void validateStrength(String rawPassword) {
        if (rawPassword.length() < MIN_LENGTH || rawPassword.length() > MAX_LENGTH) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "新密码长度需在 " + MIN_LENGTH + " 到 " + MAX_LENGTH + " 位之间"
            );
        }
        boolean hasLetter = rawPassword.chars().anyMatch(Character::isLetter);
        boolean hasDigit = rawPassword.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "新密码需同时包含字母和数字");
        }
    }

    private SysUser requireUser(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return user;
    }

    private List<PasswordChangeRequestResponse> toResponses(
            List<PasswordChangeRequest> requests
    ) {
        if (requests.isEmpty()) {
            return List.of();
        }
        List<Long> userIds = requests.stream()
                .flatMap(request -> java.util.stream.Stream.of(
                        request.getUserId(),
                        request.getReviewerId()
                ))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, SysUser> users = userIds.isEmpty()
                ? Map.of()
                : userMapper.selectByIdsIncludingDeleted(userIds).stream()
                        .collect(Collectors.toMap(SysUser::getId, user -> user));
        return requests.stream()
                .map(request -> {
                    SysUser user = users.get(request.getUserId());
                    SysUser reviewer = request.getReviewerId() == null
                            ? null
                            : users.get(request.getReviewerId());
                    return new PasswordChangeRequestResponse(
                            request.getId(),
                            request.getUserId(),
                            user == null ? null : user.getUsername(),
                            user == null ? null : user.getDisplayName(),
                            user == null ? null : primaryRole(user.getId()),
                            request.getStatus(),
                            request.getRequestedAt(),
                            request.getReviewerId(),
                            reviewer == null ? null : reviewer.getDisplayName(),
                            request.getReviewedAt(),
                            request.getReviewNote()
                    );
                })
                .toList();
    }

    private String primaryRole(Long userId) {
        List<String> roles = roleMapper.selectRoleCodesByUserId(userId);
        if (roles.contains("ADMIN")) {
            return "ADMIN";
        }
        return roles.contains("TEACHER") ? "TEACHER" : "STUDENT";
    }

    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
