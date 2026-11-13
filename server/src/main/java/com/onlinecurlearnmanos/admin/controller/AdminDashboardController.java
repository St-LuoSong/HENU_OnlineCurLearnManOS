package com.onlinecurlearnmanos.admin.controller;

import com.onlinecurlearnmanos.admin.dto.AdminDashboardSummaryResponse;
import com.onlinecurlearnmanos.admin.service.AdminDashboardService;
import com.onlinecurlearnmanos.common.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardController {

    private final AdminDashboardService dashboardService;

    @GetMapping("/summary")
    public ApiResponse<AdminDashboardSummaryResponse> summary() {
        return ApiResponse.success(dashboardService.summary());
    }
}
