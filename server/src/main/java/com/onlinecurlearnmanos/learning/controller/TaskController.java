package com.onlinecurlearnmanos.learning.controller;

import com.onlinecurlearnmanos.common.api.ApiResponse;
import com.onlinecurlearnmanos.learning.dto.TaskResponse;
import com.onlinecurlearnmanos.learning.dto.TaskSaveRequest;
import com.onlinecurlearnmanos.learning.service.TaskService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @GetMapping
    public ApiResponse<List<TaskResponse>> list(
            @RequestParam(required = false) Long goalId,
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(taskService.list(principal.getId(), goalId, status));
    }

    @PostMapping
    public ApiResponse<TaskResponse> create(
            @Valid @RequestBody TaskSaveRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(taskService.create(principal.getId(), request));
    }

    @PutMapping("/{id}")
    public ApiResponse<TaskResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody TaskSaveRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(taskService.update(id, principal.getId(), request));
    }

    @PostMapping("/{id}/complete")
    public ApiResponse<TaskResponse> complete(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(taskService.complete(id, principal.getId()));
    }

    @PostMapping("/{id}/reopen")
    public ApiResponse<TaskResponse> reopen(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(taskService.reopen(id, principal.getId()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        taskService.delete(id, principal.getId());
        return ApiResponse.success(null);
    }
}
