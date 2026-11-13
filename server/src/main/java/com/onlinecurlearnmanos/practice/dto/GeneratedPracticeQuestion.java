package com.onlinecurlearnmanos.practice.dto;

import java.util.List;

public record GeneratedPracticeQuestion(
        String type,
        String stem,
        List<String> options,
        String correctAnswer,
        String explanation,
        Integer maxScore
) {
}
