package com.onlinecurlearnmanos.ai.dto;

import jakarta.validation.constraints.Size;

public record LlmProviderTestRequest(
        @Size(max = 1000, message = "测试消息不能超过 1000 个字符")
        String message
) {
}
