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
 * 平台教学资源附件：所有登录用户可查看已发布资源的附件，
 * 只有管理员或该资源的发布教师可以上传与解除关联。
 */
@RestController
@RequestMapping("/api/v1/resources/{resourceId}/attachments")
@RequiredArgsConstructor
public class ResourceAttachmentController {

    private final FileAttachmentService attachmentService;

    @GetMapping
    public ApiResponse<List<AttachmentResponse>> list(
            @PathVariable Long resourceId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(attachmentService.listResourceAttachments(resourceId, principal));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<AttachmentResponse> attach(
            @PathVariable Long resourceId,
            @Valid @RequestBody AttachmentCreateRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(
                attachmentService.attachToResource(resourceId, request, principal)
        );
    }

    @DeleteMapping("/{attachmentId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Void> detach(
            @PathVariable Long resourceId,
            @PathVariable Long attachmentId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        attachmentService.detachResourceAttachment(resourceId, attachmentId, principal);
        return ApiResponse.success(null);
    }
}
