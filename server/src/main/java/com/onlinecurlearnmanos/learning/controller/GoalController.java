package com.onlinecurlearnmanos.learning.controller;

import com.onlinecurlearnmanos.common.api.ApiResponse;
import com.onlinecurlearnmanos.learning.dto.GoalResponse;
import com.onlinecurlearnmanos.learning.dto.GoalSaveRequest;
import com.onlinecurlearnmanos.learning.dto.GoalGenerateRequest;
import com.onlinecurlearnmanos.learning.dto.GoalSuggestionResponse;
import com.onlinecurlearnmanos.learning.service.GoalService;
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
@RequestMapping("/api/v1/goals")
@RequiredArgsConstructor
public class GoalController {

    private final GoalService goalService;

    @GetMapping
    public ApiResponse<List<GoalResponse>> list(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(goalService.list(principal.getId()));
    }

    @PostMapping
    public ApiResponse<GoalResponse> create(
            @Valid @RequestBody GoalSaveRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(goalService.create(principal.getId(), request));
    }

    @PutMapping("/{id}")
    public ApiResponse<GoalResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody GoalSaveRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(goalService.update(id, principal.getId(), request));
    }

    @PostMapping("/{id}/complete")
    public ApiResponse<GoalResponse> complete(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(goalService.complete(id, principal.getId()));
    }

    @PostMapping("/generate")
    public ApiResponse<List<GoalSuggestionResponse>> generate(
            @Valid @RequestBody GoalGenerateRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(goalService.generate(principal.getId(), request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        goalService.delete(id, principal.getId());
        return ApiResponse.success(null);
    }
}
