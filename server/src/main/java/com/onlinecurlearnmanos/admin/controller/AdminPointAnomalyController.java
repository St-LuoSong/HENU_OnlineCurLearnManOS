package com.onlinecurlearnmanos.admin.controller;

import com.onlinecurlearnmanos.admin.dto.PointAnomalyResponse;
import com.onlinecurlearnmanos.admin.service.AdminPointAnomalyService;
import com.onlinecurlearnmanos.common.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/points")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminPointAnomalyController {

    private final AdminPointAnomalyService anomalyService;

    @GetMapping("/anomalies")
    public ApiResponse<PointAnomalyResponse> anomalies() {
        return ApiResponse.success(anomalyService.anomalies());
    }
}
