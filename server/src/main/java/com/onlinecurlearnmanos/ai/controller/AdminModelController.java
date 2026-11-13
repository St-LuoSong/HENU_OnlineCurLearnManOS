package com.onlinecurlearnmanos.ai.controller;

import com.onlinecurlearnmanos.ai.dto.LlmModelResponse;
import com.onlinecurlearnmanos.ai.dto.LlmModelUpdateRequest;
import com.onlinecurlearnmanos.ai.service.LlmModelService;
import com.onlinecurlearnmanos.common.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/llm/models")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminModelController {

    private final LlmModelService modelService;

    @GetMapping
    public ApiResponse<List<LlmModelResponse>> list() {
        return ApiResponse.success(modelService.listAll());
    }

    @PutMapping("/{id}")
    public ApiResponse<LlmModelResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody LlmModelUpdateRequest request
    ) {
        return ApiResponse.success(modelService.update(id, request));
    }
}
