package com.onlinecurlearnmanos.forum.service;

import com.onlinecurlearnmanos.admin.service.AdminAuditService;
import com.onlinecurlearnmanos.cloudclass.entity.CloudClass;
import com.onlinecurlearnmanos.cloudclass.mapper.CloudClassMapper;
import com.onlinecurlearnmanos.common.exception.BusinessException;
import com.onlinecurlearnmanos.content.entity.Subject;
import com.onlinecurlearnmanos.content.mapper.SubjectMapper;
import com.onlinecurlearnmanos.forum.dto.ForumAnnouncementRequest;
import com.onlinecurlearnmanos.forum.dto.ForumAnnouncementResponse;
import com.onlinecurlearnmanos.forum.entity.ForumAnnouncement;
import com.onlinecurlearnmanos.forum.mapper.ForumAnnouncementMapper;
import com.onlinecurlearnmanos.security.UserPrincipal;
import com.onlinecurlearnmanos.support.MybatisLambdaCache;
import com.onlinecurlearnmanos.user.entity.SysUser;
import com.onlinecurlearnmanos.user.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 教师重要公示的权限边界与治理审计。
 *
 * <p>学生只读、教师只能绑定自己负责的云班级、管理员可发全局公示，
 * 隐藏 / 恢复必须写审计——这些约束必须在服务端成立。</p>
 */
class ForumAnnouncementServiceTest {

    private static final Long TEACHER_ID = 31L;
    private static final Long OTHER_TEACHER_ID = 32L;
    private static final Long ADMIN_ID = 99L;
    private static final Long CLASS_ID = 501L;
    private static final Long SUBJECT_ID = 601L;

    private final ForumAnnouncementMapper announcementMapper = mock(ForumAnnouncementMapper.class);
    private final CloudClassMapper cloudClassMapper = mock(CloudClassMapper.class);
    private final SubjectMapper subjectMapper = mock(SubjectMapper.class);
    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final AdminAuditService auditService = mock(AdminAuditService.class);

    private final ForumAnnouncementService service = new ForumAnnouncementService(
            announcementMapper,
            cloudClassMapper,
            subjectMapper,
            userMapper,
            auditService
    );

    private final Map<Long, ForumAnnouncement> store = new HashMap<>();
    private final AtomicLong sequence = new AtomicLong(1);

    private final UserPrincipal teacher = principal(TEACHER_ID, "T2024001", "师老师", "TEACHER");
    private final UserPrincipal otherTeacher =
            principal(OTHER_TEACHER_ID, "T2024002", "别老师", "TEACHER");
    private final UserPrincipal admin = principal(ADMIN_ID, "admin", "管理员", "ADMIN");
    private final UserPrincipal student = principal(71L, "2024001", "李同学", "STUDENT");

    @BeforeAll
    static void registerLambdaCache() {
        MybatisLambdaCache.register(ForumAnnouncement.class);
    }

    @BeforeEach
    void setUp() {
        store.clear();
        sequence.set(1);
        given(announcementMapper.insert(any(ForumAnnouncement.class))).willAnswer(invocation -> {
            ForumAnnouncement row = invocation.getArgument(0);
            row.setId(sequence.getAndIncrement());
            store.put(row.getId(), row);
            return 1;
        });
        given(announcementMapper.selectById(anyLong()))
                .willAnswer(invocation -> store.get(invocation.getArgument(0)));
        given(announcementMapper.updateStatusAndDeleted(anyLong(), anyString(), anyInt()))
                .willAnswer(invocation -> {
                    ForumAnnouncement row = store.get(invocation.getArgument(0));
                    if (row == null) {
                        return 0;
                    }
                    row.setStatus(invocation.getArgument(1));
                    row.setDeleted(invocation.getArgument(2));
                    return 1;
                });
    }

