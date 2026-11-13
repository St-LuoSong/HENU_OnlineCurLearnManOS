package com.onlinecurlearnmanos.admin.controller;

import com.onlinecurlearnmanos.admin.dto.AdminAuditLogResponse;
import com.onlinecurlearnmanos.admin.service.AdminAuditService;
import com.onlinecurlearnmanos.common.api.ApiResponse;
import com.onlinecurlearnmanos.common.api.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminAuditLogController {

    private final AdminAuditService auditService;

    @GetMapping
    public ApiResponse<PageResult<AdminAuditLogResponse>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size
    ) {
        return ApiResponse.success(auditService.list(page, size));
    }
}
