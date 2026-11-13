package com.onlinecurlearnmanos.learning.dto;

import java.time.LocalDate;

public record CalendarEventResponse(
        String type,
        Long sourceId,
        String title,
        LocalDate date,
        String status,
        String detail
) {
}
