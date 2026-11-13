package com.onlinecurlearnmanos.learning.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record LearningPlanItemUpdateRequest(
        @NotBlank(message = "条目标题不能为空")
        @Size(max = 255, message = "条目标题不能超过 255 个字符")
        String title,

        @Size(max = 1000, message = "条目说明不能超过 1000 个字符")
        String description,

        LocalDate startDate,

        LocalDate endDate
) {
}
