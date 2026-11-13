package com.onlinecurlearnmanos.forum.service;

import com.onlinecurlearnmanos.admin.service.AdminAuditService;
import com.onlinecurlearnmanos.cloudclass.entity.CloudClass;
import com.onlinecurlearnmanos.cloudclass.mapper.CloudClassMapper;
import com.onlinecurlearnmanos.common.api.PageResult;
import com.onlinecurlearnmanos.common.error.ErrorCode;
import com.onlinecurlearnmanos.common.exception.BusinessException;
import com.onlinecurlearnmanos.content.entity.Subject;
import com.onlinecurlearnmanos.content.mapper.SubjectMapper;
import com.onlinecurlearnmanos.forum.dto.ForumAnnouncementRequest;
import com.onlinecurlearnmanos.forum.dto.ForumAnnouncementResponse;
import com.onlinecurlearnmanos.forum.entity.ForumAnnouncement;
import com.onlinecurlearnmanos.forum.mapper.ForumAnnouncementMapper;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 教师重要公示。
 *
 * <p>权限边界：学生只读；教师只能对自己负责的云班级发布与维护；管理员可以发布全局公示，
 * 并对任意公示做隐藏 / 恢复治理，治理动作写审计流水。</p>
 */
@Service
@RequiredArgsConstructor
public class ForumAnnouncementService {

    private static final String ROLE_TEACHER = "TEACHER";
    private static final String ROLE_ADMIN = "ADMIN";
    private static final int MAX_LIST_LIMIT = 20;
    private static final Set<String> ALLOWED_STATUS =
            Set.of(ForumAnnouncement.STATUS_PUBLISHED, ForumAnnouncement.STATUS_HIDDEN);

    private final ForumAnnouncementMapper announcementMapper;
    private final CloudClassMapper cloudClassMapper;
    private final SubjectMapper subjectMapper;
    private final SysUserMapper userMapper;
    private final AdminAuditService auditService;

    public List<ForumAnnouncementResponse> listPublished(int limit, UserPrincipal principal) {
        int safeLimit = Math.min(Math.max(limit, 1), MAX_LIST_LIMIT);
        List<ForumAnnouncement> rows = announcementMapper.selectList(
                new LambdaQueryWrapper<ForumAnnouncement>()
                        .eq(ForumAnnouncement::getStatus, ForumAnnouncement.STATUS_PUBLISHED)
                        .orderByDesc(ForumAnnouncement::getCreatedAt)
                        .orderByDesc(ForumAnnouncement::getId)
                        .last("LIMIT " + safeLimit)
        );
        return toResponses(rows, principal);
    }

