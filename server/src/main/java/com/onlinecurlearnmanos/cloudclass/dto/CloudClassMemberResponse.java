package com.onlinecurlearnmanos.cloudclass.dto;

import java.time.LocalDateTime;

public record CloudClassMemberResponse(
        Long studentId,
        String loginId,
        String displayName,
        String status,
        long totalTaskCount,
        long submittedTaskCount,
        long pendingTaskCount,
        long overdueTaskCount,
        long gradedCount,
        double averageScore,
        LocalDateTime joinedAt
) {
}
