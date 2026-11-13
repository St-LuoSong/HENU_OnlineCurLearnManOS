package com.onlinecurlearnmanos.ai.dto;

import jakarta.validation.constraints.NotNull;

public record UserAiPreferenceUpdateRequest(
        @NotNull(message = "请选择模型")
        Long modelId
) {
}