    public PageResult<ForumAnnouncementResponse> listForAdmin(
            long page,
            long size,
            String status,
            UserPrincipal principal
    ) {
        long safePage = Math.max(page, 1);
        long safeSize = Math.min(Math.max(size, 1), 50);
        LambdaQueryWrapper<ForumAnnouncement> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(status)) {
            wrapper.eq(ForumAnnouncement::getStatus, status.trim().toUpperCase());
        }
        wrapper.orderByDesc(ForumAnnouncement::getCreatedAt).orderByDesc(ForumAnnouncement::getId);
        Page<ForumAnnouncement> result = announcementMapper.selectPage(
                new Page<>(safePage, safeSize),
                wrapper
        );
        return new PageResult<>(
                toResponses(result.getRecords(), principal),
                result.getTotal(),
                safePage,
                safeSize
        );
    }

    @Transactional
    public ForumAnnouncementResponse create(UserPrincipal principal, ForumAnnouncementRequest request) {
        requirePublisher(principal);
        Scope scope = resolveScope(principal, request.classId());
        ForumAnnouncement announcement = new ForumAnnouncement();
        announcement.setAuthorId(principal.getId());
        announcement.setClassId(scope.classId());
        announcement.setSubjectId(scope.subjectId());
        announcement.setTitle(request.title().trim());
        announcement.setContent(request.content().trim());
        announcement.setStatus(ForumAnnouncement.STATUS_PUBLISHED);
        announcement.setDeleted(0);
        announcement.setVersion(0);
        announcementMapper.insert(announcement);
        return toResponses(List.of(getRequired(announcement.getId())), principal).get(0);
    }

    @Transactional
    public ForumAnnouncementResponse update(
            Long id,
            UserPrincipal principal,
            ForumAnnouncementRequest request
    ) {
        ForumAnnouncement announcement = getRequired(id);
        assertCanMaintain(announcement, principal);
        Scope scope = resolveScope(principal, request.classId());
        announcementMapper.update(null, new LambdaUpdateWrapper<ForumAnnouncement>()
                .eq(ForumAnnouncement::getId, id)
                .set(ForumAnnouncement::getClassId, scope.classId())
                .set(ForumAnnouncement::getSubjectId, scope.subjectId())
                .set(ForumAnnouncement::getTitle, request.title().trim())
                .set(ForumAnnouncement::getContent, request.content().trim()));
        return toResponses(List.of(getRequired(id)), principal).get(0);
    }

    @Transactional
    public void delete(Long id, UserPrincipal principal) {
        ForumAnnouncement announcement = getRequired(id);
        assertCanMaintain(announcement, principal);
        announcementMapper.deleteById(id);
        if (isAdmin(principal)) {
            auditService.record(
                    principal.getId(),
                    "FORUM_ANNOUNCEMENT_DELETE",
                    "FORUM_ANNOUNCEMENT",
                    String.valueOf(id),
                    "删除教师公示：" + announcement.getTitle()
            );
        }
    }

    /** 管理端治理：隐藏（HIDDEN）或恢复（PUBLISHED），必须留审计。 */
    @Transactional
    public ForumAnnouncementResponse updateStatus(Long adminId, Long id, String status) {
        ForumAnnouncement announcement = getRequired(id);
        String normalized = status == null ? "" : status.trim().toUpperCase();
        if (!ALLOWED_STATUS.contains(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "公示状态只能是 PUBLISHED 或 HIDDEN");
        }
        boolean hidden = ForumAnnouncement.STATUS_HIDDEN.equals(normalized);
        announcementMapper.updateStatusAndDeleted(id, normalized, hidden ? 1 : 0);
        auditService.record(
                adminId,
                hidden ? "FORUM_ANNOUNCEMENT_HIDE" : "FORUM_ANNOUNCEMENT_RESTORE",
                "FORUM_ANNOUNCEMENT",
                String.valueOf(id),
                (hidden ? "隐藏" : "恢复") + "教师公示：" + announcement.getTitle()
        );
        return toResponses(List.of(getRequired(id)), adminPrincipal(adminId)).get(0);
    }

    private ForumAnnouncement getRequired(Long id) {
        ForumAnnouncement announcement = id == null ? null : announcementMapper.selectById(id);
        if (announcement == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "公示不存在或已被删除");
        }
        return announcement;
    }

    private void requirePublisher(UserPrincipal principal) {
        if (!isAdmin(principal) && !isTeacher(principal)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有教师或管理员可以发布公示");
        }
    }

    private void assertCanMaintain(ForumAnnouncement announcement, UserPrincipal principal) {
        if (isAdmin(principal) || Objects.equals(announcement.getAuthorId(), principal.getId())) {
            return;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN, "只能维护自己发布的公示");
    }

    /**
     * 教师必须绑定自己负责的云班级，课程由班级推导；管理员可以不绑定班级发布全局公示。
     */
    private Scope resolveScope(UserPrincipal principal, Long classId) {
        requirePublisher(principal);
        if (classId == null) {
            if (!isAdmin(principal)) {
                throw new BusinessException(
                        ErrorCode.BAD_REQUEST,
                        "教师发布公示必须选择自己负责的云班级"
                );
            }
            return new Scope(null, null);
        }
        CloudClass cloudClass = cloudClassMapper.selectById(classId);
        if (cloudClass == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "云班级不存在");
        }
        if (!isAdmin(principal) && !Objects.equals(cloudClass.getTeacherId(), principal.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能对自己负责的云班级发布公示");
        }
        return new Scope(cloudClass.getId(), cloudClass.getSubjectId());
    }

    private boolean isTeacher(UserPrincipal principal) {
        return principal != null && principal.getRoles().contains(ROLE_TEACHER);
    }

    private boolean isAdmin(UserPrincipal principal) {
        return principal != null && principal.getRoles().contains(ROLE_ADMIN);
    }

    /** 管理端治理后需要用管理员身份构造响应，这里只借角色做 canEdit / canDelete 判定。 */
    private UserPrincipal adminPrincipal(Long adminId) {
        return new UserPrincipal(adminId, "admin", "n/a", "管理员", null, 1, List.of(ROLE_ADMIN));
    }

    private List<ForumAnnouncementResponse> toResponses(
            List<ForumAnnouncement> rows,
            UserPrincipal principal
    ) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Set<Long> authorIds = rows.stream()
                .map(ForumAnnouncement::getAuthorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> authorNames = authorIds.isEmpty()
                ? Map.of()
                : userMapper.selectByIdsIncludingDeleted(authorIds).stream()
                        .collect(Collectors.toMap(SysUser::getId, SysUser::getDisplayName));
        Map<Long, CloudClass> classes = loadClasses(
                rows.stream().map(ForumAnnouncement::getClassId).toList()
        );
        Map<Long, Subject> subjects = loadSubjects(
                rows.stream().map(ForumAnnouncement::getSubjectId).toList()
        );
        return rows.stream()
                .map(row -> {
                    // 全局公示的 classId / subjectId 允许为空，而 Map.of() 是 null 敌意的：
                    // 直接 get(null) 会抛 NPE，必须先把空键挡在查询之前。
                    CloudClass cloudClass = row.getClassId() == null
                            ? null
                            : classes.get(row.getClassId());
                    Subject subject = row.getSubjectId() == null
                            ? null
                            : subjects.get(row.getSubjectId());
                    boolean admin = isAdmin(principal);
                    boolean mine = principal != null
                            && Objects.equals(row.getAuthorId(), principal.getId());
                    return new ForumAnnouncementResponse(
                            row.getId(),
                            row.getAuthorId(),
                            authorNames.getOrDefault(row.getAuthorId(), "未知用户"),
                            List.of("教师公示"),
                            row.getClassId(),
                            cloudClass == null ? null : cloudClass.getName(),
                            row.getSubjectId(),
                            subject == null ? null : subject.getName(),
                            row.getTitle(),
                            row.getContent(),
                            row.getStatus(),
                            admin || mine,
                            admin || mine,
                            row.getCreatedAt(),
                            row.getUpdatedAt()
                    );
                })
                .toList();
    }

    private Map<Long, CloudClass> loadClasses(Collection<Long> classIds) {
        List<Long> ids = distinctIds(classIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        return cloudClassMapper.selectByIds(ids).stream()
                .collect(Collectors.toMap(CloudClass::getId, Function.identity(), (left, right) -> left));
    }

    private Map<Long, Subject> loadSubjects(Collection<Long> subjectIds) {
        List<Long> ids = distinctIds(subjectIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        return subjectMapper.selectByIds(ids).stream()
                .collect(Collectors.toMap(Subject::getId, Function.identity(), (left, right) -> left));
    }

    private List<Long> distinctIds(Collection<Long> ids) {
        return ids == null
                ? List.of()
                : ids.stream().filter(Objects::nonNull).distinct().toList();
    }

    private record Scope(Long classId, Long subjectId) {
    }
}
