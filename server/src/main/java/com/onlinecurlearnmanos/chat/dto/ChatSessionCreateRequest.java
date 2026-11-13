package com.onlinecurlearnmanos.chat.dto;

import jakarta.validation.constraints.Size;

public record ChatSessionCreateRequest(
        Long subjectId,
        Long knowledgePointId,

        @Size(max = 255, message = "自定义主题不能超过 255 个字符")
        String topic,

        @Size(max = 255, message = "会话标题不能超过 255 个字符")
        String title
) {
}
