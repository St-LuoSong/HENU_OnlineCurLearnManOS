package com.onlinecurlearnmanos.cloudclass.dto;

import java.time.LocalDateTime;

public record CloudClassTaskResponse(
        Long id,
        String title,
        String description,
        String taskType,
        Long knowledgePointId,
        String knowledgePointName,
        LocalDateTime dueAt,
        int rewardPoints,
        boolean allowResubmit,
        boolean allowLateSubmit,
        Double maxScore,
        String status,
        long submissionCount,
        Long mySubmissionId,
        String mySubmissionStatus,
        Double myScore
) {
}
