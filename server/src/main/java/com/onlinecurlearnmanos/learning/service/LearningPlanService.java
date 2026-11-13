package com.onlinecurlearnmanos.learning.service;

import com.onlinecurlearnmanos.learning.dto.LearningPlanGenerateRequest;
import com.onlinecurlearnmanos.learning.dto.LearningPlanItemConvertRequest;
import com.onlinecurlearnmanos.learning.dto.LearningPlanItemMoveRequest;
import com.onlinecurlearnmanos.learning.dto.LearningPlanItemUpdateRequest;
import com.onlinecurlearnmanos.learning.dto.LearningPlanResponse;

import java.util.List;

public interface LearningPlanService {

    List<LearningPlanResponse> list(Long userId);

    LearningPlanResponse get(Long userId, Long id);

    LearningPlanResponse generate(Long userId, LearningPlanGenerateRequest request);

    void delete(Long userId, Long id);

    LearningPlanResponse updateStatus(Long userId, Long planId, String status);

    LearningPlanResponse activate(Long userId, Long planId);

    LearningPlanResponse updateItemStatus(
            Long userId,
            Long planId,
            Long itemId,
            String status
    );

    LearningPlanResponse updateItem(
            Long userId,
            Long planId,
            Long itemId,
            LearningPlanItemUpdateRequest request
    );

    LearningPlanResponse convertItem(
            Long userId,
            Long planId,
            Long itemId,
            LearningPlanItemConvertRequest request
    );

    LearningPlanResponse moveItem(
            Long userId,
            Long planId,
            Long itemId,
            LearningPlanItemMoveRequest request
    );
}
