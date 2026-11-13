package com.onlinecurlearnmanos.career.dto;

import java.time.LocalDateTime;
import java.util.List;

public record CareerAnalysisResponse(
        Long id,
        String title,
        String summary,
        String status,
        String providerCode,
        String model,
        List<String> dataSources,
        LocalDateTime createdAt,
        List<CareerInsightResponse> insights
) {
}
