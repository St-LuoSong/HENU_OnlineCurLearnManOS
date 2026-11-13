package com.onlinecurlearnmanos.point.dto;

import java.time.LocalDateTime;

public record PointTransactionResponse(
        Long id,
        String type,
        int amount,
        String direction,
        int balanceAfter,
        String status,
        String referenceType,
        String referenceId,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime completedAt
) {
}
