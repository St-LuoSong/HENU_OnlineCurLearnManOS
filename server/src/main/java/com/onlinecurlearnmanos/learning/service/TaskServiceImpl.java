package com.onlinecurlearnmanos.learning.service;

import com.onlinecurlearnmanos.common.error.ErrorCode;
import com.onlinecurlearnmanos.common.exception.BusinessException;
import com.onlinecurlearnmanos.learning.dto.TaskResponse;
import com.onlinecurlearnmanos.learning.dto.TaskSaveRequest;
import com.onlinecurlearnmanos.learning.entity.LearningGoal;
import com.onlinecurlearnmanos.learning.entity.LearningTask;
import com.onlinecurlearnmanos.learning.mapper.LearningGoalMapper;
import com.onlinecurlearnmanos.learning.mapper.LearningTaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TaskServiceImpl implements TaskService {

    private static final String TODO = "TODO";
    private static final String COMPLETED = "COMPLETED";

    private final LearningTaskMapper taskMapper;
    private final LearningGoalMapper goalMapper;

    @Override
    public List<TaskResponse> list(Long userId, Long goalId, String status) {
        LambdaQueryWrapper<LearningTask> wrapper = new LambdaQueryWrapper<LearningTask>()
                .eq(LearningTask::getUserId, userId);
        if (goalId != null) {
            wrapper.eq(LearningTask::getGoalId, goalId);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(LearningTask::getStatus, status.trim().toUpperCase());
        }
        wrapper.orderByAsc(LearningTask::getStatus)
                .orderByAsc(LearningTask::getDueAt)
                .orderByDesc(LearningTask::getId);
        return taskMapper.selectList(wrapper)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public TaskResponse create(Long userId, TaskSaveRequest request) {
        validateGoal(request.goalId(), userId);
        LearningTask task = new LearningTask();
        task.setUserId(userId);
        applyRequest(task, request);
        task.setStatus(TODO);
        task.setDeleted(0);
        task.setVersion(0);
        taskMapper.insert(task);
        return toResponse(task);
    }

    @Override
    @Transactional
    public TaskResponse update(Long id, Long userId, TaskSaveRequest request) {
        LearningTask task = getRequired(id, userId);
        validateGoal(request.goalId(), userId);
        taskMapper.update(null, new LambdaUpdateWrapper<LearningTask>()
                .eq(LearningTask::getId, id)
                .eq(LearningTask::getUserId, userId)
                .set(LearningTask::getGoalId, request.goalId())
                .set(LearningTask::getTitle, request.title().trim())
                .set(LearningTask::getDescription, normalizeNullable(request.description()))
                .set(LearningTask::getCategory, normalizeNullable(request.category()))
                .set(LearningTask::getDueAt, request.dueAt())
                .set(LearningTask::getPriority, normalizePriority(request.priority())));
        return toResponse(getRequired(id, userId));
    }

    @Override
    @Transactional
    public TaskResponse complete(Long id, Long userId) {
        LearningTask task = getRequired(id, userId);
        task.setStatus(COMPLETED);
        task.setCompletedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        return toResponse(task);
    }

    @Override
    @Transactional
    public TaskResponse reopen(Long id, Long userId) {
        getRequired(id, userId);
        taskMapper.update(null, new LambdaUpdateWrapper<LearningTask>()
                .eq(LearningTask::getId, id)
                .eq(LearningTask::getUserId, userId)
                .set(LearningTask::getStatus, TODO)
                .set(LearningTask::getCompletedAt, null));
        return toResponse(getRequired(id, userId));
    }

    @Override
    @Transactional
    public void delete(Long id, Long userId) {
        getRequired(id, userId);
        taskMapper.deleteById(id);
    }

    private void applyRequest(LearningTask task, TaskSaveRequest request) {
        task.setGoalId(request.goalId());
        task.setTitle(request.title().trim());
        task.setDescription(normalizeNullable(request.description()));
        task.setCategory(normalizeNullable(request.category()));
        task.setDueAt(request.dueAt());
        task.setPriority(normalizePriority(request.priority()));
    }

    private void validateGoal(Long goalId, Long userId) {
        if (goalId == null) {
            return;
        }
        LearningGoal goal = goalMapper.selectOne(new LambdaQueryWrapper<LearningGoal>()
                .eq(LearningGoal::getId, goalId)
                .eq(LearningGoal::getUserId, userId)
                .last("LIMIT 1"));
        if (goal == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "学习目标不存在");
        }
    }

    private LearningTask getRequired(Long id, Long userId) {
        LearningTask task = taskMapper.selectOne(new LambdaQueryWrapper<LearningTask>()
                .eq(LearningTask::getId, id)
                .eq(LearningTask::getUserId, userId)
                .last("LIMIT 1"));
        if (task == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "学习任务不存在");
        }
        return task;
    }

    private TaskResponse toResponse(LearningTask task) {
        LearningGoal goal = task.getGoalId() == null
                ? null
                : goalMapper.selectById(task.getGoalId());
        return new TaskResponse(
                task.getId(),
                task.getCycleId(),
                task.getGoalId(),
                goal == null ? null : goal.getTitle(),
                task.getTitle(),
                task.getDescription(),
                task.getCategory(),
                task.getDueAt(),
                task.getPriority(),
                task.getStatus(),
                task.getCompletedAt()
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
}
