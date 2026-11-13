package com.onlinecurlearnmanos.growth.dto;

import java.time.LocalDate;

public record GrowthCalendarEventResponse(
        String type,
        Long sourceId,
        String title,
        LocalDate date,
        String status,
        String detail
) {
}
