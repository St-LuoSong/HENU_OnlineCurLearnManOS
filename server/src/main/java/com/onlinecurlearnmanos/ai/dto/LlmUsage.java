package com.onlinecurlearnmanos.ai.dto;

public record LlmUsage(
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens
) {
}
