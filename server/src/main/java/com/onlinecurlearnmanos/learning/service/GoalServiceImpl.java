package com.onlinecurlearnmanos.learning.service;

import com.onlinecurlearnmanos.common.error.ErrorCode;
import com.onlinecurlearnmanos.common.exception.BusinessException;
import com.onlinecurlearnmanos.ai.dto.LlmChatRequest;
import com.onlinecurlearnmanos.ai.service.LlmService;
import com.onlinecurlearnmanos.content.entity.Subject;
import com.onlinecurlearnmanos.content.mapper.SubjectMapper;
import com.onlinecurlearnmanos.learning.dto.GoalResponse;
import com.onlinecurlearnmanos.learning.dto.GoalSaveRequest;
import com.onlinecurlearnmanos.learning.dto.GoalGenerateRequest;
import com.onlinecurlearnmanos.learning.dto.GoalSuggestionResponse;
import com.onlinecurlearnmanos.learning.entity.LearningGoal;
import com.onlinecurlearnmanos.learning.entity.LearningTask;
import com.onlinecurlearnmanos.learning.mapper.LearningGoalMapper;
import com.onlinecurlearnmanos.learning.mapper.LearningTaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GoalServiceImpl implements GoalService {

    private static final String ACTIVE = "ACTIVE";
    private static final String COMPLETED = "COMPLETED";

    private final LearningGoalMapper goalMapper;
    private final LearningTaskMapper taskMapper;
    private final SubjectMapper subjectMapper;
    private final LlmService llmService;
    private final ObjectMapper objectMapper;

    @Override
    public List<GoalResponse> list(Long userId) {
        return goalMapper.selectList(new LambdaQueryWrapper<LearningGoal>()
                        .eq(LearningGoal::getUserId, userId)
                        .orderByAsc(LearningGoal::getStatus)
                        .orderByAsc(LearningGoal::getTargetDate)
                        .orderByDesc(LearningGoal::getId))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public GoalResponse create(Long userId, GoalSaveRequest request) {
        validateSubject(request.subjectId());
        LearningGoal goal = new LearningGoal();
        goal.setUserId(userId);
        applyRequest(goal, request);
        goal.setStatus(ACTIVE);
        goal.setDeleted(0);
        goal.setVersion(0);
        goalMapper.insert(goal);
        return toResponse(goal);
    }

    @Override
    @Transactional
    public GoalResponse update(Long id, Long userId, GoalSaveRequest request) {
        LearningGoal goal = getRequired(id, userId);
        validateSubject(request.subjectId());
        goalMapper.update(null, new LambdaUpdateWrapper<LearningGoal>()
                .eq(LearningGoal::getId, id)
                .eq(LearningGoal::getUserId, userId)
                .set(LearningGoal::getSubjectId, request.subjectId())
                .set(LearningGoal::getTitle, request.title().trim())
                .set(LearningGoal::getDescription, normalizeNullable(request.description()))
                .set(LearningGoal::getTargetDate, request.targetDate())
                .set(LearningGoal::getPriority, normalizePriority(request.priority())));
        return toResponse(getRequired(id, userId));
    }

    @Override
    @Transactional
    public GoalResponse complete(Long id, Long userId) {
        LearningGoal goal = getRequired(id, userId);
        goal.setStatus(COMPLETED);
        goal.setCompletedAt(LocalDateTime.now());
        goalMapper.updateById(goal);
        return toResponse(goal);
    }

    @Override
    public List<GoalSuggestionResponse> generate(Long userId, GoalGenerateRequest request) {
        String prompt = """
                你是大学生的学习规划助手。请根据下面的学习需求，生成 3 到 5 个具体、可执行的学习目标。
                目标要求：
                1. 标题简洁明确；
                2. 描述说明完成标准；
                3. 目标日期格式为 yyyy-MM-dd，如果无法判断可以返回 null；
                4. 优先级只能是 HIGH、MEDIUM、LOW。

                只返回 JSON 数组，不要输出 Markdown，不要输出解释。数组元素格式：
                [{"title":"...","description":"...","targetDate":"2026-10-01","priority":"MEDIUM"}]

                学生需求：
                %s
                """.formatted(request.requirement().trim());
        return llmService.execute(
                userId,
                new LlmChatRequest(
                        prompt,
                        "你是学习目标规划助手，只输出结构化 JSON。",
                        null,
                        0.4,
                        null,
                        null
                ),
                "AI_CHAT",
                "AI 学习目标生成",
                response -> {
                    try {
                        String content = stripCodeFence(response.content());
                        List<GeneratedGoal> generated = objectMapper.readValue(
                                content,
                                new TypeReference<List<GeneratedGoal>>() {
                                }
                        );
                        return generated.stream()
                                .map(item -> new GoalSuggestionResponse(
                                        item.title(),
                                        item.description(),
                                        parseDate(item.targetDate()),
                                        normalizePriority(item.priority())
                                ))
                                .toList();
                    } catch (Exception exception) {
                        throw new BusinessException(
                                ErrorCode.INTERNAL_ERROR,
                                "AI 生成目标失败，请重试"
                        );
                    }
                }
        );
    }

    @Override
    @Transactional
    public void delete(Long id, Long userId) {
        getRequired(id, userId);
        taskMapper.delete(new LambdaQueryWrapper<LearningTask>()
                .eq(LearningTask::getGoalId, id));
        goalMapper.deleteById(id);
    }

    private void applyRequest(LearningGoal goal, GoalSaveRequest request) {
        goal.setSubjectId(request.subjectId());
        goal.setTitle(request.title().trim());
        goal.setDescription(normalizeNullable(request.description()));
        goal.setTargetDate(request.targetDate());
        goal.setPriority(normalizePriority(request.priority()));
    }

    private void validateSubject(Long subjectId) {
        if (subjectId == null) {
            return;
        }
        Subject subject = subjectMapper.selectById(subjectId);
        if (subject == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "学科不存在");
        }
    }

    private LearningGoal getRequired(Long id, Long userId) {
        LearningGoal goal = goalMapper.selectOne(new LambdaQueryWrapper<LearningGoal>()
                .eq(LearningGoal::getId, id)
                .eq(LearningGoal::getUserId, userId)
                .last("LIMIT 1"));
        if (goal == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "学习目标不存在");
        }
        return goal;
    }

    private GoalResponse toResponse(LearningGoal goal) {
        Subject subject = goal.getSubjectId() == null ? null : subjectMapper.selectById(goal.getSubjectId());
        long taskCount = taskMapper.selectCount(new LambdaQueryWrapper<LearningTask>()
                .eq(LearningTask::getGoalId, goal.getId()));
        long completedCount = taskMapper.selectCount(new LambdaQueryWrapper<LearningTask>()
                .eq(LearningTask::getGoalId, goal.getId())
                .eq(LearningTask::getStatus, COMPLETED));
        int progress = COMPLETED.equals(goal.getStatus())
                ? 100
                : taskCount == 0
                        ? 0
                        : (int) Math.round(completedCount * 100.0 / taskCount);
        return new GoalResponse(
                goal.getId(),
                goal.getCycleId(),
                goal.getSubjectId(),
                subject == null ? null : subject.getName(),
                goal.getTitle(),
                goal.getDescription(),
                goal.getTargetDate(),
                goal.getPriority(),
                goal.getStatus(),
                goal.getCompletedAt(),
                taskCount,
                completedCount,
                progress
        );
    }

    private String normalizePriority(String priority) {
        if (!StringUtils.hasText(priority)) {
            return "MEDIUM";
        }
        return priority.trim().toUpperCase();
    }

    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String stripCodeFence(String content) {
        String normalized = content == null ? "" : content.trim();
        if (normalized.startsWith("```")) {
            normalized = normalized.replaceFirst("^```(?:json)?\\s*", "");
            normalized = normalized.replaceFirst("\\s*```$", "");
        }
        return normalized.trim();
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

    private record GeneratedGoal(
            String title,
            String description,
            String targetDate,
            String priority
    ) {
    }
}
