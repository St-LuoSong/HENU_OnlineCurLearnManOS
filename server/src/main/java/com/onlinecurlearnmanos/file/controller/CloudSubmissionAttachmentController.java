package com.onlinecurlearnmanos.file.controller;

import com.onlinecurlearnmanos.common.api.ApiResponse;
import com.onlinecurlearnmanos.file.dto.AttachmentCreateRequest;
import com.onlinecurlearnmanos.file.dto.AttachmentResponse;
import com.onlinecurlearnmanos.file.service.FileAttachmentService;
import com.onlinecurlearnmanos.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 学生作业附件：提交者本人可上传，班级教师与提交者本人（以及管理员）可查看与删除。
 * 上传校验完全复用云班级的成员、任务与重交策略。
 */
@RestController
@RequestMapping("/api/v1/cloud-classes/submissions/{submissionId}/attachments")
@RequiredArgsConstructor
public class CloudSubmissionAttachmentController {

    private final FileAttachmentService attachmentService;

    @GetMapping
    public ApiResponse<List<AttachmentResponse>> list(
            @PathVariable Long submissionId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(
                attachmentService.listSubmissionAttachments(submissionId, principal)
        );
    }

    @PostMapping
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<AttachmentResponse> attach(
            @PathVariable Long submissionId,
            @Valid @RequestBody AttachmentCreateRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(
                attachmentService.attachToSubmission(submissionId, request, principal)
        );
    }

    @DeleteMapping("/{attachmentId}")
    public ApiResponse<Void> detach(
            @PathVariable Long submissionId,
            @PathVariable Long attachmentId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        attachmentService.detachSubmissionAttachment(submissionId, attachmentId, principal);
        return ApiResponse.success(null);
    }
}
