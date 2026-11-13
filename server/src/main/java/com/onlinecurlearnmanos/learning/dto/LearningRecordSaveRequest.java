package com.onlinecurlearnmanos.learning.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record LearningRecordSaveRequest(
        Long subjectId,
        Long knowledgePointId,
        Long resourceId,

        @NotBlank(message = "记录类型不能为空")
        @Size(max = 32, message = "记录类型不能超过 32 个字符")
        String recordType,

        @Min(value = 0, message = "学习时长不能小于 0")
        Integer durationMinutes,

        BigDecimal score,

        Boolean mastered,

        @Size(max = 1000, message = "备注不能超过 1000 个字符")
        String remark,

        LocalDateTime recordedAt
) {
}
