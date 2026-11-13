package com.onlinecurlearnmanos.learning.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record LearningPlanItemMoveRequest(
        @NotBlank(message = "移动方向不能为空")
        @Pattern(regexp = "UP|DOWN", message = "移动方向只能是 UP 或 DOWN")
        String direction
) {
}
