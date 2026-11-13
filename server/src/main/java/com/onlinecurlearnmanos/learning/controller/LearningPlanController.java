package com.onlinecurlearnmanos.learning.controller;

import com.onlinecurlearnmanos.common.api.ApiResponse;
import com.onlinecurlearnmanos.learning.dto.LearningPlanGenerateRequest;
import com.onlinecurlearnmanos.learning.dto.LearningPlanItemConvertRequest;
import com.onlinecurlearnmanos.learning.dto.LearningPlanItemMoveRequest;
import com.onlinecurlearnmanos.learning.dto.LearningPlanItemUpdateRequest;
import com.onlinecurlearnmanos.learning.dto.LearningPlanResponse;
import com.onlinecurlearnmanos.learning.dto.LearningPlanStatusRequest;
import com.onlinecurlearnmanos.learning.service.LearningPlanService;
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
@RequestMapping("/api/v1/learning-plans")
@RequiredArgsConstructor
public class LearningPlanController {

    private final LearningPlanService learningPlanService;

    @GetMapping
    public ApiResponse<List<LearningPlanResponse>> list(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(learningPlanService.list(principal.getId()));
    }

    @GetMapping("/{id}")
    public ApiResponse<LearningPlanResponse> get(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(learningPlanService.get(principal.getId(), id));
    }

    @PostMapping("/generate")
    public ApiResponse<LearningPlanResponse> generate(
            @Valid @RequestBody LearningPlanGenerateRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(
                learningPlanService.generate(principal.getId(), request)
        );
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        learningPlanService.delete(principal.getId(), id);
        return ApiResponse.success(null);
    }

    @PutMapping("/{id}/status")
    public ApiResponse<LearningPlanResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody LearningPlanStatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(
                learningPlanService.updateStatus(principal.getId(), id, request.status())
        );
    }

    @PostMapping("/{id}/activate")
    public ApiResponse<LearningPlanResponse> activate(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(learningPlanService.activate(principal.getId(), id));
    }

    @PutMapping("/{planId}/items/{itemId}/status")
    public ApiResponse<LearningPlanResponse> updateItemStatus(
            @PathVariable Long planId,
            @PathVariable Long itemId,
            @Valid @RequestBody LearningPlanStatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(
                learningPlanService.updateItemStatus(
                        principal.getId(),
                        planId,
                        itemId,
                        request.status()
                )
        );
    }

    @PutMapping("/{planId}/items/{itemId}")
    public ApiResponse<LearningPlanResponse> updateItem(
            @PathVariable Long planId,
            @PathVariable Long itemId,
            @Valid @RequestBody LearningPlanItemUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(
                learningPlanService.updateItem(principal.getId(), planId, itemId, request)
        );
    }

    @PostMapping("/{planId}/items/{itemId}/convert")
    public ApiResponse<LearningPlanResponse> convertItem(
            @PathVariable Long planId,
            @PathVariable Long itemId,
            @Valid @RequestBody LearningPlanItemConvertRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(
                learningPlanService.convertItem(principal.getId(), planId, itemId, request)
        );
    }

    @PutMapping("/{planId}/items/{itemId}/order")
    public ApiResponse<LearningPlanResponse> moveItem(
            @PathVariable Long planId,
            @PathVariable Long itemId,
            @Valid @RequestBody LearningPlanItemMoveRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(
                learningPlanService.moveItem(principal.getId(), planId, itemId, request)
        );
    }
}
