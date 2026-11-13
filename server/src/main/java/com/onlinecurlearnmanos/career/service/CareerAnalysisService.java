package com.onlinecurlearnmanos.career.service;

import com.onlinecurlearnmanos.career.dto.CareerAnalysisGenerateRequest;
import com.onlinecurlearnmanos.career.dto.CareerAnalysisResponse;
import com.onlinecurlearnmanos.career.dto.CareerComparisonResponse;
import com.onlinecurlearnmanos.career.dto.CareerInsightConvertRequest;
import com.onlinecurlearnmanos.career.dto.CareerInsightConvertResponse;

import java.util.List;

public interface CareerAnalysisService {

    List<CareerAnalysisResponse> list(Long userId);

    CareerAnalysisResponse get(Long userId, Long id);

    CareerAnalysisResponse generate(Long userId, CareerAnalysisGenerateRequest request);

    void delete(Long userId, Long id);

    CareerInsightConvertResponse convertInsight(
            Long userId,
            Long insightId,
            CareerInsightConvertRequest request
    );

    CareerComparisonResponse comparison(Long userId, Long analysisId);
}
