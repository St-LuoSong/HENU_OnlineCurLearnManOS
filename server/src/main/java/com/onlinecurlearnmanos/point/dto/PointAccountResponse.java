package com.onlinecurlearnmanos.point.dto;

import java.time.LocalDateTime;

public record PointAccountResponse(
        Long id,
        Long userId,
        int balance,
        int totalEarned,
        int totalSpent,
        LocalDateTime updatedAt
) {
}
