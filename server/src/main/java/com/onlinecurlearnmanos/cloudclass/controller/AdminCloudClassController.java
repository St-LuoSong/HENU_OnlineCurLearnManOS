package com.onlinecurlearnmanos.cloudclass.controller;

import com.onlinecurlearnmanos.cloudclass.dto.CloudClassResponse;
import com.onlinecurlearnmanos.cloudclass.dto.CloudClassStatusRequest;
import com.onlinecurlearnmanos.cloudclass.service.CloudClassService;
import com.onlinecurlearnmanos.common.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/cloud-classes")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCloudClassController {

    private final CloudClassService cloudClassService;

    @GetMapping
    public ApiResponse<List<CloudClassResponse>> list(
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) Long teacherId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String semester,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.success(
                cloudClassService.adminList(subjectId, teacherId, status, semester, keyword)
        );
    }

    @PutMapping("/{id}/status")
    public ApiResponse<CloudClassResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody CloudClassStatusRequest request
    ) {
        return ApiResponse.success(cloudClassService.adminUpdateStatus(id, request));
    }
}
