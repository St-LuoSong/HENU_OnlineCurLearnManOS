package com.onlinecurlearnmanos.practice.controller;

import com.onlinecurlearnmanos.common.api.ApiResponse;
import com.onlinecurlearnmanos.practice.dto.PracticeSetAnswersRequest;
import com.onlinecurlearnmanos.practice.dto.PracticeSetCreateRequest;
import com.onlinecurlearnmanos.practice.dto.PracticeSetDetailResponse;
import com.onlinecurlearnmanos.practice.dto.PracticeSetSummaryResponse;
import com.onlinecurlearnmanos.practice.service.PracticeSetService;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1/practice/sets")
@RequiredArgsConstructor
public class PracticeSetController {

    private final PracticeSetService practiceSetService;

    @GetMapping
    public ApiResponse<List<PracticeSetSummaryResponse>> list(
            @RequestParam(required = false) Long subjectId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(practiceSetService.list(principal.getId(), subjectId));
    }

    @PostMapping
    public ApiResponse<PracticeSetDetailResponse> create(
            @Valid @RequestBody PracticeSetCreateRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(practiceSetService.create(principal.getId(), request));
    }

    @GetMapping("/{id}")
    public ApiResponse<PracticeSetDetailResponse> get(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(practiceSetService.get(principal.getId(), id));
    }

    @PutMapping("/{id}/answers")
    public ApiResponse<PracticeSetDetailResponse> saveAnswers(
            @PathVariable Long id,
            @Valid @RequestBody PracticeSetAnswersRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(
                practiceSetService.saveAnswers(principal.getId(), id, request)
        );
    }

    @PostMapping("/{id}/submit")
    public ApiResponse<PracticeSetDetailResponse> submit(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(practiceSetService.submit(principal.getId(), id));
    }
}
