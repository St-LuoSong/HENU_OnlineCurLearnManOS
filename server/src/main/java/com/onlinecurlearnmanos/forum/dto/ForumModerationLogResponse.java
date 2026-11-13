package com.onlinecurlearnmanos.forum.dto;

import java.time.LocalDateTime;

public record ForumModerationLogResponse(
        Long id,
        String targetType,
        Long targetId,
        Long reportId,
        Long operatorId,
        String operatorName,
        String action,
        String reason,
        String beforeSummary,
        String afterSummary,
        boolean patrol,
        LocalDateTime createdAt
) {
}
