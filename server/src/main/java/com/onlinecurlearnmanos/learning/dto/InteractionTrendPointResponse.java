package com.onlinecurlearnmanos.learning.dto;

import java.time.LocalDate;

public record InteractionTrendPointResponse(
        LocalDate date,
        InteractionBreakdownResponse breakdown
) {

    public int total() {
        return breakdown.total();
    }
}
