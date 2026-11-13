package com.onlinecurlearnmanos.practice.dto;

import java.time.LocalDateTime;

public record PracticeMessageResponse(
        Long id,
        String role,
        String messageType,
        String content,
        Long questionId,
        LocalDateTime createdAt
) {
}
