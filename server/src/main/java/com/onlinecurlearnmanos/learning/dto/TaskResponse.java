package com.onlinecurlearnmanos.learning.dto;

import java.time.LocalDateTime;

public record TaskResponse(
        Long id,
        Long cycleId,
        Long goalId,
        String goalTitle,
        String title,
        String description,
        String category,
        LocalDateTime dueAt,
        String priority,
        String status,
        LocalDateTime completedAt
) {
}
