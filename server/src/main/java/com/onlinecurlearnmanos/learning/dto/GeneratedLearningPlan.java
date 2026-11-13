package com.onlinecurlearnmanos.learning.dto;

import java.util.List;

public record GeneratedLearningPlan(
        String title,
        String summary,
        List<GeneratedLearningPlanItem> items
) {
}
