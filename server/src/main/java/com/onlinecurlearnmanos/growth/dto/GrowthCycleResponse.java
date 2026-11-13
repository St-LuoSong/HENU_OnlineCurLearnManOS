package com.onlinecurlearnmanos.growth.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record GrowthCycleResponse(
        Long id,
        String title,
        LocalDate startDate,
        LocalDate endDate,
        String status,
        LocalDateTime completedAt,
        LocalDateTime archivedAt,
        LocalDateTime createdAt
) {
}
