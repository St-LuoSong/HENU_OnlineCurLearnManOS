package com.onlinecurlearnmanos.forum.service;

import com.onlinecurlearnmanos.admin.service.AdminAuditService;
import com.onlinecurlearnmanos.common.api.PageResult;
import com.onlinecurlearnmanos.common.error.ErrorCode;
import com.onlinecurlearnmanos.common.exception.BusinessException;
import com.onlinecurlearnmanos.forum.dto.ForumModerationLogResponse;
import com.onlinecurlearnmanos.forum.dto.ForumModerationRequest;
import com.onlinecurlearnmanos.forum.dto.ForumReportRequest;
import com.onlinecurlearnmanos.forum.dto.ForumReportResponse;
import com.onlinecurlearnmanos.forum.entity.ForumModerationLog;
import com.onlinecurlearnmanos.forum.entity.ForumPost;
import com.onlinecurlearnmanos.forum.entity.ForumReply;
import com.onlinecurlearnmanos.forum.entity.ForumReport;
import com.onlinecurlearnmanos.forum.mapper.ForumModerationLogMapper;
import com.onlinecurlearnmanos.forum.mapper.ForumPostMapper;
import com.onlinecurlearnmanos.forum.mapper.ForumReplyMapper;
import com.onlinecurlearnmanos.forum.mapper.ForumReportMapper;
import com.onlinecurlearnmanos.security.UserPrincipal;
import com.onlinecurlearnmanos.user.entity.SysUser;
import com.onlinecurlearnmanos.user.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 论坛内容治理：学生 / 教师举报，管理员带理由处置（编辑 / 隐藏 / 删除 / 恢复 / 驳回）。
 *
 * <p>每一次处置都会写入治理日志与管理员审计流水；管理员编辑他人内容必须填写理由，
 * 若来自举报还需携带 reportId，从而杜绝无审计的超级编辑权限。</p>
 */
@Service
@RequiredArgsConstructor
public class ForumGovernanceServiceImpl implements ForumGovernanceService {

    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String STATUS_HIDDEN = "HIDDEN";
    private static final String STATUS_DELETED = "DELETED";

    private static final String ACTION_DISMISS = "DISMISS";
    private static final String ACTION_EDIT = "EDIT";
    private static final String ACTION_HIDE = "HIDE";
    private static final String ACTION_DELETE = "DELETE";
    private static final String ACTION_RESTORE = "RESTORE";

    private static final Set<String> ALLOWED_ACTIONS = Set.of(
            ACTION_DISMISS,
            ACTION_EDIT,
            ACTION_HIDE,
            ACTION_DELETE,
            ACTION_RESTORE
    );

    private static final Set<String> ALLOWED_REASONS = Set.of(
            "SPAM",
            "ABUSE",
            "WRONG_CONTENT",
            "ADVERTISING",
            "OTHER"
    );

    private final ForumReportMapper reportMapper;
    private final ForumModerationLogMapper moderationLogMapper;
    private final ForumPostMapper postMapper;
    private final ForumReplyMapper replyMapper;
    private final SysUserMapper userMapper;
    private final AdminAuditService auditService;

    @Override
    @Transactional
    public ForumReportResponse report(UserPrincipal principal, ForumReportRequest request) {
        String targetType = normalizeTargetType(request.targetType());
        Long targetId = request.targetId();
        Long authorId = requireTargetAuthor(targetType, targetId);
        if (Objects.equals(authorId, principal.getId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不能举报自己发布的内容");
        }
        long pending = reportMapper.selectCount(new LambdaQueryWrapper<ForumReport>()
                .eq(ForumReport::getTargetType, targetType)
                .eq(ForumReport::getTargetId, targetId)
                .eq(ForumReport::getReporterId, principal.getId())
                .eq(ForumReport::getStatus, ForumReport.STATUS_PENDING));
        if (pending > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "你已举报过该内容，请等待管理员处理");
        }
        ForumReport report = new ForumReport();
        report.setTargetType(targetType);
        report.setTargetId(targetId);
        report.setReporterId(principal.getId());
        report.setReason(normalizeReason(request.reason()));
        report.setDetail(normalizeNullable(request.detail()));
        report.setStatus(ForumReport.STATUS_PENDING);
        report.setCreatedAt(LocalDateTime.now());
        reportMapper.insert(report);
        return toResponses(List.of(reportMapper.selectById(report.getId()))).get(0);
    }

