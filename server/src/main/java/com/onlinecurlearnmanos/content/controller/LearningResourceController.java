package com.onlinecurlearnmanos.content.controller;

import com.onlinecurlearnmanos.common.api.ApiResponse;
import com.onlinecurlearnmanos.common.api.PageResult;
import com.onlinecurlearnmanos.content.dto.LearningResourceResponse;
import com.onlinecurlearnmanos.content.dto.LearningResourceSaveRequest;
import com.onlinecurlearnmanos.content.dto.LearningResourceStatusRequest;
import com.onlinecurlearnmanos.content.service.LearningResourceService;
import com.onlinecurlearnmanos.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/resources")
@RequiredArgsConstructor
public class LearningResourceController {

    private final LearningResourceService resourceService;

    @GetMapping
    public ApiResponse<PageResult<LearningResourceResponse>> list(
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) Long knowledgePointId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "12") long size,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(resourceService.list(
                subjectId,
                knowledgePointId,
                keyword,
                page,
                size,
                principal.getRoles().contains("ADMIN")
        ));
    }

    @GetMapping("/{id}")
    public ApiResponse<LearningResourceResponse> get(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(
                resourceService.get(id, principal.getRoles().contains("ADMIN"))
        );
    }

    @PostMapping("/{id}/view")
    public ApiResponse<Void> recordView(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        resourceService.recordView(principal.getId(), id);
        return ApiResponse.success(null);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<LearningResourceResponse> create(
            @Valid @RequestBody LearningResourceSaveRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(resourceService.create(
                request,
                principal.getId(),
                principal.getId()
        ));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<LearningResourceResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody LearningResourceSaveRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(resourceService.update(id, request, principal.getId()));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<LearningResourceResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody LearningResourceStatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(
                resourceService.updateStatus(id, request.status(), principal.getId())
        );
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        resourceService.delete(id, principal.getId());
        return ApiResponse.success(null);
    }
}
