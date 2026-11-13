package com.onlinecurlearnmanos.auth.controller;

import com.onlinecurlearnmanos.auth.dto.AuthResponse;
import com.onlinecurlearnmanos.auth.dto.LoginRequest;
import com.onlinecurlearnmanos.auth.dto.RegisterRequest;
import com.onlinecurlearnmanos.auth.dto.UserResponse;
import com.onlinecurlearnmanos.auth.service.AuthService;
import com.onlinecurlearnmanos.common.api.ApiResponse;
import com.onlinecurlearnmanos.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    /**
     * 管理端专属登录入口：普通端页面不出现该入口，且只接受 ADMIN 账号。
     */
    @PostMapping("/admin/login")
    public ApiResponse<AuthResponse> adminLogin(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.loginAdmin(request));
    }

    @GetMapping("/me")
    public ApiResponse<UserResponse> me(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(authService.currentUser(principal));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        authService.logout(authorization);
        return ApiResponse.success(null);
    }
}
