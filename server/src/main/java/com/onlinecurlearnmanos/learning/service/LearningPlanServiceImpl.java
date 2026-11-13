package com.onlinecurlearnmanos.learning.service;

import com.onlinecurlearnmanos.ai.dto.LlmChatRequest;
import com.onlinecurlearnmanos.ai.service.LlmService;
import com.onlinecurlearnmanos.common.error.ErrorCode;
import com.onlinecurlearnmanos.common.exception.BusinessException;
import com.onlinecurlearnmanos.learning.dto.GeneratedLearningPlan;
import com.onlinecurlearnmanos.learning.dto.GoalResponse;
import com.onlinecurlearnmanos.learning.dto.GoalSaveRequest;
import com.onlinecurlearnmanos.learning.dto.LearningPlanGenerateRequest;
import com.onlinecurlearnmanos.learning.dto.LearningPlanItemConvertRequest;
import com.onlinecurlearnmanos.learning.dto.LearningPlanItemMoveRequest;
import com.onlinecurlearnmanos.learning.dto.LearningPlanItemResponse;
import com.onlinecurlearnmanos.learning.dto.LearningPlanItemUpdateRequest;
import com.onlinecurlearnmanos.learning.dto.LearningPlanResponse;
import com.onlinecurlearnmanos.learning.dto.TaskResponse;
import com.onlinecurlearnmanos.learning.dto.TaskSaveRequest;
import com.onlinecurlearnmanos.learning.entity.LearningPlan;
import com.onlinecurlearnmanos.learning.entity.LearningPlanItem;
import com.onlinecurlearnmanos.learning.mapper.LearningPlanItemMapper;
import com.onlinecurlearnmanos.learning.mapper.LearningPlanMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class LearningPlanServiceImpl implements LearningPlanService {

    private final LearningPlanMapper planMapper;
    private final LearningPlanItemMapper itemMapper;
    private final LearningPlanPersistenceService persistenceService;
    private final LlmService llmService;
    private final ObjectMapper objectMapper;
    private final GoalService goalService;
    private final TaskService taskService;

    @Override
    public List<LearningPlanResponse> list(Long userId) {
        return planMapper.selectList(new LambdaQueryWrapper<LearningPlan>()
                        .eq(LearningPlan::getUserId, userId)
                        .orderByDesc(LearningPlan::getUpdatedAt)
                        .orderByDesc(LearningPlan::getId))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public LearningPlanResponse get(Long userId, Long id) {
        return toResponse(getRequired(userId, id));
    }

    @Override
    public LearningPlanResponse generate(Long userId, LearningPlanGenerateRequest request) {
        String prompt = """
                你是大学生学习规划助手。请根据学生需求生成一份可执行的学习计划。
                要求：
                1. 计划标题简洁明确；
                2. 计划摘要说明目标和完成标准；
                3. 生成 4 到 8 个计划条目；
                4. 每个条目包含标题、说明、开始日期、结束日期；
                5. 日期使用 yyyy-MM-dd 格式，无法判断时返回 null；
                6. 只返回 JSON，不要输出 Markdown 和解释。

                返回格式：
                {
                  "title": "计划标题",
                  "summary": "计划摘要",
                  "items": [
                    {
                      "title": "条目标题",
                      "description": "完成标准",
                      "startDate": "2026-09-20",
                      "endDate": "2026-09-26",
                      "resourceId": null,
                      "knowledgePointId": null,
                      "taskId": null
                    }
                  ]
                }

                学生需求：
                %s
                """.formatted(request.requirement().trim());

        return llmService.execute(
                userId,
                new LlmChatRequest(
                        prompt,
                        "你是学习规划助手，只输出结构化 JSON。",
                        null,
                        0.4,
                        null,
                        null
                ),
                "AI_PLAN",
                "AI 学习规划生成",
                providerResponse -> {
                    try {
                        String content = stripCodeFence(providerResponse.content());
                        GeneratedLearningPlan generated = objectMapper.readValue(
                                content,
                                GeneratedLearningPlan.class
                        );
                        LearningPlan saved =
                                persistenceService.save(userId, generated, providerResponse);
                        return toResponse(saved);
                    } catch (BusinessException exception) {
                        throw exception;
                    } catch (Exception exception) {
                        throw new BusinessException(
                                ErrorCode.INTERNAL_ERROR,
                                "AI 生成学习计划失败，请重试"
                        );
                    }
                }
        );
    }

    @Override
    @Transactional
    public void delete(Long userId, Long id) {
        getRequired(userId, id);
        itemMapper.delete(new LambdaQueryWrapper<LearningPlanItem>()
                .eq(LearningPlanItem::getPlanId, id));
        planMapper.deleteById(id);
    }

    @Override
    @Transactional
    public LearningPlanResponse updateStatus(Long userId, Long planId, String status) {
        LearningPlan plan = getRequired(userId, planId);
        String normalized = normalizePlanStatus(status);
        if ("ACTIVE".equals(normalized)) {
            LambdaUpdateWrapper<LearningPlan> archiveWrapper = new LambdaUpdateWrapper<LearningPlan>()
                    .eq(LearningPlan::getUserId, userId)
                    .eq(LearningPlan::getStatus, "ACTIVE")
                    .ne(LearningPlan::getId, planId);
            if (plan.getCycleId() != null) {
                archiveWrapper.eq(LearningPlan::getCycleId, plan.getCycleId());
            }
            planMapper.update(null, archiveWrapper.set(LearningPlan::getStatus, "ARCHIVED"));
        }
        planMapper.update(null, new LambdaUpdateWrapper<LearningPlan>()
                .eq(LearningPlan::getId, plan.getId())
                .set(LearningPlan::getStatus, normalized));
        return toResponse(getRequired(userId, planId));
    }

    @Override
    @Transactional
    public LearningPlanResponse activate(Long userId, Long planId) {
        LearningPlan plan = getRequired(userId, planId);
        LambdaUpdateWrapper<LearningPlan> archiveWrapper = new LambdaUpdateWrapper<LearningPlan>()
                .eq(LearningPlan::getUserId, userId)
                .eq(LearningPlan::getStatus, "ACTIVE")
                .ne(LearningPlan::getId, planId);
        if (plan.getCycleId() != null) {
            archiveWrapper.eq(LearningPlan::getCycleId, plan.getCycleId());
        }
        planMapper.update(null, archiveWrapper.set(LearningPlan::getStatus, "ARCHIVED"));
        planMapper.update(null, new LambdaUpdateWrapper<LearningPlan>()
                .eq(LearningPlan::getId, planId)
                .eq(LearningPlan::getUserId, userId)
                .set(LearningPlan::getStatus, "ACTIVE"));
        return toResponse(getRequired(userId, planId));
    }

    @Override
    @Transactional
    public LearningPlanResponse updateItemStatus(
            Long userId,
            Long planId,
            Long itemId,
            String status
    ) {
        getRequired(userId, planId);
        LearningPlanItem item = getRequiredItem(planId, itemId);
        itemMapper.update(null, new LambdaUpdateWrapper<LearningPlanItem>()
                .eq(LearningPlanItem::getId, item.getId())
                .set(LearningPlanItem::getStatus, normalizeItemStatus(status)));
        syncPlanStatusByItems(userId, planId);
        return toResponse(getRequired(userId, planId));
    }

    @Override
    @Transactional
    public LearningPlanResponse updateItem(
            Long userId,
            Long planId,
            Long itemId,
            LearningPlanItemUpdateRequest request
    ) {
        getRequired(userId, planId);
        LearningPlanItem item = getRequiredItem(planId, itemId);
        itemMapper.update(null, new LambdaUpdateWrapper<LearningPlanItem>()
                .eq(LearningPlanItem::getId, item.getId())
                .set(LearningPlanItem::getTitle, request.title().trim())
                .set(LearningPlanItem::getDescription, normalizeNullable(request.description()))
                .set(LearningPlanItem::getStartDate, request.startDate())
                .set(LearningPlanItem::getEndDate, request.endDate()));
        return toResponse(getRequired(userId, planId));
    }

    @Override
    @Transactional
    public LearningPlanResponse convertItem(
            Long userId,
            Long planId,
            Long itemId,
            LearningPlanItemConvertRequest request
    ) {
        getRequired(userId, planId);
        LearningPlanItem item = getRequiredItem(planId, itemId);
        if ("TASK".equals(request.target())) {
            if (item.getTaskId() != null) {
                throw new BusinessException(ErrorCode.CONFLICT, "该条目已经转换为学习任务");
            }
            TaskResponse task = taskService.create(userId, new TaskSaveRequest(
                    null,
                    item.getTitle(),
                    item.getDescription(),
                    "PLAN",
                    item.getEndDate() == null ? null : item.getEndDate().atStartOfDay(),
                    "MEDIUM"
            ));
            itemMapper.update(null, new LambdaUpdateWrapper<LearningPlanItem>()
                    .eq(LearningPlanItem::getId, item.getId())
                    .set(LearningPlanItem::getTaskId, task.id()));
        } else {
            if (item.getGoalId() != null) {
                throw new BusinessException(ErrorCode.CONFLICT, "该条目已经转换为学习目标");
            }
            GoalResponse goal = goalService.create(userId, new GoalSaveRequest(
                    null,
                    item.getTitle(),
                    item.getDescription(),
                    item.getEndDate(),
                    "MEDIUM"
            ));
            itemMapper.update(null, new LambdaUpdateWrapper<LearningPlanItem>()
                    .eq(LearningPlanItem::getId, item.getId())
                    .set(LearningPlanItem::getGoalId, goal.id()));
        }
        return toResponse(getRequired(userId, planId));
    }

    /**
     * 条目全部完成时把计划标记为已完成；重新打开任一条目则回到进行中。
     */
    private void syncPlanStatusByItems(Long userId, Long planId) {
        List<LearningPlanItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<LearningPlanItem>()
                        .eq(LearningPlanItem::getPlanId, planId)
        );
        if (items.isEmpty()) {
            return;
        }
        boolean allDone = items.stream().allMatch(item -> "DONE".equals(item.getStatus()));
        LearningPlan plan = getRequired(userId, planId);
        if (allDone && !"COMPLETED".equals(plan.getStatus())) {
            planMapper.update(null, new LambdaUpdateWrapper<LearningPlan>()
                    .eq(LearningPlan::getId, planId)
                    .set(LearningPlan::getStatus, "COMPLETED"));
        } else if (!allDone && "COMPLETED".equals(plan.getStatus())) {
            planMapper.update(null, new LambdaUpdateWrapper<LearningPlan>()
                    .eq(LearningPlan::getId, planId)
                    .set(LearningPlan::getStatus, "ACTIVE"));
        }
    }

    private LearningPlanItem getRequiredItem(Long planId, Long itemId) {
        LearningPlanItem item = itemMapper.selectOne(new LambdaQueryWrapper<LearningPlanItem>()
                .eq(LearningPlanItem::getId, itemId)
                .eq(LearningPlanItem::getPlanId, planId)
                .last("LIMIT 1"));
        if (item == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "学习计划条目不存在");
        }
        return item;
    }

    private String normalizePlanStatus(String status) {
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!List.of("DRAFT", "ACTIVE", "COMPLETED", "ARCHIVED").contains(normalized)) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "计划状态只能是 DRAFT、ACTIVE、COMPLETED 或 ARCHIVED"
            );
        }
        return normalized;
    }

    private String normalizeItemStatus(String status) {
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!List.of("TODO", "DONE").contains(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "条目状态只能是 TODO 或 DONE");
        }
        return normalized;
    }

    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    @Override
    @Transactional
    public LearningPlanResponse moveItem(
            Long userId,
            Long planId,
            Long itemId,
            LearningPlanItemMoveRequest request
    ) {
        getRequired(userId, planId);
        List<LearningPlanItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<LearningPlanItem>()
                        .eq(LearningPlanItem::getPlanId, planId)
                        .orderByAsc(LearningPlanItem::getSortOrder)
                        .orderByAsc(LearningPlanItem::getId)
        );
        int index = -1;
        for (int position = 0; position < items.size(); position++) {
            if (items.get(position).getId().equals(itemId)) {
                index = position;
                break;
            }
        }
        if (index < 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "学习计划条目不存在");
        }
        int swapIndex = "UP".equals(request.direction()) ? index - 1 : index + 1;
        if (swapIndex < 0 || swapIndex >= items.size()) {
            return toResponse(getRequired(userId, planId));
        }
        LearningPlanItem current = items.get(index);
        LearningPlanItem neighbor = items.get(swapIndex);
        int currentOrder = current.getSortOrder() == null ? index + 1 : current.getSortOrder();
        int neighborOrder = neighbor.getSortOrder() == null ? swapIndex + 1 : neighbor.getSortOrder();
        itemMapper.update(null, new LambdaUpdateWrapper<LearningPlanItem>()
                .eq(LearningPlanItem::getId, current.getId())
                .set(LearningPlanItem::getSortOrder, neighborOrder));
        itemMapper.update(null, new LambdaUpdateWrapper<LearningPlanItem>()
                .eq(LearningPlanItem::getId, neighbor.getId())
                .set(LearningPlanItem::getSortOrder, currentOrder));
        return toResponse(getRequired(userId, planId));
    }

    private LearningPlan getRequired(Long userId, Long id) {
        LearningPlan plan = planMapper.selectOne(new LambdaQueryWrapper<LearningPlan>()
                .eq(LearningPlan::getId, id)
                .eq(LearningPlan::getUserId, userId)
                .last("LIMIT 1"));
        if (plan == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "学习计划不存在");
        }
        return plan;
    }

    private LearningPlanResponse toResponse(LearningPlan plan) {
        List<LearningPlanItemResponse> items = itemMapper.selectList(
                        new LambdaQueryWrapper<LearningPlanItem>()
                                .eq(LearningPlanItem::getPlanId, plan.getId())
                                .orderByAsc(LearningPlanItem::getSortOrder)
                                .orderByAsc(LearningPlanItem::getId))
                .stream()
                .map(item -> new LearningPlanItemResponse(
                        item.getId(),
                        item.getTitle(),
                        item.getDescription(),
                        item.getStartDate(),
                        item.getEndDate(),
                        item.getResourceId(),
                        item.getKnowledgePointId(),
                        item.getGoalId(),
                        item.getTaskId(),
                        item.getStatus(),
                        item.getSortOrder() == null ? 0 : item.getSortOrder()
                ))
                .toList();
        return new LearningPlanResponse(
                plan.getId(),
                plan.getCycleId(),
                plan.getTitle(),
                plan.getSummary(),
                plan.getStatus(),
                plan.getProviderCode(),
                plan.getModel(),
                plan.getCreatedAt(),
                plan.getUpdatedAt(),
                items.size(),
                (int) items.stream().filter(item -> "DONE".equals(item.status())).count(),
                items.isEmpty()
                        ? 0
                        : (int) Math.round(
                                items.stream().filter(item -> "DONE".equals(item.status())).count()
                                        * 100.0
                                        / items.size()
                        ),
                items
        );
    }

    private String stripCodeFence(String content) {
        String normalized = content == null ? "" : content.trim();
        if (normalized.startsWith("```")) {
            normalized = normalized.replaceFirst("^```(?:json)?\\s*", "");
            normalized = normalized.replaceFirst("\\s*```$", "");
        }
        return normalized.trim();
    }
}
