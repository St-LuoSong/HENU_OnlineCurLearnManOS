package com.onlinecurlearnmanos.career.service;

import com.onlinecurlearnmanos.ai.dto.LlmChatResponse;
import com.onlinecurlearnmanos.career.dto.CareerDataPreviewResponse;
import com.onlinecurlearnmanos.career.dto.GeneratedCareerAnalysis;
import com.onlinecurlearnmanos.career.dto.GeneratedCareerInsight;
import com.onlinecurlearnmanos.career.entity.CareerAnalysis;
import com.onlinecurlearnmanos.career.entity.CareerAnalysisInsight;
import com.onlinecurlearnmanos.career.mapper.CareerAnalysisInsightMapper;
import com.onlinecurlearnmanos.career.mapper.CareerAnalysisMapper;
import com.onlinecurlearnmanos.common.error.ErrorCode;
import com.onlinecurlearnmanos.common.exception.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CareerAnalysisPersistenceService {

    private static final int MAX_TITLE_LENGTH = 255;
    private static final int MAX_SUMMARY_LENGTH = 1000;
    private static final int MAX_CONTENT_LENGTH = 2000;
    private static final int MAX_SCORE = 100;
    private static final String DEFAULT_CATEGORY = "SUGGESTION";

    private static final Set<String> CATEGORIES = Set.of(
            "DIRECTION",
            "INTEREST",
            "ABILITY",
            "SUGGESTION",
            "PATH",
            "GROWTH",
            "ACTION"
    );

    private final CareerAnalysisMapper analysisMapper;
    private final CareerAnalysisInsightMapper insightMapper;
    private final ObjectMapper objectMapper;

    @Transactional
    public CareerAnalysis save(
            Long userId,
            GeneratedCareerAnalysis generated,
            LlmChatResponse providerResponse,
            CareerDataPreviewResponse data
    ) {
        if (!StringUtils.hasText(generated.title())
                || generated.insights() == null
                || generated.insights().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "AI 返回的生涯分析不完整");
        }

        CareerAnalysis analysis = new CareerAnalysis();
        analysis.setUserId(userId);
        analysis.setTitle(truncate(generated.title().trim(), MAX_TITLE_LENGTH));
        analysis.setSummary(truncate(normalizeNullable(generated.summary()), MAX_SUMMARY_LENGTH));
        analysis.setStatus("COMPLETED");
        analysis.setProviderCode(providerResponse.providerCode());
        analysis.setModel(providerResponse.model());
        analysis.setDataSources(data.sources().isEmpty() ? null : String.join(",", data.sources()));
        analysis.setDataSnapshot(serializeSnapshot(data));
        analysis.setDeleted(0);
        analysis.setVersion(0);
        analysisMapper.insert(analysis);

        int sortOrder = 1;
        for (GeneratedCareerInsight insight : generated.insights()) {
            if (!StringUtils.hasText(insight.title())) {
                continue;
            }
            CareerAnalysisInsight entity = new CareerAnalysisInsight();
            entity.setAnalysisId(analysis.getId());
            entity.setCategory(normalizeCategory(insight.category()));
            entity.setTitle(truncate(insight.title().trim(), MAX_TITLE_LENGTH));
            entity.setContent(truncate(normalizeNullable(insight.content()), MAX_CONTENT_LENGTH));
            entity.setReason(truncate(normalizeNullable(insight.reason()), MAX_SUMMARY_LENGTH));
            entity.setTargetDate(parseDate(insight.targetDate()));
            entity.setScore(normalizeScore(insight.score()));
            entity.setSortOrder(sortOrder++);
            entity.setDeleted(0);
            entity.setVersion(0);
            insightMapper.insert(entity);
        }
        if (sortOrder == 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "AI 返回的生涯分析不完整");
        }
        return analysis;
    }

    private String normalizeCategory(String value) {
        if (!StringUtils.hasText(value)) {
            return DEFAULT_CATEGORY;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return CATEGORIES.contains(normalized) ? normalized : DEFAULT_CATEGORY;
    }

    private Integer normalizeScore(Integer score) {
        if (score == null) {
            return null;
        }
        return Math.max(0, Math.min(MAX_SCORE, score));
    }

    private String serializeSnapshot(CareerDataPreviewResponse data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private LocalDate parseDate(String value) {
        if (!StringUtils.hasText(value) || "null".equalsIgnoreCase(value.trim())) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (Exception ignored) {
            return null;
        }
    }
}
