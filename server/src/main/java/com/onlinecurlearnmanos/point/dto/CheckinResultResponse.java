package com.onlinecurlearnmanos.point.dto;

public record CheckinResultResponse(
        int earnedPoints,
        CheckinStatusResponse status
) {
}
