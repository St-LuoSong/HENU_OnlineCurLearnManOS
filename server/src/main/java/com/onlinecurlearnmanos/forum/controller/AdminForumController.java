package com.onlinecurlearnmanos.forum.controller;

import com.onlinecurlearnmanos.common.api.ApiResponse;
import com.onlinecurlearnmanos.common.api.PageResult;
import com.onlinecurlearnmanos.forum.dto.ForumAnnouncementResponse;
import com.onlinecurlearnmanos.forum.dto.ForumModerationLogResponse;
import com.onlinecurlearnmanos.forum.dto.ForumModerationRequest;
import com.onlinecurlearnmanos.forum.dto.ForumReportResponse;
import com.onlinecurlearnmanos.forum.service.ForumGovernanceService;
import com.onlinecurlearnmanos.forum.service.ForumAnnouncementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.onlinecurlearnmanos.security.UserPrincipal;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/forum")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminForumController {

    private final ForumGovernanceService governanceService;
    private final ForumAnnouncementService announcementService;

    @GetMapping("/reports")
    public ApiResponse<PageResult<ForumReportResponse>> listReports(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String targetType
    ) {
        return ApiResponse.success(governanceService.listReports(page, size, status, targetType));
    }

    @GetMapping("/reports/pending")
    public ApiResponse<List<ForumReportResponse>> pendingReports(
            @RequestParam String targetType,
            @RequestParam Long targetId
    ) {
        return ApiResponse.success(
                governanceService.listPendingReportsFor(targetType, targetId)
        );
    }

    @PostMapping("/moderate")
    public ApiResponse<ForumModerationLogResponse> moderate(
            @Valid @RequestBody ForumModerationRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(governanceService.moderate(principal.getId(), request));
    }

    @GetMapping("/moderation-logs")
    public ApiResponse<PageResult<ForumModerationLogResponse>> listLogs(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size
    ) {
        return ApiResponse.success(governanceService.listLogs(page, size));
    }

    /** 教师公示治理：管理员可查看全部状态，并隐藏 / 恢复任意公示（写审计）。 */
    @GetMapping("/announcements")
    public ApiResponse<PageResult<ForumAnnouncementResponse>> listAnnouncements(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(announcementService.listForAdmin(page, size, status, principal));
    }

    @PutMapping("/announcements/{id}/status")
    public ApiResponse<ForumAnnouncementResponse> updateAnnouncementStatus(
            @PathVariable Long id,
            @RequestParam String status,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(
                announcementService.updateStatus(principal.getId(), id, status)
        );
    }
}
