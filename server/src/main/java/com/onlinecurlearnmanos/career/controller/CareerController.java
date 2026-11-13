package com.onlinecurlearnmanos.career.controller;

import com.onlinecurlearnmanos.career.dto.CareerAnalysisGenerateRequest;
import com.onlinecurlearnmanos.career.dto.CareerAnalysisResponse;
import com.onlinecurlearnmanos.career.dto.CareerConsentRequest;
import com.onlinecurlearnmanos.career.dto.CareerComparisonResponse;
import com.onlinecurlearnmanos.career.dto.CareerDataPreviewResponse;
import com.onlinecurlearnmanos.career.dto.CareerInsightConvertRequest;
import com.onlinecurlearnmanos.career.dto.CareerInsightConvertResponse;
import com.onlinecurlearnmanos.career.dto.CareerProfileResponse;
import com.onlinecurlearnmanos.career.dto.CareerProfileSaveRequest;
import com.onlinecurlearnmanos.career.service.CareerAnalysisService;
import com.onlinecurlearnmanos.career.service.CareerProfileService;
import com.onlinecurlearnmanos.common.api.ApiResponse;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/career")
@RequiredArgsConstructor
public class CareerController {

    private final CareerProfileService profileService;
    private final CareerAnalysisService analysisService;

    @GetMapping("/profile")
    public ApiResponse<CareerProfileResponse> getProfile(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(profileService.get(principal.getId()));
    }

    @PutMapping("/profile")
    public ApiResponse<CareerProfileResponse> saveProfile(
            @Valid @RequestBody CareerProfileSaveRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(profileService.save(principal.getId(), request));
    }

    @PostMapping("/consent")
    public ApiResponse<CareerProfileResponse> updateConsent(
            @RequestBody CareerConsentRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(profileService.updateConsent(principal.getId(), request));
    }

    @GetMapping("/data-preview")
    public ApiResponse<CareerDataPreviewResponse> preview(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(profileService.preview(principal.getId()));
    }

    @GetMapping("/analyses")
    public ApiResponse<List<CareerAnalysisResponse>> list(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(analysisService.list(principal.getId()));
    }

    @GetMapping("/analyses/{id}")
    public ApiResponse<CareerAnalysisResponse> get(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(analysisService.get(principal.getId(), id));
    }

    @PostMapping("/analyses")
    public ApiResponse<CareerAnalysisResponse> generate(
            @Valid @RequestBody CareerAnalysisGenerateRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(analysisService.generate(principal.getId(), request));
    }

    @DeleteMapping("/analyses/{id}")
    public ApiResponse<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        analysisService.delete(principal.getId(), id);
        return ApiResponse.success(null);
    }

    @GetMapping("/analyses/{id}/comparison")
    public ApiResponse<CareerComparisonResponse> comparison(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(analysisService.comparison(principal.getId(), id));
    }

    @PostMapping("/insights/{insightId}/convert")
    public ApiResponse<CareerInsightConvertResponse> convertInsight(
            @PathVariable Long insightId,
            @Valid @RequestBody CareerInsightConvertRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(
                analysisService.convertInsight(principal.getId(), insightId, request)
        );
    }
}
