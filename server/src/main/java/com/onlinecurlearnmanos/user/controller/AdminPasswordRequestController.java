package com.onlinecurlearnmanos.user.controller;

import com.onlinecurlearnmanos.common.api.ApiResponse;
import com.onlinecurlearnmanos.common.api.PageResult;
import com.onlinecurlearnmanos.security.UserPrincipal;
import com.onlinecurlearnmanos.user.dto.PasswordChangeRequestResponse;
import com.onlinecurlearnmanos.user.dto.PasswordChangeReviewRequest;
import com.onlinecurlearnmanos.user.service.PasswordChangeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员安全审批：处理学生 / 教师的密码变更申请。
 * 审批界面只展示申请人、时间与状态，绝不返回任何密码内容。
 */
@RestController
@RequestMapping("/api/v1/admin/password-requests")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminPasswordRequestController {

    private final PasswordChangeService passwordChangeService;

    @GetMapping
    public ApiResponse<PageResult<PasswordChangeRequestResponse>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String status
    ) {
        return ApiResponse.success(passwordChangeService.list(page, size, status));
    }

    @PutMapping("/{id}/review")
    public ApiResponse<PasswordChangeRequestResponse> review(
            @PathVariable Long id,
            @Valid @RequestBody PasswordChangeReviewRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(
                passwordChangeService.review(principal.getId(), id, request)
        );
    }
}
