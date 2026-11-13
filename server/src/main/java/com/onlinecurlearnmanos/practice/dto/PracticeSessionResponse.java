package com.onlinecurlearnmanos.practice.dto;

import java.time.LocalDateTime;

public record PracticeSessionResponse(
        Long id,
        Long subjectId,
        String subjectName,
        Long knowledgePointId,
        String knowledgePointName,
        String title,
        String state,
        String currentGrade,
        int consecutiveFailures,
        Long currentQuestionId,
        boolean transferQuestion,
        long messageCount,
        LocalDateTime updatedAt
) {
}
