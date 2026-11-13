package com.onlinecurlearnmanos.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record QuestionOptionRequest(
        @NotBlank(message = "选项标识不能为空")
        @Size(max = 8, message = "选项标识不能超过 8 个字符")
        String key,

        @NotBlank(message = "选项内容不能为空")
        @Size(max = 500, message = "选项内容不能超过 500 个字符")
        String text
) {
}
