package com.onlinecurlearnmanos.forum.service;

import com.onlinecurlearnmanos.common.api.PageResult;
import com.onlinecurlearnmanos.common.exception.BusinessException;
import com.onlinecurlearnmanos.forum.dto.ForumPostSaveRequest;
import com.onlinecurlearnmanos.forum.dto.ForumPostSummaryResponse;
import com.onlinecurlearnmanos.forum.entity.ForumCategory;
import com.onlinecurlearnmanos.forum.entity.ForumPost;
import com.onlinecurlearnmanos.security.UserPrincipal;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 论坛权限矩阵（审计 D2/D9）：学生只能动自己的内容；教师可以额外删除学生内容，
 * 但不能删除其他教师或管理员的内容；管理员可以删除任意内容。
 */
class ForumPermissionMatrixTest extends ForumServiceTestSupport {

    @Test
    void studentCanDeleteOwnPost() {
        given(postMapper.selectById(POST_ID)).willReturn(post(STUDENT_ID));

        forumService.deletePost(POST_ID, principal(STUDENT_ID, "STUDENT"));

        verify(postMapper).deleteById(POST_ID);
    }

    @Test
    void studentCannotDeleteOthersPost() {
        given(postMapper.selectById(POST_ID)).willReturn(post(TEACHER_ID));

        assertThatThrownBy(() -> forumService.deletePost(
                POST_ID,
                principal(STUDENT_ID, "STUDENT")
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权删除");
        verify(postMapper, never()).deleteById(anyLong());
    }

    @Test
    void teacherCanDeleteStudentContent() {
        given(postMapper.selectById(POST_ID)).willReturn(post(STUDENT_ID));
        given(roleMapper.selectUserIdsByRoleCode(any(), eq("TEACHER"))).willReturn(List.of());
        given(roleMapper.selectUserIdsByRoleCode(any(), eq("ADMIN"))).willReturn(List.of());

        forumService.deletePost(POST_ID, principal(TEACHER_ID, "TEACHER"));

        verify(postMapper).deleteById(POST_ID);
    }

    @Test
    void teacherCannotDeleteOtherTeacherContent() {
        given(postMapper.selectById(POST_ID)).willReturn(post(OTHER_TEACHER_ID));
        given(roleMapper.selectUserIdsByRoleCode(any(), eq("TEACHER")))
                .willReturn(List.of(OTHER_TEACHER_ID));
        given(roleMapper.selectUserIdsByRoleCode(any(), eq("ADMIN"))).willReturn(List.of());

        assertThatThrownBy(() -> forumService.deletePost(
                POST_ID,
                principal(TEACHER_ID, "TEACHER")
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权删除");
    }

    @Test
    void teacherCannotDeleteAdminContent() {
        given(postMapper.selectById(POST_ID)).willReturn(post(ADMIN_ID));
        given(roleMapper.selectUserIdsByRoleCode(any(), eq("TEACHER"))).willReturn(List.of());
        given(roleMapper.selectUserIdsByRoleCode(any(), eq("ADMIN"))).willReturn(List.of(ADMIN_ID));

        assertThatThrownBy(() -> forumService.deletePost(
                POST_ID,
                principal(TEACHER_ID, "TEACHER")
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权删除");
    }

    @Test
    void adminCanDeleteAnyContent() {
        given(postMapper.selectById(POST_ID)).willReturn(post(OTHER_TEACHER_ID));

        forumService.deletePost(POST_ID, principal(ADMIN_ID, "ADMIN"));

        verify(postMapper).deleteById(POST_ID);
    }

    @Test
    void teacherCannotEditOthersPost() {
        given(postMapper.selectById(POST_ID)).willReturn(post(STUDENT_ID));
        ForumPostSaveRequest request = new ForumPostSaveRequest(
                CATEGORY_ID,
                "新标题",
                "新内容",
                List.of()
        );

        assertThatThrownBy(() -> forumService.updatePost(
                POST_ID,
                principal(TEACHER_ID, "TEACHER"),
                request
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能编辑自己");
    }

    @Test
    void listPostsExposesAuthorRoleBadgesAndBatchesLookups() {
        ForumPost teacherPost = post(TEACHER_ID);
        ForumPost studentPost = post(STUDENT_ID);
        studentPost.setId(12L);
        Page<ForumPost> page = new Page<>(1, 10);
        page.setRecords(List.of(teacherPost, studentPost));
        page.setTotal(2);
        given(postMapper.selectPage(any(), any())).willReturn(page);
        given(categoryMapper.selectByIds(any())).willReturn(List.of(category()));
        given(userMapper.selectByIdsIncludingDeleted(any())).willReturn(List.of(
                user(TEACHER_ID, "李老师"),
                user(STUDENT_ID, "张三")
        ));
        given(roleMapper.selectUserIdsByRoleCode(any(), eq("TEACHER")))
                .willReturn(List.of(TEACHER_ID));
        given(roleMapper.selectUserIdsByRoleCode(any(), eq("ADMIN"))).willReturn(List.of());
        given(likeMapper.selectLikedPostIds(any(), any())).willReturn(List.of());

        PageResult<ForumPostSummaryResponse> result = forumService.listPosts(
                1,
                10,
                null,
                null,
                principal(STUDENT_ID, "STUDENT")
        );

        ForumPostSummaryResponse teacherSummary = result.records().get(0);
        assertThat(teacherSummary.authorRole()).isEqualTo("TEACHER");
        assertThat(teacherSummary.authorBadges()).containsExactly("教师");
        assertThat(teacherSummary.canEdit()).isFalse();
        assertThat(teacherSummary.canDelete()).isFalse();
        assertThat(teacherSummary.canReport()).isTrue();

        ForumPostSummaryResponse studentSummary = result.records().get(1);
        assertThat(studentSummary.authorRole()).isEqualTo("STUDENT");
        assertThat(studentSummary.authorBadges()).isEmpty();
        assertThat(studentSummary.canEdit()).isTrue();
        assertThat(studentSummary.canDelete()).isTrue();
        assertThat(studentSummary.canReport()).isFalse();

        verify(userMapper, times(1)).selectByIdsIncludingDeleted(any());
        verify(categoryMapper, times(1)).selectByIds(any());
        verify(roleMapper, times(2)).selectUserIdsByRoleCode(any(), any());
        verify(likeMapper, times(1)).selectLikedPostIds(any(), any());
    }

    @Test
    void teacherSeesDeletePermissionOnlyForStudentContent() {
        ForumPost studentPost = post(STUDENT_ID);
        Page<ForumPost> page = new Page<>(1, 10);
        page.setRecords(List.of(studentPost));
        page.setTotal(1);
        given(postMapper.selectPage(any(), any())).willReturn(page);
        given(categoryMapper.selectByIds(any())).willReturn(List.of(category()));
        given(userMapper.selectByIdsIncludingDeleted(any()))
                .willReturn(List.of(user(STUDENT_ID, "张三")));
        given(roleMapper.selectUserIdsByRoleCode(any(), eq("TEACHER"))).willReturn(List.of());
        given(roleMapper.selectUserIdsByRoleCode(any(), eq("ADMIN"))).willReturn(List.of());
        given(likeMapper.selectLikedPostIds(any(), any())).willReturn(List.of());
        UserPrincipal teacher = principal(TEACHER_ID, "TEACHER");

        ForumPostSummaryResponse summary = forumService.listPosts(1, 10, null, null, teacher)
                .records()
                .get(0);

        assertThat(summary.canDelete()).isTrue();
        assertThat(summary.canEdit()).isFalse();
    }

    private ForumCategory category() {
        ForumCategory category = new ForumCategory();
        category.setId(CATEGORY_ID);
        category.setCode("COURSE");
        category.setName("课程讨论");
        return category;
    }
}
