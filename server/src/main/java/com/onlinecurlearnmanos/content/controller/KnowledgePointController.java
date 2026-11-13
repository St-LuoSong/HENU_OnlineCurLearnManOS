package com.onlinecurlearnmanos.content.controller;

import com.onlinecurlearnmanos.common.api.ApiResponse;
import com.onlinecurlearnmanos.content.dto.KnowledgePointResponse;
import com.onlinecurlearnmanos.content.dto.KnowledgePointSaveRequest;
import com.onlinecurlearnmanos.content.service.KnowledgePointService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/v1/knowledge-points")
@RequiredArgsConstructor
public class KnowledgePointController {

    private final KnowledgePointService knowledgePointService;

    @GetMapping
    public ApiResponse<List<KnowledgePointResponse>> list(
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.success(knowledgePointService.list(subjectId, keyword));
    }

    @GetMapping("/{id}")
    public ApiResponse<KnowledgePointResponse> get(@PathVariable Long id) {
        return ApiResponse.success(knowledgePointService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ApiResponse<KnowledgePointResponse> create(
            @Valid @RequestBody KnowledgePointSaveRequest request
    ) {
        return ApiResponse.success(knowledgePointService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ApiResponse<KnowledgePointResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody KnowledgePointSaveRequest request
    ) {
        return ApiResponse.success(knowledgePointService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        knowledgePointService.delete(id);
        return ApiResponse.success(null);
    }
}
