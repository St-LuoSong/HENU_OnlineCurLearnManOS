package com.onlinecurlearnmanos.forum.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ForumMessageAckRequest(
        @NotBlank String type,
        @NotNull @Positive Long sourceId
) {
}
