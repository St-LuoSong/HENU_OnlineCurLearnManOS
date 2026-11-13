package com.onlinecurlearnmanos.learning.controller;

import com.onlinecurlearnmanos.common.api.ApiResponse;
import com.onlinecurlearnmanos.learning.dto.LearningRecordResponse;
import com.onlinecurlearnmanos.learning.dto.LearningRecordSaveRequest;
import com.onlinecurlearnmanos.learning.service.LearningRecordService;
import com.onlinecurlearnmanos.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/learning-records")
@RequiredArgsConstructor
public class LearningRecordController {

    private final LearningRecordService recordService;

    @GetMapping
    public ApiResponse<List<LearningRecordResponse>> list(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime to,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(recordService.list(principal.getId(), from, to));
    }

    @PostMapping
    public ApiResponse<LearningRecordResponse> create(
            @Valid @RequestBody LearningRecordSaveRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(recordService.create(principal.getId(), request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        recordService.delete(id, principal.getId());
        return ApiResponse.success(null);
    }
}
