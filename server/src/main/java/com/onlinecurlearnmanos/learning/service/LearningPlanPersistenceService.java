package com.onlinecurlearnmanos.learning.service;

import com.onlinecurlearnmanos.ai.dto.LlmChatResponse;
import com.onlinecurlearnmanos.common.error.ErrorCode;
import com.onlinecurlearnmanos.common.exception.BusinessException;
import com.onlinecurlearnmanos.content.mapper.KnowledgePointMapper;
import com.onlinecurlearnmanos.content.mapper.LearningResourceMapper;
import com.onlinecurlearnmanos.learning.dto.GeneratedLearningPlan;
import com.onlinecurlearnmanos.learning.dto.GeneratedLearningPlanItem;
import com.onlinecurlearnmanos.learning.entity.LearningPlan;
import com.onlinecurlearnmanos.learning.entity.LearningPlanItem;
import com.onlinecurlearnmanos.learning.entity.LearningTask;
import com.onlinecurlearnmanos.learning.mapper.LearningPlanItemMapper;
import com.onlinecurlearnmanos.learning.mapper.LearningPlanMapper;
import com.onlinecurlearnmanos.learning.mapper.LearningTaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class LearningPlanPersistenceService {

    private final LearningPlanMapper planMapper;
    private final LearningPlanItemMapper itemMapper;
    private final LearningResourceMapper resourceMapper;
    private final KnowledgePointMapper knowledgePointMapper;
    private final LearningTaskMapper taskMapper;

    @Transactional
    public LearningPlan save(
            Long userId,
            GeneratedLearningPlan generated,
            LlmChatResponse providerResponse
    ) {
        if (!StringUtils.hasText(generated.title())
                || generated.items() == null
                || generated.items().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "AI 返回的学习计划不完整");
        }

        LearningPlan plan = new LearningPlan();
        plan.setUserId(userId);
        plan.setTitle(generated.title().trim());
        plan.setSummary(StringUtils.hasText(generated.summary()) ? generated.summary().trim() : null);
        plan.setStatus("DRAFT");
        plan.setProviderCode(providerResponse.providerCode());
        plan.setModel(providerResponse.model());
        plan.setDeleted(0);
        plan.setVersion(0);
        planMapper.insert(plan);

        int sortOrder = 1;
        for (GeneratedLearningPlanItem item : generated.items()) {
            if (!StringUtils.hasText(item.title())) {
                continue;
            }
            LearningPlanItem entity = new LearningPlanItem();
            entity.setPlanId(plan.getId());
            entity.setTitle(item.title().trim());
            entity.setDescription(
                    StringUtils.hasText(item.description()) ? item.description().trim() : null
            );
            entity.setStartDate(item.startDate());
            entity.setEndDate(item.endDate());
            entity.setResourceId(validResourceId(item.resourceId()));
            entity.setKnowledgePointId(validKnowledgePointId(item.knowledgePointId()));
            entity.setTaskId(validTaskId(userId, item.taskId()));
            entity.setStatus("TODO");
            entity.setSortOrder(sortOrder++);
            entity.setDeleted(0);
            entity.setVersion(0);
            itemMapper.insert(entity);
        }
        return plan;
    }

    /**
     * AI 可能返回不存在的资源 ID，这里统一置空而不是让整个计划失效。
     */
    private Long validResourceId(Long resourceId) {
        if (resourceId == null) {
            return null;
        }
        return resourceMapper.selectById(resourceId) == null ? null : resourceId;
    }

    private Long validKnowledgePointId(Long knowledgePointId) {
        if (knowledgePointId == null) {
            return null;
        }
        return knowledgePointMapper.selectById(knowledgePointId) == null
                ? null
                : knowledgePointId;
    }

    /**
     * 学习任务必须属于当前用户，避免模型幻觉出的任务 ID 造成跨用户关联。
     */
    private Long validTaskId(Long userId, Long taskId) {
        if (taskId == null) {
            return null;
        }
        long count = taskMapper.selectCount(new LambdaQueryWrapper<LearningTask>()
                .eq(LearningTask::getId, taskId)
                .eq(LearningTask::getUserId, userId));
        return count == 0 ? null : taskId;
    }
}
