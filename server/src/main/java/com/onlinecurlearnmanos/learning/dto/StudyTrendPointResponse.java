package com.onlinecurlearnmanos.learning.dto;

import java.time.LocalDate;

/** 学习概览折线图的单日学习时长。 */
public record StudyTrendPointResponse(
        LocalDate date,
        int minutes
) {
}

