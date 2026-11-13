package com.onlinecurlearnmanos.learning.dto;

import java.time.LocalDate;

public record LearningPlanItemResponse(
        Long id,
        String title,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        Long resourceId,
        Long knowledgePointId,
        Long goalId,
        Long taskId,
        String status,
        int sortOrder
) {
}
