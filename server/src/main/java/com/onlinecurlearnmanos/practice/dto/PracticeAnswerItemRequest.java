package com.onlinecurlearnmanos.practice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PracticeAnswerItemRequest(
        @NotNull(message = "题目编号不能为空")
        Long itemId,

        @NotNull(message = "答案不能为空")
        @Size(max = 20000, message = "答案不能超过 20000 个字符")
        String answer
) {
}
