package com.onlinecurlearnmanos.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LlmProviderUpdateRequest(
        @NotBlank(message = "API 请求地址不能为空")
        @Size(max = 512, message = "API 请求地址不能超过 512 个字符")
        String baseUrl,

        @NotBlank(message = "默认模型不能为空")
        @Size(max = 128, message = "默认模型不能超过 128 个字符")
        String defaultModel,

        @Size(max = 4096, message = "API Key 长度异常")
        String apiKey,

        Boolean enabled
) {
}
