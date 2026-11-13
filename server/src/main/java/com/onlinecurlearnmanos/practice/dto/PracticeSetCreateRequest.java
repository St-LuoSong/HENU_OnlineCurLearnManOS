package com.onlinecurlearnmanos.practice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PracticeSetCreateRequest(
        @NotNull(message = "请选择课程")
        Long subjectId,

        Long knowledgePointId,

        @Pattern(regexp = "STANDARD|EXPERT|standard|expert", message = "AI 模式不合法")
        String mode,

        @Size(max = 255, message = "标题不能超过 255 个字符")
        String title
) {
}
