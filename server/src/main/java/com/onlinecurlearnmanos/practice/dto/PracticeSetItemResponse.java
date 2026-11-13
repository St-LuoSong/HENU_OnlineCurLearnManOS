package com.onlinecurlearnmanos.practice.dto;

import java.util.List;

public record PracticeSetItemResponse(
        Long id,
        int seq,
        String questionType,
        String stem,
        List<String> options,
        String studentAnswer,
        Boolean correct,
        Integer awardedScore,
        int maxScore,
        String feedback,
        String errorType,
        String correctAnswer,
        String explanation
) {
}
