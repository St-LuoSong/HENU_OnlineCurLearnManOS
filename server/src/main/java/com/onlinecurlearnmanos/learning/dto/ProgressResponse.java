package com.onlinecurlearnmanos.learning.dto;

import java.time.LocalDateTime;

public record ProgressResponse(
        Long id,
        Long subjectId,
        String subjectName,
        Long knowledgePointId,
        String knowledgePointName,
        String currentGrade,
        int attemptCount,
        int correctCount,
        boolean mastered,
        boolean transferPassed,
        LocalDateTime lastActivity
) {
}
