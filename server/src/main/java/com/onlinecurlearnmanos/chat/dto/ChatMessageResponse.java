package com.onlinecurlearnmanos.chat.dto;

import java.time.LocalDateTime;

public record ChatMessageResponse(
        Long id,
        String role,
        String content,
        String providerCode,
        String model,
        Integer totalTokens,
        LocalDateTime createdAt
) {
}
