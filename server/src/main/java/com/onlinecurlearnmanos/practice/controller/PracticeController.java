package com.onlinecurlearnmanos.practice.controller;

import com.onlinecurlearnmanos.common.api.ApiResponse;
import com.onlinecurlearnmanos.practice.dto.PracticeMessageRequest;
import com.onlinecurlearnmanos.practice.dto.PracticeSessionCreateRequest;
import com.onlinecurlearnmanos.practice.dto.PracticeSessionDetailResponse;
import com.onlinecurlearnmanos.practice.dto.PracticeSessionResponse;
import com.onlinecurlearnmanos.practice.dto.PracticeTurnResponse;
import com.onlinecurlearnmanos.practice.service.PracticeService;
import com.onlinecurlearnmanos.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/practice")
@RequiredArgsConstructor
public class PracticeController {

    private final PracticeService practiceService;

    @GetMapping("/sessions")
    public ApiResponse<List<PracticeSessionResponse>> list(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(practiceService.list(principal.getId()));
    }

    @PostMapping("/sessions")
    public ApiResponse<PracticeSessionDetailResponse> create(
            @Valid @RequestBody PracticeSessionCreateRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(practiceService.create(principal.getId(), request));
    }

    @GetMapping("/sessions/{id}")
    public ApiResponse<PracticeSessionDetailResponse> detail(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(practiceService.detail(principal.getId(), id));
    }

    @PostMapping("/sessions/{id}/messages")
    public ApiResponse<PracticeTurnResponse> sendMessage(
            @PathVariable Long id,
            @Valid @RequestBody PracticeMessageRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(practiceService.sendMessage(principal.getId(), id, request));
    }

    @PostMapping("/sessions/{id}/questions")
    public ApiResponse<PracticeTurnResponse> startQuestion(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(practiceService.startQuestion(principal.getId(), id));
    }

    @PostMapping("/sessions/{id}/hint")
    public ApiResponse<PracticeTurnResponse> requestHint(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(practiceService.requestHint(principal.getId(), id));
    }

    @DeleteMapping("/sessions/{id}")
    public ApiResponse<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        practiceService.delete(principal.getId(), id);
        return ApiResponse.success(null);
    }
}