    @Override
    public PageResult<ForumReportResponse> listReports(
            long page,
            long size,
            String status,
            String targetType
    ) {
        long safePage = Math.max(page, 1);
        long safeSize = Math.min(Math.max(size, 1), 50);
        LambdaQueryWrapper<ForumReport> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(status)) {
            wrapper.eq(ForumReport::getStatus, status.trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(targetType)) {
            wrapper.eq(ForumReport::getTargetType, normalizeTargetType(targetType));
        }
        wrapper.orderByDesc(ForumReport::getCreatedAt).orderByDesc(ForumReport::getId);
        Page<ForumReport> result = reportMapper.selectPage(new Page<>(safePage, safeSize), wrapper);
        return new PageResult<>(
                toResponses(result.getRecords()),
                result.getTotal(),
                safePage,
                safeSize
        );
    }

    @Override
    public List<ForumReportResponse> listPendingReportsFor(String targetType, Long targetId) {
        return toResponses(reportMapper.selectList(new LambdaQueryWrapper<ForumReport>()
                .eq(ForumReport::getTargetType, normalizeTargetType(targetType))
                .eq(ForumReport::getTargetId, targetId)
                .eq(ForumReport::getStatus, ForumReport.STATUS_PENDING)
                .orderByAsc(ForumReport::getCreatedAt)));
    }

