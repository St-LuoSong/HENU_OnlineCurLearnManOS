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
 * 云班级教学资源附件：班级教师上传，班级教师与在班学生可查看。
 */
@RestController
@RequestMapping("/api/v1/cloud-classes/class-resources/{classResourceId}/attachments")
@RequiredArgsConstructor
public class CloudClassResourceAttachmentController {

    private final FileAttachmentService attachmentService;

    @GetMapping
    public ApiResponse<List<AttachmentResponse>> list(
            @PathVariable Long classResourceId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(
                attachmentService.listClassResourceAttachments(classResourceId, principal)
        );
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<AttachmentResponse> attach(
            @PathVariable Long classResourceId,
            @Valid @RequestBody AttachmentCreateRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(
                attachmentService.attachToClassResource(classResourceId, request, principal)
        );
    }

    @DeleteMapping("/{attachmentId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Void> detach(
            @PathVariable Long classResourceId,
            @PathVariable Long attachmentId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        attachmentService.detachClassResourceAttachment(classResourceId, attachmentId, principal);
        return ApiResponse.success(null);
    }
}
