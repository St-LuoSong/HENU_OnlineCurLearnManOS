package com.onlinecurlearnmanos.forum.service;

import com.onlinecurlearnmanos.admin.service.AdminAuditService;
import com.onlinecurlearnmanos.common.error.ErrorCode;
import com.onlinecurlearnmanos.common.exception.BusinessException;
import com.onlinecurlearnmanos.forum.dto.ForumModerationLogResponse;
import com.onlinecurlearnmanos.forum.dto.ForumModerationRequest;
import com.onlinecurlearnmanos.forum.dto.ForumReportRequest;
import com.onlinecurlearnmanos.forum.dto.ForumReportResponse;
import com.onlinecurlearnmanos.forum.entity.ForumModerationLog;
import com.onlinecurlearnmanos.forum.entity.ForumPost;
import com.onlinecurlearnmanos.forum.entity.ForumReport;
import com.onlinecurlearnmanos.forum.mapper.ForumModerationLogMapper;
import com.onlinecurlearnmanos.forum.mapper.ForumPostMapper;
import com.onlinecurlearnmanos.forum.mapper.ForumReplyMapper;
import com.onlinecurlearnmanos.forum.mapper.ForumReportMapper;
import com.onlinecurlearnmanos.security.UserPrincipal;
import com.onlinecurlearnmanos.support.MybatisLambdaCache;
import com.onlinecurlearnmanos.user.entity.SysUser;
import com.onlinecurlearnmanos.user.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 论坛治理流程（审计 D8）：举报写入 → 管理员带理由处置 → 举报结案 → 写治理日志与审计流水。
 */
@ExtendWith(MockitoExtension.class)
class ForumGovernanceTest {

    private static final Long POST_ID = 11L;
    private static final Long REPORT_ID = 51L;
    private static final Long LOG_ID = 61L;
    private static final Long STUDENT_ID = 31L;
    private static final Long TEACHER_ID = 32L;
    private static final Long ADMIN_ID = 34L;

    @Mock
    private ForumReportMapper reportMapper;

    @Mock
    private ForumModerationLogMapper moderationLogMapper;

    @Mock
    private ForumPostMapper postMapper;

    @Mock
    private ForumReplyMapper replyMapper;

    @Mock
    private SysUserMapper userMapper;

    @Mock
    private AdminAuditService auditService;

    @InjectMocks
    private ForumGovernanceServiceImpl governanceService;

    private final AtomicReference<ForumModerationLog> lastInsertedLog = new AtomicReference<>();

    @BeforeAll
    static void registerLambdaCache() {
        MybatisLambdaCache.register(ForumReport.class, ForumModerationLog.class, ForumPost.class);
    }

