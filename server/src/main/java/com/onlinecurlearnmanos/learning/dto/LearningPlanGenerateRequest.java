package com.onlinecurlearnmanos.learning.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LearningPlanGenerateRequest(
        @NotBlank(message = "请描述学习规划需求")
        @Size(max = 2000, message = "学习规划需求不能超过 2000 个字符")
        String requirement
) {
}
