package com.onlinecurlearnmanos.practice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PracticeSessionCreateRequest(
        @NotNull(message = "请选择课程")
        Long subjectId,

        Long knowledgePointId,

        @Size(max = 255, message = "会话标题不能超过 255 个字符")
        String title
) {
}
