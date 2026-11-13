package com.onlinecurlearnmanos.point.controller;

import com.onlinecurlearnmanos.common.api.ApiResponse;
import com.onlinecurlearnmanos.common.api.PageResult;
import com.onlinecurlearnmanos.point.dto.PointAccountResponse;
import com.onlinecurlearnmanos.point.dto.PointAdjustRequest;
import com.onlinecurlearnmanos.point.dto.PointTransactionResponse;
import com.onlinecurlearnmanos.point.service.PointService;
import com.onlinecurlearnmanos.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/points")
@RequiredArgsConstructor
public class PointController {

    private final PointService pointService;

    @GetMapping("/account")
    public ApiResponse<PointAccountResponse> account(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(pointService.getAccount(principal.getId()));
    }

    @GetMapping("/transactions")
    public ApiResponse<PageResult<PointTransactionResponse>> transactions(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(pointService.listTransactions(principal.getId(), page, size));
    }

    @PostMapping("/adjust")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PointAccountResponse> adjust(
            @RequestParam Long userId,
            @Valid @RequestBody PointAdjustRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(
                pointService.adjust(userId, request.amount(), request.remark(), principal.getId())
        );
    }
}
