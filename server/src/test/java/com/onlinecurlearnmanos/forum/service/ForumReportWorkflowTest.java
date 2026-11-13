package com.onlinecurlearnmanos.forum.service;

import com.onlinecurlearnmanos.admin.service.AdminAuditService;
import com.onlinecurlearnmanos.common.exception.BusinessException;
import com.onlinecurlearnmanos.forum.dto.ForumModerationLogResponse;
import com.onlinecurlearnmanos.forum.dto.ForumModerationRequest;
import com.onlinecurlearnmanos.forum.dto.ForumReportRequest;
import com.onlinecurlearnmanos.forum.dto.ForumReportResponse;
import com.onlinecurlearnmanos.forum.entity.ForumCategory;
import com.onlinecurlearnmanos.forum.entity.ForumModerationLog;
import com.onlinecurlearnmanos.forum.entity.ForumPost;
import com.onlinecurlearnmanos.forum.entity.ForumReply;
import com.onlinecurlearnmanos.forum.entity.ForumReport;
import com.onlinecurlearnmanos.forum.mapper.ForumCategoryMapper;
import com.onlinecurlearnmanos.forum.mapper.ForumLikeMapper;
import com.onlinecurlearnmanos.forum.mapper.ForumModerationLogMapper;
import com.onlinecurlearnmanos.forum.mapper.ForumPostMapper;
import com.onlinecurlearnmanos.forum.mapper.ForumReplyMapper;
import com.onlinecurlearnmanos.forum.mapper.ForumReportMapper;
import com.onlinecurlearnmanos.security.UserPrincipal;
import com.onlinecurlearnmanos.support.MybatisLambdaCache;
import com.onlinecurlearnmanos.user.entity.SysUser;
import com.onlinecurlearnmanos.user.mapper.SysRoleMapper;
import com.onlinecurlearnmanos.user.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 举报治理完整链路（14-E 回归）：学生举报 → 待处理队列 → 管理员处置 →
 * 举报结案 + 治理日志 + 审计流水，并验证隐藏内容确实从公开列表与详情中消失。
 */
@ExtendWith(MockitoExtension.class)
class ForumReportWorkflowTest {

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
    private ForumLikeMapper likeMapper;

    @Mock
    private ForumMediaService mediaService;

    @Mock
    private ForumCategoryMapper categoryMapper;

    @Mock
    private SysUserMapper userMapper;

    @Mock
    private SysRoleMapper roleMapper;

    @Mock
    private AdminAuditService auditService;

    @InjectMocks
    private ForumGovernanceServiceImpl governanceService;

    private ForumServiceImpl forumService;

    private final AtomicReference<ForumModerationLog> lastLog = new AtomicReference<>();

    @BeforeAll
    static void registerLambdaCache() {
        MybatisLambdaCache.register(
                ForumReport.class,
                ForumModerationLog.class,
                ForumPost.class,
                ForumReply.class,
                ForumCategory.class
        );
    }

