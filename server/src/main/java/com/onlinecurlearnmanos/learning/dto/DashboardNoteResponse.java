package com.onlinecurlearnmanos.learning.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record DashboardNoteResponse(
        Long id,
        LocalDate noteDate,
        String content,
        String tone,
        LocalDateTime updatedAt
) {
}

