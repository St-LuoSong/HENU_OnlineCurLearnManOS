package com.onlinecurlearnmanos.ai.dto;

public record LlmProviderTestResponse(
        boolean success,
        String providerCode,
        String model,
        String content
) {
}
