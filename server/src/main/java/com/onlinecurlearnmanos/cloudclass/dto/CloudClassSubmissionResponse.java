package com.onlinecurlearnmanos.cloudclass.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CloudClassSubmissionResponse(
        Long id,
        Long taskId,
        String taskTitle,
        Long studentId,
        String studentName,
        String content,
        BigDecimal score,
        String feedback,
        String status,
        LocalDateTime submittedAt,
        LocalDateTime gradedAt
) {
}
