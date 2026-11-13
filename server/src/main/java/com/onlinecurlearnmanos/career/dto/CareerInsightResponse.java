package com.onlinecurlearnmanos.career.dto;

import java.time.LocalDate;

public record CareerInsightResponse(
        Long id,
        String category,
        String title,
        String content,
        String reason,
        LocalDate targetDate,
        Long convertedGoalId,
        Long convertedTaskId,
        Integer score,
        int sortOrder
) {
}
