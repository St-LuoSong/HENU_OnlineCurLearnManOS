package com.onlinecurlearnmanos.learning.service;

import com.onlinecurlearnmanos.learning.dto.ProgressResponse;
import com.onlinecurlearnmanos.learning.dto.ProgressUpdateRequest;

import java.math.BigDecimal;
import java.util.List;

public interface ProgressService {

    List<ProgressResponse> list(Long userId);

    ProgressResponse upsert(Long userId, ProgressUpdateRequest request);

    void applyRecord(Long userId, Long subjectId, Long knowledgePointId, BigDecimal score, Boolean mastered);

    void markTransferPassed(Long userId, Long subjectId, Long knowledgePointId);

    void applyGrade(
            Long userId,
            Long subjectId,
            Long knowledgePointId,
            BigDecimal score,
            Boolean mastered
    );
}
