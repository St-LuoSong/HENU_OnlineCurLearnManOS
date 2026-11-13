package com.onlinecurlearnmanos.chat.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record KnowledgeFavoriteRequest(
        @NotNull(message = "消息编号不能为空")
        Long messageId,

        @Size(max = 500, message = "备注不能超过 500 个字符")
        String note
) {
}
