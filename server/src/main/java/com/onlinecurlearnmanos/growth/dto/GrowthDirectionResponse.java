package com.onlinecurlearnmanos.growth.dto;

import java.time.LocalDateTime;

public record GrowthDirectionResponse(
        Long cycleId,
        Long sourceAnalysisId,
        String summary,
        String snapshotJson,
        LocalDateTime updatedAt
) {
}
