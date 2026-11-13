package com.onlinecurlearnmanos.growth.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record GrowthReviewResponse(
        Long id,
        Long cycleId,
        LocalDate weekStart,
        LocalDate weekEnd,
        String evidenceJson,
        String note,
        LocalDateTime updatedAt
) {
}
