package com.onlinecurlearnmanos.cloudclass.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CloudClassTaskRequest(
        @NotBlank(message = "任务标题不能为空")
        @Size(max = 255, message = "任务标题不能超过 255 个字符")
        String title,

        @Size(max = 1000, message = "任务说明不能超过 1000 个字符")
        String description,

        @NotBlank(message = "任务类型不能为空")
        @Size(max = 16, message = "任务类型不能超过 16 个字符")
        String taskType,

        LocalDateTime dueAt,

        @Min(value = 0, message = "奖励积分不能小于 0")
        Integer rewardPoints,

        Boolean allowResubmit,

        Boolean allowLateSubmit,

        BigDecimal maxScore,

        Long knowledgePointId,

        @Size(max = 16, message = "任务状态不能超过 16 个字符")
        String status
) {
}
