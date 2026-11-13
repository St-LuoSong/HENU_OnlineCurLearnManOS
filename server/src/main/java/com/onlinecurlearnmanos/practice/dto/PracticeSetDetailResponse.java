package com.onlinecurlearnmanos.practice.dto;

import java.time.LocalDateTime;
import java.util.List;

public record PracticeSetDetailResponse(
        Long id,
        String title,
        Long subjectId,
        String subjectName,
        Long knowledgePointId,
        String knowledgePointName,
        String billingMode,
        String status,
        int questionCount,
        int answeredCount,
        int correctCount,
        Integer score,
        Integer durationSeconds,
        LocalDateTime startedAt,
        LocalDateTime submittedAt,
        List<PracticeSetItemResponse> items
) {
}
