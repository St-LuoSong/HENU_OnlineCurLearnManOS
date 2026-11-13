package com.onlinecurlearnmanos.chat.dto;

import jakarta.validation.constraints.Size;

public record KnowledgeFavoriteNoteRequest(
        @Size(max = 500, message = "备注不能超过 500 个字符")
        String note
) {
}
