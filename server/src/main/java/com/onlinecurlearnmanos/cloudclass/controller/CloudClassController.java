package com.onlinecurlearnmanos.cloudclass.controller;

import com.onlinecurlearnmanos.cloudclass.dto.CloudClassCreateRequest;
import com.onlinecurlearnmanos.cloudclass.dto.CloudClassDetailResponse;
import com.onlinecurlearnmanos.cloudclass.dto.CloudClassGradeRequest;
import com.onlinecurlearnmanos.cloudclass.dto.CloudClassJoinRequest;
import com.onlinecurlearnmanos.cloudclass.dto.CloudClassJoinEnabledRequest;
import com.onlinecurlearnmanos.cloudclass.dto.CloudClassMemberResponse;
import com.onlinecurlearnmanos.cloudclass.dto.CloudClassMemberStatusRequest;
import com.onlinecurlearnmanos.cloudclass.dto.CloudClassResourceRequest;
import com.onlinecurlearnmanos.cloudclass.dto.CloudClassResourceResponse;
import com.onlinecurlearnmanos.cloudclass.dto.CloudClassResponse;
import com.onlinecurlearnmanos.cloudclass.dto.CloudClassRosterResponse;
import com.onlinecurlearnmanos.cloudclass.dto.CloudClassStatisticsResponse;
import com.onlinecurlearnmanos.cloudclass.dto.CloudClassStatusRequest;
import com.onlinecurlearnmanos.cloudclass.dto.CloudClassSubmissionRequest;
import com.onlinecurlearnmanos.cloudclass.dto.CloudClassSubmissionResponse;
import com.onlinecurlearnmanos.cloudclass.dto.CloudClassTaskRequest;
import com.onlinecurlearnmanos.cloudclass.dto.CloudClassTaskResponse;
import com.onlinecurlearnmanos.cloudclass.dto.CloudClassTaskStatusRequest;
import com.onlinecurlearnmanos.cloudclass.dto.TeacherStudentResponse;
import com.onlinecurlearnmanos.cloudclass.service.CloudClassService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cloud-classes")
@RequiredArgsConstructor
public class CloudClassController {

    private final CloudClassService cloudClassService;

    @PostMapping
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<CloudClassResponse> create(
            @Valid @RequestBody CloudClassCreateRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(cloudClassService.create(principal.getId(), request));
    }

    @PostMapping("/join")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<CloudClassResponse> join(
            @Valid @RequestBody CloudClassJoinRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(cloudClassService.join(principal.getId(), request));
    }

    @GetMapping("/preview")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<CloudClassResponse> preview(
            @RequestParam String classCode
    ) {
        return ApiResponse.success(cloudClassService.preview(classCode));
    }

    @GetMapping("/teaching")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<List<CloudClassResponse>> listTeaching(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(cloudClassService.listTeaching(principal.getId()));
    }

    @GetMapping("/learning")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<List<CloudClassResponse>> listLearning(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(cloudClassService.listLearning(principal.getId()));
    }

    @GetMapping("/teaching/students")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<List<TeacherStudentResponse>> teacherStudents(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(cloudClassService.teacherStudents(principal.getId()));
    }

    @PutMapping("/{id}/members/{studentId}/status")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<CloudClassMemberResponse> updateMemberStatus(
            @PathVariable Long id,
            @PathVariable Long studentId,
            @Valid @RequestBody CloudClassMemberStatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(
                cloudClassService.updateMemberStatus(
                        principal.getId(),
                        id,
                        studentId,
                        request
                )
        );
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<CloudClassResponse> updateClassStatus(
            @PathVariable Long id,
            @Valid @RequestBody CloudClassStatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(
                cloudClassService.updateClassStatus(principal.getId(), id, request)
        );
    }

    @PutMapping("/{id}/join-enabled")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<CloudClassResponse> updateJoinEnabled(
            @PathVariable Long id,
            @Valid @RequestBody CloudClassJoinEnabledRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(
                cloudClassService.updateJoinEnabled(
                        principal.getId(),
                        id,
                        Boolean.TRUE.equals(request.enabled())
                )
        );
    }

    @PutMapping("/tasks/{taskId}/status")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<CloudClassTaskResponse> updateTaskStatus(
            @PathVariable Long taskId,
            @Valid @RequestBody CloudClassTaskStatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(
                cloudClassService.updateTaskStatus(principal.getId(), taskId, request)
        );
    }

    @GetMapping("/{id}")
    public ApiResponse<CloudClassDetailResponse> detail(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(
                cloudClassService.detail(principal.getId(), principal.getRoles(), id)
        );
    }

    @GetMapping("/{id}/roster")
    public ApiResponse<CloudClassRosterResponse> roster(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "50") long size,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(
                cloudClassService.roster(
                        principal.getId(),
                        principal.getRoles(),
                        id,
                        page,
                        size
                )
        );
    }

    @PostMapping("/{id}/resources")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<CloudClassResourceResponse> publishResource(
            @PathVariable Long id,
            @Valid @RequestBody CloudClassResourceRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(
                cloudClassService.publishResource(principal.getId(), id, request)
        );
    }

    @PostMapping("/{id}/tasks")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<CloudClassTaskResponse> publishTask(
            @PathVariable Long id,
            @Valid @RequestBody CloudClassTaskRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(
                cloudClassService.publishTask(principal.getId(), id, request)
        );
    }

    @PostMapping("/tasks/{taskId}/submit")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<CloudClassSubmissionResponse> submit(
            @PathVariable Long taskId,
            @Valid @RequestBody CloudClassSubmissionRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(
                cloudClassService.submit(principal.getId(), taskId, request)
        );
    }

    @GetMapping("/tasks/{taskId}/submissions")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<List<CloudClassSubmissionResponse>> submissions(
            @PathVariable Long taskId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(
                cloudClassService.submissions(principal.getId(), taskId)
        );
    }

    @PutMapping("/submissions/{id}/grade")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<CloudClassSubmissionResponse> grade(
            @PathVariable Long id,
            @Valid @RequestBody CloudClassGradeRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(
                cloudClassService.grade(principal.getId(), id, request)
        );
    }

    @GetMapping("/{id}/statistics")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<CloudClassStatisticsResponse> statistics(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(
                cloudClassService.statistics(principal.getId(), id)
        );
    }
}
