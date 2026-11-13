package com.onlinecurlearnmanos.ai.dto;

import java.time.LocalDateTime;

public record LlmProviderResponse(
        Long id,
        String code,
        String adapterCode,
        String name,
        String baseUrl,
        String defaultModel,
        boolean enabled,
        boolean apiKeyConfigured,
        LocalDateTime updatedAt,
        long callsToday,
        long failuresToday,
        String lastErrorMessage,
        long affectedUserCount
) {
}
