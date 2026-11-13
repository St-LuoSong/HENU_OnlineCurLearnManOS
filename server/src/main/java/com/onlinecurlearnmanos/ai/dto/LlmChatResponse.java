package com.onlinecurlearnmanos.ai.dto;

public record LlmChatResponse(
        String content,
        String model,
        String providerCode,
        LlmUsage usage,
        String requestId
) {
}
