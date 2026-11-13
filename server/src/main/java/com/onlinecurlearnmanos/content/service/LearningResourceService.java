package com.onlinecurlearnmanos.content.service;

import com.onlinecurlearnmanos.common.api.PageResult;
import com.onlinecurlearnmanos.content.dto.LearningResourceResponse;
import com.onlinecurlearnmanos.content.dto.LearningResourceSaveRequest;

public interface LearningResourceService {

    PageResult<LearningResourceResponse> list(
            Long subjectId,
            Long knowledgePointId,
            String keyword,
            long page,
            long size,
            boolean includeUnpublished
    );

    LearningResourceResponse get(Long id, boolean includeUnpublished);

    void recordView(Long userId, Long resourceId);

    LearningResourceResponse create(
            LearningResourceSaveRequest request,
            Long publisherId,
            Long operatorId
    );

    LearningResourceResponse update(
            Long id,
            LearningResourceSaveRequest request,
            Long operatorId
    );

    LearningResourceResponse updateStatus(Long id, String status, Long operatorId);

    void delete(Long id, Long operatorId);
}
