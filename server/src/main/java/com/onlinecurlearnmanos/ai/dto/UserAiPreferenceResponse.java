package com.onlinecurlearnmanos.ai.dto;

import java.util.List;

public record UserAiPreferenceResponse(
        Long selectedModelId,
        LlmModelResponse selectedModel,
        List<LlmModelResponse> availableModels
) {
}
