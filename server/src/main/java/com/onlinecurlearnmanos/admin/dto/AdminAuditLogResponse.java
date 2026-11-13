package com.onlinecurlearnmanos.admin.dto;

import java.time.LocalDateTime;

public record AdminAuditLogResponse(
        Long id,
        Long operatorId,
        String operatorName,
        String action,
        String targetType,
        String targetId,
        String detail,
        LocalDateTime createdAt
) {
}
