package com.onlinecurlearnmanos.learning.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record GoalSaveRequest(
        Long subjectId,

        @NotBlank(message = "目标标题不能为空")
        @Size(max = 255, message = "目标标题不能超过 255 个字符")
        String title,

        @Size(max = 1000, message = "目标描述不能超过 1000 个字符")
        String description,

        LocalDate targetDate,

        @Size(max = 16, message = "优先级不能超过 16 个字符")
        String priority
) {
}
