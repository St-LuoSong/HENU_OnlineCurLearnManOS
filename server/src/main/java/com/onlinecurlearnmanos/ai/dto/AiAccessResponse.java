package com.onlinecurlearnmanos.ai.dto;

public record AiAccessResponse(
        int standardUsed,
        int standardLimit,
        int standardRemaining,
        int expertUsed,
        int expertLimit,
        int expertRemaining
) {
}
