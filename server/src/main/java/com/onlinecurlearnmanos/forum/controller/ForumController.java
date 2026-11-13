package com.onlinecurlearnmanos.forum.controller;

import com.onlinecurlearnmanos.common.api.ApiResponse;
import com.onlinecurlearnmanos.common.api.PageResult;
import com.onlinecurlearnmanos.forum.dto.ForumCategoryResponse;
import com.onlinecurlearnmanos.forum.dto.ForumAnnouncementRequest;
import com.onlinecurlearnmanos.forum.dto.ForumAnnouncementResponse;
import com.onlinecurlearnmanos.forum.dto.ForumMessageAckRequest;
import com.onlinecurlearnmanos.forum.dto.ForumMessageListResponse;
import com.onlinecurlearnmanos.forum.dto.ForumPostDetailResponse;
import com.onlinecurlearnmanos.forum.dto.ForumPostSaveRequest;
import com.onlinecurlearnmanos.forum.dto.ForumPostSummaryResponse;
import com.onlinecurlearnmanos.forum.dto.ForumReplyResponse;
import com.onlinecurlearnmanos.forum.dto.ForumReplySaveRequest;
import com.onlinecurlearnmanos.forum.dto.ForumRankingsResponse;
import com.onlinecurlearnmanos.forum.dto.ForumReportRequest;
import com.onlinecurlearnmanos.forum.dto.ForumReportResponse;
import com.onlinecurlearnmanos.forum.service.ForumAnnouncementService;
import com.onlinecurlearnmanos.forum.service.ForumGovernanceService;
import com.onlinecurlearnmanos.forum.service.ForumMessageService;
import com.onlinecurlearnmanos.forum.service.ForumService;
import com.onlinecurlearnmanos.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/forum")
@RequiredArgsConstructor
public class ForumController {

    private final ForumService forumService;
    private final ForumGovernanceService governanceService;
    private final ForumAnnouncementService announcementService;
    private final ForumMessageService messageService;

    @GetMapping("/categories")
    public ApiResponse<List<ForumCategoryResponse>> categories() {
        return ApiResponse.success(forumService.categories());
    }

    /** 右侧排行榜：window=ALL|WEEK|MONTH，默认 5 条，最多 10 条。 */
    @GetMapping("/rankings")
    public ApiResponse<ForumRankingsResponse> rankings(
            @RequestParam(defaultValue = "ALL") String window,
            @RequestParam(defaultValue = "5") int limit,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(forumService.rankings(window, limit, principal));
    }

    /** 教师重要公示：所有登录用户可读，只有教师 / 管理员可发布。 */
    @GetMapping("/announcements")
    public ApiResponse<List<ForumAnnouncementResponse>> announcements(
            @RequestParam(defaultValue = "5") int limit,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(announcementService.listPublished(limit, principal));
    }

    /** 论坛消息：他人回复我的帖子 + 教师提醒，按时间倒序返回。 */
    @GetMapping("/messages")
    public ApiResponse<ForumMessageListResponse> messages(
            @RequestParam(defaultValue = "6") int limit,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(messageService.list(principal, limit));
    }

    /** 记录用户对一条回复或教师提醒的“已了解”状态。 */
    @PostMapping("/messages/ack")
    public ApiResponse<Void> acknowledgeMessage(
            @Valid @RequestBody ForumMessageAckRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        messageService.acknowledge(principal, request);
        return ApiResponse.success(null);
    }

    @PostMapping("/announcements")
    public ApiResponse<ForumAnnouncementResponse> createAnnouncement(
            @Valid @RequestBody ForumAnnouncementRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(announcementService.create(principal, request));
    }

    @PutMapping("/announcements/{id}")
    public ApiResponse<ForumAnnouncementResponse> updateAnnouncement(
            @PathVariable Long id,
            @Valid @RequestBody ForumAnnouncementRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(announcementService.update(id, principal, request));
    }

    @DeleteMapping("/announcements/{id}")
    public ApiResponse<Void> deleteAnnouncement(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        announcementService.delete(id, principal);
        return ApiResponse.success(null);
    }

    @GetMapping("/posts")
    public ApiResponse<PageResult<ForumPostSummaryResponse>> listPosts(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(
                forumService.listPosts(page, size, categoryId, keyword, principal)
        );
    }

    @GetMapping("/posts/{id}")
    public ApiResponse<ForumPostDetailResponse> getPost(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(forumService.getPost(id, principal));
    }

    @PostMapping("/posts")
    public ApiResponse<ForumPostDetailResponse> createPost(
            @Valid @RequestBody ForumPostSaveRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(forumService.createPost(principal, request));
    }

    @PutMapping("/posts/{id}")
    public ApiResponse<ForumPostDetailResponse> updatePost(
            @PathVariable Long id,
            @Valid @RequestBody ForumPostSaveRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(forumService.updatePost(id, principal, request));
    }

    @DeleteMapping("/posts/{id}")
    public ApiResponse<Void> deletePost(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        forumService.deletePost(id, principal);
        return ApiResponse.success(null);
    }

    @PostMapping("/posts/{id}/like")
    public ApiResponse<ForumPostSummaryResponse> like(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(forumService.like(id, principal));
    }

    @DeleteMapping("/posts/{id}/like")
    public ApiResponse<ForumPostSummaryResponse> unlike(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(forumService.unlike(id, principal));
    }

    @PostMapping("/posts/{id}/replies")
    public ApiResponse<ForumReplyResponse> createReply(
            @PathVariable Long id,
            @Valid @RequestBody ForumReplySaveRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(forumService.createReply(id, principal, request));
    }

    @PutMapping("/replies/{id}")
    public ApiResponse<ForumReplyResponse> updateReply(
            @PathVariable Long id,
            @Valid @RequestBody ForumReplySaveRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(forumService.updateReply(id, principal, request));
    }

    @DeleteMapping("/replies/{id}")
    public ApiResponse<Void> deleteReply(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        forumService.deleteReply(id, principal);
        return ApiResponse.success(null);
    }

    /**
     * 举报帖子或回复。学生与教师都可以举报他人内容，管理员通过治理接口处理。
     */
    @PostMapping("/reports")
    public ApiResponse<ForumReportResponse> report(
            @Valid @RequestBody ForumReportRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(governanceService.report(principal, request));
    }

}
