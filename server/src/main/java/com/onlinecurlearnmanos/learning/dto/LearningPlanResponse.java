package com.onlinecurlearnmanos.learning.dto;

import java.time.LocalDateTime;
import java.util.List;

public record LearningPlanResponse(
        Long id,
        Long cycleId,
        String title,
        String summary,
        String status,
        String providerCode,
        String model,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        int totalItemCount,
        int completedItemCount,
        int progress,
        List<LearningPlanItemResponse> items
) {
}
