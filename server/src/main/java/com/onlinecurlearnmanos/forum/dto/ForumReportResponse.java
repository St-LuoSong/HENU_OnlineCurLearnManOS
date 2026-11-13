package com.onlinecurlearnmanos.forum.dto;

import java.time.LocalDateTime;

public record ForumReportResponse(
        Long id,
        String targetType,
        Long targetId,
        String targetSummary,
        Long reporterId,
        String reporterName,
        String reason,
        String detail,
        String status,
        Long handlerId,
        String handlerName,
        String handleAction,
        String handleNote,
        LocalDateTime handledAt,
        LocalDateTime createdAt
) {
}
