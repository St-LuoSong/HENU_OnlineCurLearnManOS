package com.onlinecurlearnmanos.point.dto;

import java.time.LocalDate;
import java.util.List;

public record CheckinStatusResponse(
        boolean checkedToday,
        int currentStreak,
        int todayReward,
        int nextReward,
        long totalCheckins,
        List<LocalDate> recentCheckins
) {
}
