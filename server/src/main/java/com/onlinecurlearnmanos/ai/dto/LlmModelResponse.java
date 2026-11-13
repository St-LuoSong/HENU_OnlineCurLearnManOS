package com.onlinecurlearnmanos.ai.dto;

import java.time.LocalDateTime;

public record LlmModelResponse(
        Long id,
        Long providerId,
        String providerCode,
        String providerName,
        String modelCode,
        String displayName,
        String tier,
        int pointCost,
        boolean enabled,
        LocalDateTime updatedAt
) {
}