    @Test
    void rejectsReportingOwnContent() {
        given(postMapper.selectById(POST_ID)).willReturn(post(TEACHER_ID));

        assertThatThrownBy(() -> governanceService.report(
                principal(TEACHER_ID, "TEACHER"),
                new ForumReportRequest("POST", POST_ID, "SPAM", null)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能举报自己");

        verify(reportMapper, never()).insert(any(ForumReport.class));
    }

    @Test
    void rejectsDuplicatePendingReport() {
        given(postMapper.selectById(POST_ID)).willReturn(post(TEACHER_ID));
        given(reportMapper.selectCount(any())).willReturn(1L);

        assertThatThrownBy(() -> governanceService.report(
                principal(STUDENT_ID, "STUDENT"),
                new ForumReportRequest("POST", POST_ID, "SPAM", "广告内容")
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已举报过");
    }

    @Test
    void storesPendingReportWithTargetSummary() {
        given(postMapper.selectById(POST_ID)).willReturn(post(TEACHER_ID));
        given(reportMapper.selectCount(any())).willReturn(0L);
        given(reportMapper.insert(any(ForumReport.class))).willAnswer(invocation -> {
            ((ForumReport) invocation.getArgument(0)).setId(REPORT_ID);
            return 1;
        });
        ForumReport persisted = report(STUDENT_ID, ForumReport.STATUS_PENDING);
        given(reportMapper.selectById(REPORT_ID)).willReturn(persisted);
        given(userMapper.selectByIdsIncludingDeleted(any()))
                .willReturn(List.of(user(STUDENT_ID, "张三")));
        given(postMapper.selectByIds(any())).willReturn(List.of(post(TEACHER_ID)));

        ForumReportResponse response = governanceService.report(
                principal(STUDENT_ID, "STUDENT"),
                new ForumReportRequest("POST", POST_ID, "SPAM", "疑似广告")
        );

        assertThat(response.status()).isEqualTo(ForumReport.STATUS_PENDING);
        assertThat(response.reporterName()).isEqualTo("张三");
        assertThat(response.targetSummary()).contains("极限怎么理解");
    }

    @Test
    void adminHideResolvesReportAndWritesLogs() {
        stubModerationBase(ForumReport.STATUS_PENDING, 1);

        ForumModerationLogResponse log = governanceService.moderate(
                ADMIN_ID,
                new ForumModerationRequest(
                        "POST",
                        POST_ID,
                        "HIDE",
                        "内容与课程无关，先隐藏",
                        REPORT_ID,
                        null,
                        null
                )
        );

        verify(postMapper).updateStatusAndDeleted(POST_ID, "HIDDEN", 0);
        ArgumentCaptor<ForumModerationLog> captor =
                ArgumentCaptor.forClass(ForumModerationLog.class);
        verify(moderationLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getReason()).isEqualTo("内容与课程无关，先隐藏");
        assertThat(captor.getValue().getPatrol()).isZero();
        verify(reportMapper).update(isNull(), any());
        verify(auditService).record(
                eq(ADMIN_ID),
                eq("FORUM_HIDE"),
                eq("POST"),
                eq(String.valueOf(POST_ID)),
                any()
        );
        assertThat(log.action()).isEqualTo("HIDE");
        assertThat(log.operatorName()).isEqualTo("管理员");
    }

    @Test
    void patrolModerationWorksWithoutReport() {
        given(postMapper.selectById(POST_ID)).willReturn(post(STUDENT_ID));
        given(postMapper.updateStatusAndDeleted(POST_ID, "HIDDEN", 0)).willReturn(1);
        stubLogWrite(LOG_ID);

        ForumModerationLogResponse log = governanceService.moderate(
                ADMIN_ID,
                new ForumModerationRequest(
                        "POST",
                        POST_ID,
                        "HIDE",
                        "主动巡查：违规内容",
                        null,
                        null,
                        null
                )
        );

        assertThat(log.patrol()).isTrue();
        verify(reportMapper, never()).update(isNull(), any());
    }

    @Test
    void editRequiresTitleAndContent() {
        given(reportMapper.selectById(REPORT_ID))
                .willReturn(report(STUDENT_ID, ForumReport.STATUS_PENDING));
        given(postMapper.selectById(POST_ID)).willReturn(post(TEACHER_ID));

        assertThatThrownBy(() -> governanceService.moderate(
                ADMIN_ID,
                new ForumModerationRequest(
                        "POST",
                        POST_ID,
                        "EDIT",
                        "修正标题与内容",
                        REPORT_ID,
                        "新的标题",
                        null
                )
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须提供新的正文");
    }

    @Test
    void moderationRejectsReportTargetMismatch() {
        ForumReport otherTarget = report(STUDENT_ID, ForumReport.STATUS_PENDING);
        otherTarget.setTargetId(999L);
        given(reportMapper.selectById(REPORT_ID)).willReturn(otherTarget);

        assertThatThrownBy(() -> governanceService.moderate(
                ADMIN_ID,
                new ForumModerationRequest(
                        "POST",
                        POST_ID,
                        "HIDE",
                        "目标不一致",
                        REPORT_ID,
                        null,
                        null
                )
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不一致");
    }

    @Test
    void restoreUsesRawUpdateSoDeletedContentCanComeBack() {
        given(postMapper.updateStatusAndDeleted(POST_ID, "PUBLISHED", 0)).willReturn(1);
        stubLogWrite(LOG_ID);

        ForumModerationLogResponse log = governanceService.moderate(
                ADMIN_ID,
                new ForumModerationRequest(
                        "POST",
                        POST_ID,
                        "RESTORE",
                        "复查后恢复",
                        null,
                        null,
                        null
                )
        );

        assertThat(log.action()).isEqualTo("RESTORE");
        verify(postMapper).updateStatusAndDeleted(POST_ID, "PUBLISHED", 0);
    }

    private void stubModerationBase(String reportStatus, int updatedStatusRows) {
        ForumReport report = report(STUDENT_ID, reportStatus);
        given(reportMapper.selectById(REPORT_ID)).willReturn(report);
        given(postMapper.selectById(POST_ID)).willReturn(post(TEACHER_ID));
        given(postMapper.updateStatusAndDeleted(POST_ID, "HIDDEN", 0))
                .willReturn(updatedStatusRows);
        stubLogWrite(LOG_ID);
    }

    private void stubLogWrite(Long logId) {
        given(moderationLogMapper.insert(any(ForumModerationLog.class))).willAnswer(invocation -> {
            ForumModerationLog log = invocation.getArgument(0);
            log.setId(logId);
            lastInsertedLog.set(log);
            return 1;
        });
        given(moderationLogMapper.selectById(logId))
                .willAnswer(invocation -> lastInsertedLog.get());
        given(userMapper.selectByIdsIncludingDeleted(any()))
                .willReturn(List.of(user(ADMIN_ID, "管理员")));
    }

    private UserPrincipal principal(Long id, String... roles) {
        return new UserPrincipal(id, "user-" + id, "n/a", "用户" + id, null, 1, List.of(roles));
    }

    private ForumPost post(Long authorId) {
        ForumPost post = new ForumPost();
        post.setId(POST_ID);
        post.setCategoryId(41L);
        post.setAuthorId(authorId);
        post.setTitle("极限怎么理解");
        post.setContent("我对极限的定义有点模糊");
        post.setStatus("PUBLISHED");
        return post;
    }

    private ForumReport report(Long reporterId, String status) {
        ForumReport report = new ForumReport();
        report.setId(REPORT_ID);
        report.setTargetType(ForumReport.TARGET_POST);
        report.setTargetId(POST_ID);
        report.setReporterId(reporterId);
        report.setReason("SPAM");
        report.setDetail("疑似广告");
        report.setStatus(status);
        report.setCreatedAt(LocalDateTime.of(2026, 9, 20, 11, 0));
        return report;
    }

    private SysUser user(Long id, String displayName) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername("user-" + id);
        user.setDisplayName(displayName);
        return user;
    }
}
