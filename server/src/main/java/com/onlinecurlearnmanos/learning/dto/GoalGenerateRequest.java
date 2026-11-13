package com.onlinecurlearnmanos.learning.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GoalGenerateRequest(
        @NotBlank(message = "请描述你的学习需求")
        @Size(max = 2000, message = "学习需求不能超过 2000 个字符")
        String requirement
) {
}
