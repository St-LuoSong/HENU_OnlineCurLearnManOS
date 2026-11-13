package com.onlinecurlearnmanos.user.controller;

import com.onlinecurlearnmanos.common.api.ApiResponse;
import com.onlinecurlearnmanos.security.UserPrincipal;
import com.onlinecurlearnmanos.user.dto.PasswordChangeRequestResponse;
import com.onlinecurlearnmanos.user.dto.PasswordChangeSubmitRequest;
import com.onlinecurlearnmanos.user.dto.UserProfileResponse;
import com.onlinecurlearnmanos.user.dto.UserProfileUpdateRequest;
import com.onlinecurlearnmanos.user.service.PasswordChangeService;
import com.onlinecurlearnmanos.user.service.UserProfileService;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * 学生 / 教师个人中心：基本资料与密码变更申请。
 * 登录标识（学号 / 工号）只读，密码必须经管理员审批。
 */
@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
public class UserAccountController {

    private final UserProfileService profileService;
    private final PasswordChangeService passwordChangeService;

    @GetMapping("/profile")
    public ApiResponse<UserProfileResponse> getProfile(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(profileService.get(principal.getId()));
    }

    @PutMapping("/profile")
    public ApiResponse<UserProfileResponse> updateProfile(
            @Valid @RequestBody UserProfileUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(profileService.update(principal.getId(), request));
    }

    @GetMapping("/password-request")
    public ApiResponse<PasswordChangeRequestResponse> pendingPasswordRequest(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(passwordChangeService.pending(principal.getId()));
    }

    @PostMapping("/password-request")
    public ApiResponse<PasswordChangeRequestResponse> submitPasswordRequest(
            @Valid @RequestBody PasswordChangeSubmitRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(
                passwordChangeService.submit(principal.getId(), request)
        );
    }

    @DeleteMapping("/password-request/{id}")
    public ApiResponse<Void> cancelPasswordRequest(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        passwordChangeService.cancel(principal.getId(), id);
        return ApiResponse.success(null);
    }
}
