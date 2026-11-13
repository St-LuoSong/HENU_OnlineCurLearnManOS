package com.onlinecurlearnmanos.practice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PracticeMessageRequest(
        @NotBlank(message = "请输入内容")
        @Size(max = 2000, message = "内容不能超过 2000 个字符")
        String content
) {
}