    @Test
    void studentCannotPublishAnnouncement() {
        assertThatThrownBy(() -> service.create(student, new ForumAnnouncementRequest(CLASS_ID, "标题", "内容")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只有教师或管理员可以发布公示");
        verify(announcementMapper, never()).insert(any(ForumAnnouncement.class));
    }

    @Test
    void teacherMustBindOwnCloudClass() {
        assertThatThrownBy(() -> service.create(teacher, new ForumAnnouncementRequest(null, "标题", "内容")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须选择自己负责的云班级");
    }

    @Test
    void teacherCannotPublishToAnotherTeachersClass() {
        given(cloudClassMapper.selectById(CLASS_ID))
                .willReturn(cloudClass(CLASS_ID, OTHER_TEACHER_ID, SUBJECT_ID));

        assertThatThrownBy(() -> service.create(teacher, new ForumAnnouncementRequest(CLASS_ID, "标题", "内容")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能对自己负责的云班级发布公示");
    }

    @Test
    void teacherPublishesToOwnClassAndSubjectIsDerivedFromClass() {
        given(cloudClassMapper.selectById(CLASS_ID))
                .willReturn(cloudClass(CLASS_ID, TEACHER_ID, SUBJECT_ID));

        service.create(teacher, new ForumAnnouncementRequest(CLASS_ID, "期中安排", "周五随堂测"));

        ArgumentCaptor<ForumAnnouncement> captor = ArgumentCaptor.forClass(ForumAnnouncement.class);
        verify(announcementMapper).insert(captor.capture());
        assertThat(captor.getValue().getClassId()).isEqualTo(CLASS_ID);
        assertThat(captor.getValue().getSubjectId()).isEqualTo(SUBJECT_ID);
        assertThat(captor.getValue().getAuthorId()).isEqualTo(TEACHER_ID);
        assertThat(captor.getValue().getStatus()).isEqualTo(ForumAnnouncement.STATUS_PUBLISHED);
        assertThat(captor.getValue().getDeleted()).isZero();
    }

    @Test
    void adminPublishesGlobalAnnouncementWithoutClassScope() {
        service.create(admin, new ForumAnnouncementRequest(null, "平台维护", "周日凌晨停机"));

        ArgumentCaptor<ForumAnnouncement> captor = ArgumentCaptor.forClass(ForumAnnouncement.class);
        verify(announcementMapper).insert(captor.capture());
        assertThat(captor.getValue().getClassId()).isNull();
        assertThat(captor.getValue().getSubjectId()).isNull();
        assertThat(captor.getValue().getAuthorId()).isEqualTo(ADMIN_ID);
    }

    @Test
    void adminHideWritesAuditAndMarksDeleted() {
        store.put(1L, announcement(1L, TEACHER_ID, CLASS_ID, SUBJECT_ID));

        ForumAnnouncementResponse response = service.updateStatus(ADMIN_ID, 1L, "HIDDEN");

        verify(announcementMapper).updateStatusAndDeleted(1L, ForumAnnouncement.STATUS_HIDDEN, 1);
        verify(auditService).record(
                eq(ADMIN_ID),
                eq("FORUM_ANNOUNCEMENT_HIDE"),
                eq("FORUM_ANNOUNCEMENT"),
                eq("1"),
                anyString()
        );
        assertThat(response.status()).isEqualTo(ForumAnnouncement.STATUS_HIDDEN);
        assertThat(response.canEdit()).isTrue();
        assertThat(response.canDelete()).isTrue();
    }

    @Test
    void adminRestoreClearsDeletedFlag() {
        ForumAnnouncement hidden = announcement(1L, TEACHER_ID, CLASS_ID, SUBJECT_ID);
        hidden.setStatus(ForumAnnouncement.STATUS_HIDDEN);
        hidden.setDeleted(1);
        store.put(1L, hidden);

        service.updateStatus(ADMIN_ID, 1L, "published");

        verify(announcementMapper).updateStatusAndDeleted(1L, ForumAnnouncement.STATUS_PUBLISHED, 0);
        verify(auditService).record(
                eq(ADMIN_ID),
                eq("FORUM_ANNOUNCEMENT_RESTORE"),
                eq("FORUM_ANNOUNCEMENT"),
                eq("1"),
                anyString()
        );
    }

    @Test
    void rejectsUnknownGovernanceStatusWithoutAudit() {
        store.put(1L, announcement(1L, TEACHER_ID, CLASS_ID, SUBJECT_ID));

        assertThatThrownBy(() -> service.updateStatus(ADMIN_ID, 1L, "DELETED"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PUBLISHED 或 HIDDEN");

        verify(announcementMapper, never()).updateStatusAndDeleted(anyLong(), anyString(), anyInt());
        verify(auditService, never()).record(anyLong(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void studentReadsPublishedAnnouncementWithoutMaintainRights() {
        ForumAnnouncement row = announcement(1L, TEACHER_ID, CLASS_ID, SUBJECT_ID);
        row.setTitle("期中安排");
        given(announcementMapper.selectList(any())).willReturn(List.of(row));
        given(userMapper.selectByIdsIncludingDeleted(any())).willReturn(List.of(user(TEACHER_ID, "师老师")));
        given(cloudClassMapper.selectByIds(any()))
                .willReturn(List.of(cloudClass(CLASS_ID, TEACHER_ID, SUBJECT_ID)));
        given(subjectMapper.selectByIds(any())).willReturn(List.of(subject(SUBJECT_ID, "高等数学")));

        List<ForumAnnouncementResponse> rows = service.listPublished(5, student);

        assertThat(rows).hasSize(1);
        ForumAnnouncementResponse response = rows.get(0);
        assertThat(response.title()).isEqualTo("期中安排");
        assertThat(response.authorName()).isEqualTo("师老师");
        assertThat(response.className()).isEqualTo("高等数学云班");
        assertThat(response.subjectName()).isEqualTo("高等数学");
        assertThat(response.authorBadges()).contains("教师公示");
        assertThat(response.canEdit()).isFalse();
        assertThat(response.canDelete()).isFalse();
    }

    private UserPrincipal principal(Long id, String username, String displayName, String role) {
        return new UserPrincipal(id, username, "n/a", displayName, null, 1, List.of(role));
    }

    private ForumAnnouncement announcement(Long id, Long authorId, Long classId, Long subjectId) {
        ForumAnnouncement row = new ForumAnnouncement();
        row.setId(id);
        row.setAuthorId(authorId);
        row.setClassId(classId);
        row.setSubjectId(subjectId);
        row.setTitle("公示标题");
        row.setContent("公示内容");
        row.setStatus(ForumAnnouncement.STATUS_PUBLISHED);
        row.setDeleted(0);
        row.setVersion(0);
        return row;
    }

    private CloudClass cloudClass(Long id, Long teacherId, Long subjectId) {
        CloudClass cloudClass = new CloudClass();
        cloudClass.setId(id);
        cloudClass.setName("高等数学云班");
        cloudClass.setTeacherId(teacherId);
        cloudClass.setSubjectId(subjectId);
        return cloudClass;
    }

    private Subject subject(Long id, String name) {
        Subject subject = new Subject();
        subject.setId(id);
        subject.setName(name);
        return subject;
    }

    private SysUser user(Long id, String displayName) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setDisplayName(displayName);
        return user;
    }
}

