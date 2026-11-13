package com.onlinecurlearnmanos.ai.controller;

import com.onlinecurlearnmanos.ai.dto.LlmProviderResponse;
import com.onlinecurlearnmanos.ai.dto.LlmProviderTestRequest;
import com.onlinecurlearnmanos.ai.dto.LlmProviderTestResponse;
import com.onlinecurlearnmanos.ai.dto.LlmProviderUpdateRequest;
import com.onlinecurlearnmanos.ai.service.LlmProviderService;
import com.onlinecurlearnmanos.common.api.ApiResponse;
import com.onlinecurlearnmanos.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/llm")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminLlmController {

    private final LlmProviderService providerService;

    @GetMapping("/providers")
    public ApiResponse<List<LlmProviderResponse>> listProviders() {
        return ApiResponse.success(providerService.listProviders());
    }

    @PutMapping("/providers/{id}")
    public ApiResponse<LlmProviderResponse> updateProvider(
            @PathVariable Long id,
            @Valid @RequestBody LlmProviderUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(
                providerService.updateProvider(id, request, principal.getId())
        );
    }

    @PostMapping("/providers/{id}/test")
    public ApiResponse<LlmProviderTestResponse> testProvider(
            @PathVariable Long id,
            @Valid @RequestBody LlmProviderTestRequest request
    ) {
        return ApiResponse.success(providerService.testProvider(id, request));
    }
}
