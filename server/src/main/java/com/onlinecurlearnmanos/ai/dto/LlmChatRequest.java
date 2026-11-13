package com.onlinecurlearnmanos.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LlmChatRequest(
        @NotBlank(message = "消息内容不能为空")
        @Size(max = 20000, message = "消息内容不能超过 20000 个字符")
        String message,

        @Size(max = 20000, message = "系统提示词不能超过 20000 个字符")
        String systemPrompt,

        @Size(max = 128, message = "模型名称不能超过 128 个字符")
        String model,

        Double temperature,

        @Size(max = 128, message = "幂等键不能超过 128 个字符")
        String idempotencyKey,

        @Size(max = 64, message = "请求编号不能超过 64 个字符")
        String requestId
) {
}
