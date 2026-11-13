package com.onlinecurlearnmanos.learning.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record LearningPlanItemConvertRequest(
        @NotBlank(message = "转换目标不能为空")
        @Pattern(regexp = "TASK|GOAL", message = "转换目标只能是 TASK 或 GOAL")
        String target
) {
}
