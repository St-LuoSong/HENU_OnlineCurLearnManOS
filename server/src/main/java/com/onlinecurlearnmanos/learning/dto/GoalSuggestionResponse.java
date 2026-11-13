package com.onlinecurlearnmanos.learning.dto;

import java.time.LocalDate;

public record GoalSuggestionResponse(
        String title,
        String description,
        LocalDate targetDate,
        String priority
) {
}
