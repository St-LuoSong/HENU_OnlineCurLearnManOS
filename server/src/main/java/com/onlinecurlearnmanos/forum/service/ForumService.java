package com.onlinecurlearnmanos.forum.service;

import com.onlinecurlearnmanos.common.api.PageResult;
import com.onlinecurlearnmanos.forum.dto.ForumCategoryResponse;
import com.onlinecurlearnmanos.forum.dto.ForumPostDetailResponse;
import com.onlinecurlearnmanos.forum.dto.ForumPostSaveRequest;
import com.onlinecurlearnmanos.forum.dto.ForumPostSummaryResponse;
import com.onlinecurlearnmanos.forum.dto.ForumRankingsResponse;
import com.onlinecurlearnmanos.forum.dto.ForumReplyResponse;
import com.onlinecurlearnmanos.forum.dto.ForumReplySaveRequest;
import com.onlinecurlearnmanos.security.UserPrincipal;

import java.util.List;

public interface ForumService {

    List<ForumCategoryResponse> categories();

    /** 右侧排行榜：同一时间窗口下按浏览 / 点赞 / 评论各取前 N 条。 */
    ForumRankingsResponse rankings(String window, int limit, UserPrincipal principal);

    PageResult<ForumPostSummaryResponse> listPosts(
            long page,
            long size,
            Long categoryId,
            String keyword,
            UserPrincipal principal
    );

    ForumPostDetailResponse getPost(Long id, UserPrincipal principal);

    ForumPostDetailResponse createPost(UserPrincipal principal, ForumPostSaveRequest request);

    ForumPostDetailResponse updatePost(Long id, UserPrincipal principal, ForumPostSaveRequest request);

    void deletePost(Long id, UserPrincipal principal);

    ForumPostSummaryResponse like(Long id, UserPrincipal principal);

    ForumPostSummaryResponse unlike(Long id, UserPrincipal principal);

    ForumReplyResponse createReply(Long postId, UserPrincipal principal, ForumReplySaveRequest request);

    ForumReplyResponse updateReply(Long replyId, UserPrincipal principal, ForumReplySaveRequest request);

    void deleteReply(Long replyId, UserPrincipal principal);
}
