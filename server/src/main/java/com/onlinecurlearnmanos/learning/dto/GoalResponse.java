package com.onlinecurlearnmanos.learning.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record GoalResponse(
        Long id,
        Long cycleId,
        Long subjectId,
        String subjectName,
        String title,
        String description,
        LocalDate targetDate,
        String priority,
        String status,
        LocalDateTime completedAt,
        long taskCount,
        long completedTaskCount,
        int progress
) {
}
