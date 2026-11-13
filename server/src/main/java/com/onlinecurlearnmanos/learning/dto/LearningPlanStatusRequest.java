package com.onlinecurlearnmanos.learning.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LearningPlanStatusRequest(
        @NotBlank(message = "状态不能为空")
        @Size(max = 16, message = "状态不能超过 16 个字符")
        String status
) {
}
