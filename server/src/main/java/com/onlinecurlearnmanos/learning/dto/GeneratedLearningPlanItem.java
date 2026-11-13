package com.onlinecurlearnmanos.learning.dto;

import java.time.LocalDate;

public record GeneratedLearningPlanItem(
        String title,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        Long resourceId,
        Long knowledgePointId,
        Long taskId
) {
}
