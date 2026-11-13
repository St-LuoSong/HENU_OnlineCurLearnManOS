package com.onlinecurlearnmanos.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChatMessageSendRequest(
        @NotBlank(message = "消息内容不能为空")
        @Size(max = 20000, message = "消息内容不能超过 20000 个字符")
        String content,
        boolean retry,

        @Pattern(regexp = "STANDARD|EXPERT|standard|expert", message = "AI 模式不合法")
        String mode
) {
}
