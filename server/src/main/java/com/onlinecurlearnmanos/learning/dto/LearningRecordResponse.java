package com.onlinecurlearnmanos.learning.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record LearningRecordResponse(
        Long id,
        String subjectName,
        String knowledgePointName,
        String resourceTitle,
        String recordType,
        Integer durationMinutes,
        BigDecimal score,
        boolean mastered,
        String remark,
        LocalDateTime recordedAt
) {
}
