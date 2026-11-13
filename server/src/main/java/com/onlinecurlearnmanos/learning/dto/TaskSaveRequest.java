package com.onlinecurlearnmanos.learning.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record TaskSaveRequest(
        Long goalId,

        @NotBlank(message = "任务标题不能为空")
        @Size(max = 255, message = "任务标题不能超过 255 个字符")
        String title,

        @Size(max = 1000, message = "任务描述不能超过 1000 个字符")
        String description,

        @Size(max = 32, message = "任务分类不能超过 32 个字符")
        String category,

        LocalDateTime dueAt,

        @Size(max = 16, message = "优先级不能超过 16 个字符")
        String priority
) {
}