    @BeforeEach
    void setUp() {
        forumService = new ForumServiceImpl(
                categoryMapper,
                postMapper,
                replyMapper,
                likeMapper,
                mediaService,
                userMapper,
                roleMapper
        );
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void reportEntersPendingQueueAndHideResolvesItWithAuditTrail() {
        given(postMapper.selectById(POST_ID)).willReturn(post("PUBLISHED"));
        given(reportMapper.selectCount(any())).willReturn(0L);
        given(reportMapper.insert(any(ForumReport.class))).willAnswer(invocation -> {
            ((ForumReport) invocation.getArgument(0)).setId(REPORT_ID);
            return 1;
        });
        given(reportMapper.selectById(REPORT_ID)).willReturn(pendingReport());
        given(userMapper.selectByIdsIncludingDeleted(any()))
                .willReturn(List.of(user(STUDENT_ID, "张三")));
        given(postMapper.selectByIds(any())).willReturn(List.of(post("PUBLISHED")));

        ForumReportResponse created = governanceService.report(
                principal(STUDENT_ID, "STUDENT"),
                new ForumReportRequest("POST", POST_ID, "SPAM", "疑似广告推广")
        );
        assertThat(created.status()).isEqualTo(ForumReport.STATUS_PENDING);
        assertThat(created.targetSummary()).contains("极限怎么理解");

        given(reportMapper.selectList(any())).willReturn(List.of(pendingReport()));
        assertThat(governanceService.listPendingReportsFor("POST", POST_ID)).hasSize(1);

        given(postMapper.updateStatusAndDeleted(POST_ID, "HIDDEN", 0)).willReturn(1);
        stubLogWrite();
        ForumModerationLogResponse log = governanceService.moderate(
                ADMIN_ID,
                new ForumModerationRequest(
                        "POST",
                        POST_ID,
                        "HIDE",
                        "内容与课程无关",
                        REPORT_ID,
                        null,
                        null
                )
        );

        verify(postMapper).updateStatusAndDeleted(POST_ID, "HIDDEN", 0);
        ArgumentCaptor<LambdaUpdateWrapper<ForumReport>> captor =
                ArgumentCaptor.forClass((Class) LambdaUpdateWrapper.class);
        verify(reportMapper).update(isNull(), captor.capture());
        assertThat(captor.getValue().getParamNameValuePairs().values())
                .contains(ForumReport.STATUS_RESOLVED, ADMIN_ID, "HIDE", "内容与课程无关");
        verify(auditService).record(
                eq(ADMIN_ID),
                eq("FORUM_HIDE"),
                eq("POST"),
                eq(String.valueOf(POST_ID)),
                any()
        );
        assertThat(log.action()).isEqualTo("HIDE");
        assertThat(lastLog.get().getPatrol()).isZero();
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void hiddenPostDisappearsFromPublicListAndDetail() {
        Page<ForumPost> emptyPage = new Page<>(1, 8);
        given(postMapper.selectPage(any(), any())).willReturn(emptyPage);

        forumService.listPosts(1, 8, null, null, principal(STUDENT_ID, "STUDENT"));

        ArgumentCaptor<LambdaQueryWrapper<ForumPost>> captor =
                ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);
        verify(postMapper).selectPage(any(), captor.capture());
        // 公开列表只查 PUBLISHED，被隐藏（status=HIDDEN）的帖子不会出现在列表里。
        // 注意：MyBatis-Plus 的参数是延迟写入的，必须先渲染 SQL 片段才能读到参数值。
        LambdaQueryWrapper<ForumPost> listWrapper = captor.getValue();
        assertThat(listWrapper.getSqlSegment()).contains("status");
        assertThat(listWrapper.getParamNameValuePairs().values()).contains("PUBLISHED");

        given(postMapper.selectById(POST_ID)).willReturn(post("HIDDEN"));
        assertThatThrownBy(() -> forumService.getPost(POST_ID, principal(STUDENT_ID, "STUDENT")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已被隐藏");
        assertThatThrownBy(() -> forumService.getPost(POST_ID, principal(TEACHER_ID, "TEACHER")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已被隐藏");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void dismissOnlyClosesReportAndKeepsContentVisible() {
        given(reportMapper.selectById(REPORT_ID)).willReturn(pendingReport());
        given(postMapper.selectById(POST_ID)).willReturn(post("PUBLISHED"));
        stubLogWrite();

        governanceService.moderate(
                ADMIN_ID,
                new ForumModerationRequest(
                        "POST",
                        POST_ID,
                        "DISMISS",
                        "复核后内容合规",
                        REPORT_ID,
                        null,
                        null
                )
        );

        verify(postMapper, never()).updateStatusAndDeleted(any(), any(), anyInt());
        ArgumentCaptor<LambdaUpdateWrapper<ForumReport>> captor =
                ArgumentCaptor.forClass((Class) LambdaUpdateWrapper.class);
        verify(reportMapper).update(isNull(), captor.capture());
        assertThat(captor.getValue().getParamNameValuePairs().values())
                .contains(ForumReport.STATUS_DISMISSED, "复核后内容合规");
        verify(auditService).record(
                eq(ADMIN_ID),
                eq("FORUM_DISMISS"),
                eq("POST"),
                eq(String.valueOf(POST_ID)),
                any()
        );
    }

    @Test
    void deleteModerationFlagsRowAsDeletedForLogicDeleteFiltering() {
        given(reportMapper.selectById(REPORT_ID)).willReturn(pendingReport());
        given(postMapper.selectById(POST_ID)).willReturn(post("PUBLISHED"));
        given(postMapper.updateStatusAndDeleted(POST_ID, "DELETED", 1)).willReturn(1);
        stubLogWrite();

        ForumModerationLogResponse log = governanceService.moderate(
                ADMIN_ID,
                new ForumModerationRequest(
                        "POST",
                        POST_ID,
                        "DELETE",
                        "严重违规，删除处理",
                        REPORT_ID,
                        null,
                        null
                )
        );

        verify(postMapper).updateStatusAndDeleted(POST_ID, "DELETED", 1);
        assertThat(log.afterSummary()).isEqualTo("已删除");
        verify(auditService).record(
                eq(ADMIN_ID),
                eq("FORUM_DELETE"),
                eq("POST"),
                eq(String.valueOf(POST_ID)),
                any()
        );
    }

    private void stubLogWrite() {
        given(moderationLogMapper.insert(any(ForumModerationLog.class))).willAnswer(invocation -> {
            ForumModerationLog log = invocation.getArgument(0);
            log.setId(LOG_ID);
            lastLog.set(log);
            return 1;
        });
        given(moderationLogMapper.selectById(LOG_ID)).willAnswer(invocation -> lastLog.get());
        given(userMapper.selectByIdsIncludingDeleted(any()))
                .willReturn(List.of(user(ADMIN_ID, "管理员")));
    }

    private UserPrincipal principal(Long id, String... roles) {
        return new UserPrincipal(id, "user-" + id, "n/a", "用户" + id, null, 1, List.of(roles));
    }

    private ForumPost post(String status) {
        ForumPost post = new ForumPost();
        post.setId(POST_ID);
        post.setCategoryId(41L);
        post.setAuthorId(TEACHER_ID);
        post.setTitle("极限怎么理解");
        post.setContent("我对极限的定义有点模糊");
        post.setStatus(status);
        post.setCreatedAt(LocalDateTime.of(2026, 9, 20, 10, 0));
        return post;
    }

    private ForumReport pendingReport() {
        ForumReport report = new ForumReport();
        report.setId(REPORT_ID);
        report.setTargetType(ForumReport.TARGET_POST);
        report.setTargetId(POST_ID);
        report.setReporterId(STUDENT_ID);
        report.setReason("SPAM");
        report.setDetail("疑似广告推广");
        report.setStatus(ForumReport.STATUS_PENDING);
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
