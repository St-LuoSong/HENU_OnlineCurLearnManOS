package com.onlinecurlearnmanos.admin.controller;

import com.onlinecurlearnmanos.admin.dto.AdminUserResponse;
import com.onlinecurlearnmanos.admin.dto.AdminUserDeleteRequest;
import com.onlinecurlearnmanos.admin.dto.AdminUserStatusRequest;
import com.onlinecurlearnmanos.admin.service.AdminUserService;
import com.onlinecurlearnmanos.common.api.ApiResponse;
import com.onlinecurlearnmanos.common.api.PageResult;
import com.onlinecurlearnmanos.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService userService;

    @GetMapping
    public ApiResponse<PageResult<AdminUserResponse>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.success(userService.list(page, size, role, enabled, keyword));
    }

    @PutMapping("/{id}/status")
    public ApiResponse<AdminUserResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody AdminUserStatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(
                userService.updateStatus(id, request.enabled(), principal.getId())
        );
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteAccount(
            @PathVariable Long id,
            @Valid @RequestBody AdminUserDeleteRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        userService.deleteAccount(
                id,
                request.reason(),
                request.confirmLoginId(),
                principal.getId()
        );
        return ApiResponse.success(null);
    }
}
