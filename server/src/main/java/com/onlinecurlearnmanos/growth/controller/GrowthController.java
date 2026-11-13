package com.onlinecurlearnmanos.growth.controller;

import com.onlinecurlearnmanos.common.api.ApiResponse;
import com.onlinecurlearnmanos.growth.dto.GrowthCycleCreateRequest;
import com.onlinecurlearnmanos.growth.dto.GrowthCycleResponse;
import com.onlinecurlearnmanos.growth.dto.GrowthDirectionResponse;
import com.onlinecurlearnmanos.growth.dto.GrowthReviewResponse;
import com.onlinecurlearnmanos.growth.dto.GrowthReviewSaveRequest;
import com.onlinecurlearnmanos.growth.dto.GrowthWorkspaceResponse;
import com.onlinecurlearnmanos.growth.service.GrowthCycleService;
import com.onlinecurlearnmanos.growth.service.GrowthWorkspaceService;
import com.onlinecurlearnmanos.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/growth")
@RequiredArgsConstructor
public class GrowthController {

    private final GrowthWorkspaceService growthWorkspaceService;
    private final GrowthCycleService growthCycleService;

    @GetMapping("/workspace")
    public ApiResponse<GrowthWorkspaceResponse> workspace(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) Long cycleId
    ) {
        return ApiResponse.success(cycleId == null
                ? growthWorkspaceService.workspace(principal.getId())
                : growthWorkspaceService.workspace(principal.getId(), cycleId));
    }

    @GetMapping("/cycles")
    public ApiResponse<List<GrowthCycleResponse>> cycles(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(growthCycleService.list(principal.getId()));
    }

    @PostMapping("/cycles")
    public ApiResponse<GrowthCycleResponse> createCycle(
            @Valid @RequestBody GrowthCycleCreateRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(growthCycleService.create(principal.getId(), request));
    }

    @PostMapping("/cycles/{id}/complete")
    public ApiResponse<GrowthCycleResponse> completeCycle(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(growthCycleService.complete(principal.getId(), id));
    }

    @PostMapping("/cycles/{id}/archive")
    public ApiResponse<GrowthCycleResponse> archiveCycle(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(growthCycleService.archive(principal.getId(), id));
    }

    @PostMapping("/cycles/{id}/direction/refresh")
    public ApiResponse<GrowthDirectionResponse> refreshDirection(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(growthWorkspaceService.refreshDirection(principal.getId(), id));
    }

    @GetMapping("/cycles/{id}/reviews")
    public ApiResponse<List<GrowthReviewResponse>> reviews(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(growthCycleService.reviews(principal.getId(), id));
    }

    @PutMapping("/cycles/{id}/reviews/{weekStart}")
    public ApiResponse<GrowthReviewResponse> saveReview(
            @PathVariable Long id,
            @PathVariable LocalDate weekStart,
            @Valid @RequestBody GrowthReviewSaveRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(
                growthCycleService.saveReview(principal.getId(), id, weekStart, request.note())
        );
    }
}
