package com.onlinecurlearnmanos.career.dto;

import java.util.List;

public record GeneratedCareerAnalysis(
        String title,
        String summary,
        List<GeneratedCareerInsight> insights
) {
}
