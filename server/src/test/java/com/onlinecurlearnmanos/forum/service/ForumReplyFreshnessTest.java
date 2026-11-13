package com.onlinecurlearnmanos.forum.service;

import com.onlinecurlearnmanos.forum.dto.ForumReplyResponse;
import com.onlinecurlearnmanos.forum.entity.ForumReply;
import com.onlinecurlearnmanos.security.UserPrincipal;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 审计 D1 回归：发布回复后必须重新读库再返回 DTO。
 *
 * <p>此前直接把插入用的内存对象转成响应，`createdAt` 仍为 null，
 * 前端渲染 `value.replace(...)` 时抛错，表现为回复后页面卡死、刷新才恢复。</p>
 */
class ForumReplyFreshnessTest extends ForumServiceTestSupport {

    @Test
    void createdReplyCarriesDatabaseTimestampAndPermissionFlags() {
        UserPrincipal student = principal(STUDENT_ID, "STUDENT");
        given(postMapper.selectById(POST_ID)).willReturn(post(TEACHER_ID));
        given(replyMapper.insert(any(ForumReply.class))).willAnswer(invocation -> {
            ((ForumReply) invocation.getArgument(0)).setId(REPLY_ID);
            return 1;
        });
        ForumReply persisted = reply(STUDENT_ID);
        persisted.setContent("我的回复");
        persisted.setCreatedAt(LocalDateTime.of(2026, 9, 20, 11, 0));
        given(replyMapper.selectById(REPLY_ID)).willReturn(persisted);
        given(userMapper.selectByIdsIncludingDeleted(any()))
                .willReturn(List.of(user(STUDENT_ID, "张三")));
        given(roleMapper.selectUserIdsByRoleCode(any(), any())).willReturn(List.of());

        ForumReplyResponse response = forumService.createReply(
                POST_ID,
                student,
                replyRequest("我的回复")
        );

        assertThat(response.createdAt()).isEqualTo(LocalDateTime.of(2026, 9, 20, 11, 0));
        assertThat(response.content()).isEqualTo("我的回复");
        assertThat(response.authorName()).isEqualTo("张三");
        assertThat(response.authorRole()).isEqualTo("STUDENT");
        assertThat(response.canEdit()).isTrue();
        assertThat(response.canDelete()).isTrue();
        assertThat(response.canReport()).isFalse();
        verify(replyMapper).selectById(REPLY_ID);
    }

    @Test
    void updatedReplyAlsoReturnsPersistedState() {
        UserPrincipal student = principal(STUDENT_ID, "STUDENT");
        given(replyMapper.selectById(REPLY_ID)).willReturn(reply(STUDENT_ID));
        given(userMapper.selectByIdsIncludingDeleted(any()))
                .willReturn(List.of(user(STUDENT_ID, "张三")));
        given(roleMapper.selectUserIdsByRoleCode(any(), any())).willReturn(List.of());

        ForumReplyResponse response = forumService.updateReply(
                REPLY_ID,
                student,
                replyRequest("更新后的回复")
        );

        assertThat(response.createdAt()).isNotNull();
        assertThat(response.canEdit()).isTrue();
        // 一次读取待更新记录，一次写后回读，确保响应里的时间字段来自数据库
        verify(replyMapper, times(2)).selectById(REPLY_ID);
    }
}
