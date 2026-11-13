package com.onlinecurlearnmanos.admin.service;

import com.onlinecurlearnmanos.admin.dto.AdminUserResponse;
import com.onlinecurlearnmanos.cloudclass.entity.CloudClass;
import com.onlinecurlearnmanos.cloudclass.mapper.CloudClassMapper;
import com.onlinecurlearnmanos.common.api.PageResult;
import com.onlinecurlearnmanos.common.error.ErrorCode;
import com.onlinecurlearnmanos.common.exception.BusinessException;
import com.onlinecurlearnmanos.user.entity.SysUser;
import com.onlinecurlearnmanos.user.mapper.SysRoleMapper;
import com.onlinecurlearnmanos.user.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final AdminAuditService auditService;
    private final CloudClassMapper cloudClassMapper;

    public PageResult<AdminUserResponse> list(
            long page,
            long size,
            String role,
            Boolean enabled,
            String keyword
    ) {
        long safePage = Math.max(page, 1);
        long safeSize = Math.min(Math.max(size, 1), 50);
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (enabled != null) {
            wrapper.eq(SysUser::getStatus, Boolean.TRUE.equals(enabled) ? 1 : 0);
        }
        if (StringUtils.hasText(keyword)) {
            String normalized = keyword.trim();
            wrapper.and(condition -> condition
                    .like(SysUser::getUsername, normalized)
                    .or()
                    .like(SysUser::getDisplayName, normalized)
            );
        }
        if (StringUtils.hasText(role)) {
            wrapper.exists(
                    "SELECT 1 FROM sys_user_role ur "
                            + "JOIN sys_role r ON r.id = ur.role_id "
                            + "WHERE ur.user_id = sys_user.id "
                            + "AND r.code = {0} AND r.deleted = 0",
                    role.trim().toUpperCase()
            );
        }
        wrapper.orderByDesc(SysUser::getId);
        Page<SysUser> result = userMapper.selectPage(new Page<>(safePage, safeSize), wrapper);
        List<AdminUserResponse> records = result.getRecords().stream()
                .map(this::toResponse)
                .toList();
        return new PageResult<>(records, result.getTotal(), safePage, safeSize);
    }

    @Transactional
    public AdminUserResponse updateStatus(Long userId, boolean enabled, Long operatorId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        if (!enabled) {
            if (Objects.equals(userId, operatorId)) {
                throw new BusinessException(
                        ErrorCode.CONFLICT,
                        "不能停用当前登录的管理员账号"
                );
            }
            List<String> roles = roleMapper.selectRoleCodesByUserId(userId);
            if (roles.contains("ADMIN") && roleMapper.countOtherActiveAdmins(userId) == 0) {
                throw new BusinessException(
                        ErrorCode.CONFLICT,
                        "系统必须保留至少一个启用状态的管理员账号"
                );
            }
        }
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, userId)
                .set(SysUser::getStatus, enabled ? 1 : 0));
        auditService.record(
                operatorId,
                enabled ? "USER_ENABLE" : "USER_DISABLE",
                "SYS_USER",
                String.valueOf(userId),
                (enabled ? "启用账号：" : "停用账号：") + user.getUsername()
        );
        return toResponse(userMapper.selectById(userId));
    }

    @Transactional
    public void deleteAccount(
            Long userId,
            String reason,
            String confirmLoginId,
            Long operatorId
    ) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在或已被删除");
        }
        if (Objects.equals(userId, operatorId)) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "不能删除当前登录的管理员账号"
            );
        }
        String normalizedConfirm = confirmLoginId == null ? "" : confirmLoginId.trim();
        if (!Objects.equals(user.getUsername(), normalizedConfirm)) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "确认学号 / 工号与目标账号不一致"
            );
        }

        List<String> roles = roleMapper.selectRoleCodesByUserId(userId);
        if (roles.contains("ADMIN")
                && Integer.valueOf(1).equals(user.getStatus())
                && roleMapper.countOtherActiveAdmins(userId) == 0) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "系统必须保留至少一个启用状态的管理员账号"
            );
        }
        if (roles.contains("TEACHER")) {
            long activeClasses = cloudClassMapper.selectCount(
                    new LambdaQueryWrapper<CloudClass>()
                            .eq(CloudClass::getTeacherId, userId)
                            .eq(CloudClass::getStatus, "ACTIVE")
            );
            if (activeClasses > 0) {
                throw new BusinessException(
                        ErrorCode.CONFLICT,
                        "该教师仍负责 " + activeClasses + " 个进行中的云班级，请先归档或转交"
                );
            }
        }

        String normalizedReason = reason == null ? "" : reason.trim();
        if (normalizedReason.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "删除原因不能为空");
        }
        int updated = userMapper.markAccountDeleted(
                userId,
                operatorId,
                normalizedReason,
                LocalDateTime.now()
        );
        if (updated == 0) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "账号已被删除或状态已变化，请刷新后重试"
            );
        }
        auditService.record(
                operatorId,
                "USER_DELETE",
                "SYS_USER",
                String.valueOf(userId),
                "删除账号：" + user.getUsername() + "；原因：" + normalizedReason
        );
    }

    private AdminUserResponse toResponse(SysUser user) {
        return new AdminUserResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getEmail(),
                Integer.valueOf(1).equals(user.getStatus()),
                user.getStatus(),
                roleMapper.selectRoleCodesByUserId(user.getId()),
                user.getCreatedAt()
        );
    }
}