    @Override
    @Transactional
    public ForumModerationLogResponse moderate(Long adminId, ForumModerationRequest request) {
        String targetType = normalizeTargetType(request.targetType());
        Long targetId = request.targetId();
        String action = normalizeAction(request.action());
        String note = request.note().trim();

        ForumReport report = null;
        if (request.reportId() != null) {
            report = reportMapper.selectById(request.reportId());
            if (report == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "举报记录不存在");
            }
            if (!Objects.equals(report.getTargetType(), targetType)
                    || !Objects.equals(report.getTargetId(), targetId)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "举报记录与处置目标不一致");
            }
        }

        String beforeSummary = targetSummary(targetType, targetId);
        switch (action) {
            case ACTION_DISMISS -> requireTarget(targetType, targetId);
            case ACTION_HIDE -> {
                requireTarget(targetType, targetId);
                updateTarget(targetType, targetId, STATUS_HIDDEN, 0);
            }
            case ACTION_DELETE -> {
                requireTarget(targetType, targetId);
                updateTarget(targetType, targetId, STATUS_DELETED, 1);
            }
            case ACTION_RESTORE -> {
                if (updateTarget(targetType, targetId, STATUS_PUBLISHED, 0) == 0) {
                    throw new BusinessException(ErrorCode.NOT_FOUND, "目标内容不存在");
                }
            }
            case ACTION_EDIT -> applyEdit(adminId, targetType, request);
            default -> throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的处置动作");
        }
        String afterSummary = ACTION_DELETE.equals(action)
                ? "已删除"
                : targetSummary(targetType, targetId);

        ForumModerationLog log = new ForumModerationLog();
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setReportId(report == null ? null : report.getId());
        log.setOperatorId(adminId);
        log.setAction(action);
        log.setReason(note);
        log.setBeforeSummary(beforeSummary);
        log.setAfterSummary(afterSummary);
        log.setPatrol(report == null ? 1 : 0);
        log.setCreatedAt(LocalDateTime.now());
        moderationLogMapper.insert(log);

        if (report != null) {
            reportMapper.update(null, new LambdaUpdateWrapper<ForumReport>()
                    .eq(ForumReport::getId, report.getId())
                    .eq(ForumReport::getStatus, ForumReport.STATUS_PENDING)
                    .set(
                            ForumReport::getStatus,
                            ACTION_DISMISS.equals(action)
                                    ? ForumReport.STATUS_DISMISSED
                                    : ForumReport.STATUS_RESOLVED
                    )
                    .set(ForumReport::getHandlerId, adminId)
                    .set(ForumReport::getHandleAction, action)
                    .set(ForumReport::getHandleNote, note)
                    .set(ForumReport::getHandledAt, LocalDateTime.now()));
        }

        auditService.record(
                adminId,
                "FORUM_" + action,
                targetType,
                String.valueOf(targetId),
                (report == null ? "主动巡查：" : "举报处置：" ) + note
        );
        return toLogResponses(List.of(moderationLogMapper.selectById(log.getId()))).get(0);
    }

    @Override
    public PageResult<ForumModerationLogResponse> listLogs(long page, long size) {
        long safePage = Math.max(page, 1);
        long safeSize = Math.min(Math.max(size, 1), 50);
        Page<ForumModerationLog> result = moderationLogMapper.selectPage(
                new Page<>(safePage, safeSize),
                new LambdaQueryWrapper<ForumModerationLog>()
                        .orderByDesc(ForumModerationLog::getCreatedAt)
                        .orderByDesc(ForumModerationLog::getId)
        );
        return new PageResult<>(
                toLogResponses(result.getRecords()),
                result.getTotal(),
                safePage,
                safeSize
        );
    }

    private void applyEdit(Long adminId, String targetType, ForumModerationRequest request) {
        requireTarget(targetType, request.targetId());
        if (!StringUtils.hasText(request.content())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "编辑内容时必须提供新的正文");
        }
        if (ForumReport.TARGET_POST.equals(targetType)) {
            if (!StringUtils.hasText(request.title())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "编辑帖子时必须提供新的标题");
            }
            postMapper.update(null, new LambdaUpdateWrapper<ForumPost>()
                    .eq(ForumPost::getId, request.targetId())
                    .set(ForumPost::getTitle, request.title().trim())
                    .set(ForumPost::getContent, request.content().trim()));
            return;
        }
        replyMapper.update(null, new LambdaUpdateWrapper<ForumReply>()
                .eq(ForumReply::getId, request.targetId())
                .set(ForumReply::getContent, request.content().trim()));
    }

    private int updateTarget(String targetType, Long targetId, String status, int deleted) {
        return ForumReport.TARGET_POST.equals(targetType)
                ? postMapper.updateStatusAndDeleted(targetId, status, deleted)
                : replyMapper.updateStatusAndDeleted(targetId, status, deleted);
    }

    private void requireTarget(String targetType, Long targetId) {
        requireTargetAuthor(targetType, targetId);
    }

    private Long requireTargetAuthor(String targetType, Long targetId) {
        if (ForumReport.TARGET_POST.equals(targetType)) {
            ForumPost post = postMapper.selectById(targetId);
            if (post == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "帖子不存在或已被删除");
            }
            return post.getAuthorId();
        }
        ForumReply reply = replyMapper.selectById(targetId);
        if (reply == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "回复不存在或已被删除");
        }
        return reply.getAuthorId();
    }

    private String targetSummary(String targetType, Long targetId) {
        if (ForumReport.TARGET_POST.equals(targetType)) {
            ForumPost post = postMapper.selectById(targetId);
            return post == null ? null : "帖子《" + post.getTitle() + "》";
        }
        ForumReply reply = replyMapper.selectById(targetId);
        return reply == null ? null : "回复：" + preview(reply.getContent());
    }

    private List<ForumReportResponse> toResponses(List<ForumReport> reports) {
        if (reports.isEmpty()) {
            return List.of();
        }
        Set<Long> userIds = new HashSet<>();
        for (ForumReport report : reports) {
            if (report.getReporterId() != null) {
                userIds.add(report.getReporterId());
            }
            if (report.getHandlerId() != null) {
                userIds.add(report.getHandlerId());
            }
        }
        Map<Long, String> names = userIds.isEmpty()
                ? Map.of()
                : userMapper.selectByIdsIncludingDeleted(userIds).stream()
                        .collect(Collectors.toMap(SysUser::getId, SysUser::getDisplayName));
        Map<Long, String> postTitles = loadPostTitles(reports);
        Map<Long, String> replySummaries = loadReplySummaries(reports);
        return reports.stream()
                .map(report -> new ForumReportResponse(
                        report.getId(),
                        report.getTargetType(),
                        report.getTargetId(),
                        ForumReport.TARGET_POST.equals(report.getTargetType())
                                ? postTitles.get(report.getTargetId())
                                : replySummaries.get(report.getTargetId()),
                        report.getReporterId(),
                        names.get(report.getReporterId()),
                        report.getReason(),
                        report.getDetail(),
                        report.getStatus(),
                        report.getHandlerId(),
                        report.getHandlerId() == null ? null : names.get(report.getHandlerId()),
                        report.getHandleAction(),
                        report.getHandleNote(),
                        report.getHandledAt(),
                        report.getCreatedAt()
                ))
                .toList();
    }

    private List<ForumModerationLogResponse> toLogResponses(List<ForumModerationLog> logs) {
        if (logs.isEmpty()) {
            return List.of();
        }
        List<Long> operatorIds = logs.stream()
                .map(ForumModerationLog::getOperatorId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, String> names = operatorIds.isEmpty()
                ? Map.of()
                : userMapper.selectByIdsIncludingDeleted(operatorIds).stream()
                        .collect(Collectors.toMap(SysUser::getId, SysUser::getDisplayName));
        return logs.stream()
                .map(log -> new ForumModerationLogResponse(
                        log.getId(),
                        log.getTargetType(),
                        log.getTargetId(),
                        log.getReportId(),
                        log.getOperatorId(),
                        names.get(log.getOperatorId()),
                        log.getAction(),
                        log.getReason(),
                        log.getBeforeSummary(),
                        log.getAfterSummary(),
                        Integer.valueOf(1).equals(log.getPatrol()),
                        log.getCreatedAt()
                ))
                .toList();
    }

    private Map<Long, String> loadPostTitles(Collection<ForumReport> reports) {
        List<Long> ids = reports.stream()
                .filter(report -> ForumReport.TARGET_POST.equals(report.getTargetType()))
                .map(ForumReport::getTargetId)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return postMapper.selectByIds(ids).stream()
                .collect(Collectors.toMap(
                        ForumPost::getId,
                        post -> "帖子《" + post.getTitle() + "》"
                ));
    }

    private Map<Long, String> loadReplySummaries(Collection<ForumReport> reports) {
        List<Long> ids = reports.stream()
                .filter(report -> ForumReport.TARGET_REPLY.equals(report.getTargetType()))
                .map(ForumReport::getTargetId)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return replyMapper.selectByIds(ids).stream()
                .collect(Collectors.toMap(
                        ForumReply::getId,
                        reply -> "回复：" + preview(reply.getContent())
                ));
    }

    private String normalizeTargetType(String targetType) {
        String normalized = targetType == null
                ? ""
                : targetType.trim().toUpperCase(Locale.ROOT);
        if (!ForumReport.TARGET_POST.equals(normalized)
                && !ForumReport.TARGET_REPLY.equals(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "举报对象类型只能是 POST 或 REPLY");
        }
        return normalized;
    }

    private String normalizeAction(String action) {
        String normalized = action == null ? "" : action.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_ACTIONS.contains(normalized)) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "处置动作只能是 DISMISS / EDIT / HIDE / DELETE / RESTORE"
            );
        }
        return normalized;
    }

    private String normalizeReason(String reason) {
        String normalized = reason == null ? "" : reason.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_REASONS.contains(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "举报原因不合法");
        }
        return normalized;
    }

    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String preview(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String normalized = content.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 60 ? normalized : normalized.substring(0, 60) + "...";
    }
}
