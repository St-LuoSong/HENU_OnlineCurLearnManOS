package com.onlinecurlearnmanos.practice.dto;

import java.time.LocalDateTime;

public record PracticeAnswerResponse(
        Long id,
        Long questionId,
        boolean transfer,
        boolean correct,
        String grade,
        String errorType,
        String errorLabel,
        String feedback,
        LocalDateTime answeredAt
) {
}
