package com.onlinecurlearnmanos.learning.controller;

import com.onlinecurlearnmanos.common.api.ApiResponse;
import com.onlinecurlearnmanos.learning.dto.ProgressResponse;
import com.onlinecurlearnmanos.learning.dto.ProgressUpdateRequest;
import com.onlinecurlearnmanos.learning.service.ProgressService;
import com.onlinecurlearnmanos.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/progress")
@RequiredArgsConstructor
public class ProgressController {

    private final ProgressService progressService;

    @GetMapping
    public ApiResponse<List<ProgressResponse>> list(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(progressService.list(principal.getId()));
    }

    @PutMapping
    public ApiResponse<ProgressResponse> upsert(
            @Valid @RequestBody ProgressUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(progressService.upsert(principal.getId(), request));
    }
}
