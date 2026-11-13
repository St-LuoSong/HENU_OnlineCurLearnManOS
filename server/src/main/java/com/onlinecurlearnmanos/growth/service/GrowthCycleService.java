package com.onlinecurlearnmanos.growth.service;

import com.onlinecurlearnmanos.common.error.ErrorCode;
import com.onlinecurlearnmanos.common.exception.BusinessException;
import com.onlinecurlearnmanos.growth.dto.GrowthCycleCreateRequest;
import com.onlinecurlearnmanos.growth.dto.GrowthCycleResponse;
import com.onlinecurlearnmanos.growth.dto.GrowthDirectionResponse;
import com.onlinecurlearnmanos.growth.dto.GrowthReviewResponse;
import com.onlinecurlearnmanos.growth.entity.GrowthCycle;
import com.onlinecurlearnmanos.growth.entity.GrowthCycleDirection;
import com.onlinecurlearnmanos.growth.entity.GrowthWeeklyReview;
import com.onlinecurlearnmanos.growth.mapper.GrowthCycleDirectionMapper;
import com.onlinecurlearnmanos.growth.mapper.GrowthCycleMapper;
import com.onlinecurlearnmanos.growth.mapper.GrowthWeeklyReviewMapper;
import com.onlinecurlearnmanos.learning.dto.LearningRecordResponse;
import com.onlinecurlearnmanos.learning.entity.LearningGoal;
import com.onlinecurlearnmanos.learning.entity.LearningPlan;
import com.onlinecurlearnmanos.learning.entity.LearningPlanItem;
import com.onlinecurlearnmanos.learning.entity.LearningTask;
import com.onlinecurlearnmanos.learning.mapper.LearningGoalMapper;
import com.onlinecurlearnmanos.learning.mapper.LearningPlanMapper;
import com.onlinecurlearnmanos.learning.mapper.LearningPlanItemMapper;
import com.onlinecurlearnmanos.learning.mapper.LearningTaskMapper;
import com.onlinecurlearnmanos.learning.service.LearningRecordService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GrowthCycleService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final String ACTIVE = "ACTIVE";
    private static final String COMPLETED = "COMPLETED";
    private static final String ARCHIVED = "ARCHIVED";
    private static final String DEFAULT_TITLE = "当前成长周期";

    private final GrowthCycleMapper cycleMapper;
    private final GrowthCycleDirectionMapper directionMapper;
    private final GrowthWeeklyReviewMapper reviewMapper;
    private final LearningGoalMapper goalMapper;
    private final LearningTaskMapper taskMapper;
    private final LearningPlanMapper planMapper;
    private final LearningPlanItemMapper planItemMapper;
    private final LearningRecordService learningRecordService;
    private final ObjectMapper objectMapper;

    @Transactional
    public GrowthCycle ensureActiveCycle(Long userId) {
        GrowthCycle cycle = findActive(userId);
        if (cycle == null) {
            cycle = createDefaultCycle(userId);
        }
        assignOrphanRecords(userId, cycle.getId());
        archiveExtraActivePlans(userId, cycle.getId());
        return cycle;
    }

    public List<GrowthCycleResponse> list(Long userId) {
        ensureActiveCycle(userId);
        return cycleMapper.selectList(new LambdaQueryWrapper<GrowthCycle>()
                        .eq(GrowthCycle::getUserId, userId)
                        .orderByDesc(GrowthCycle::getStartDate)
                        .orderByDesc(GrowthCycle::getId))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public GrowthCycle getActive(Long userId) {
        return ensureActiveCycle(userId);
    }

    public GrowthCycle getOwned(Long userId, Long cycleId) {
        return getRequired(userId, cycleId);
    }

    @Transactional
    public GrowthCycleResponse create(Long userId, GrowthCycleCreateRequest request) {
        if (findActive(userId) != null) {
            throw new BusinessException(ErrorCode.CONFLICT, "请先结束当前成长周期");
        }
        if (!request.endDate().isAfter(request.startDate())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "结束日期必须晚于开始日期");
        }
        GrowthCycle cycle = new GrowthCycle();
        cycle.setUserId(userId);
        cycle.setTitle(request.title().trim());
        cycle.setStartDate(request.startDate());
        cycle.setEndDate(request.endDate());
        cycle.setStatus(ACTIVE);
        cycleMapper.insert(cycle);
        copyCarryOver(userId, cycle.getId(), request.carryOverGoalIds(), request.carryOverTaskIds());
        return toResponse(cycle);
    }

    @Transactional
    public GrowthCycleResponse complete(Long userId, Long cycleId) {
        GrowthCycle cycle = getRequired(userId, cycleId);
        if (!ACTIVE.equals(cycle.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "只有进行中的周期可以结束");
        }
        cycle.setStatus(COMPLETED);
        cycle.setCompletedAt(LocalDateTime.now());
        cycleMapper.updateById(cycle);
        return toResponse(cycle);
    }

    @Transactional
    public GrowthCycleResponse archive(Long userId, Long cycleId) {
        GrowthCycle cycle = getRequired(userId, cycleId);
        if (!COMPLETED.equals(cycle.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "只有已完成周期可以归档");
        }
        cycle.setStatus(ARCHIVED);
        cycle.setArchivedAt(LocalDateTime.now());
        cycleMapper.updateById(cycle);
        return toResponse(cycle);
    }

    public GrowthDirectionResponse direction(Long userId, Long cycleId) {
        getRequired(userId, cycleId);
        GrowthCycleDirection direction = directionMapper.selectById(cycleId);
        if (direction == null) {
            return null;
        }
        return new GrowthDirectionResponse(
                cycleId,
                direction.getSourceAnalysisId(),
                direction.getDirectionSummary(),
                direction.getSnapshotJson(),
                direction.getUpdatedAt()
        );
    }

    @Transactional
    public GrowthDirectionResponse saveDirection(
            Long userId,
            Long cycleId,
            Long sourceAnalysisId,
            String summary,
            String snapshotJson
    ) {
        GrowthCycle cycle = getRequired(userId, cycleId);
        requireActive(cycle);
        GrowthCycleDirection direction = directionMapper.selectById(cycleId);
        if (direction == null) {
            direction = new GrowthCycleDirection();
            direction.setCycleId(cycleId);
            direction.setSourceAnalysisId(sourceAnalysisId);
            direction.setDirectionSummary(summary);
            direction.setSnapshotJson(snapshotJson);
            directionMapper.insert(direction);
        } else {
            direction.setSourceAnalysisId(sourceAnalysisId);
            direction.setDirectionSummary(summary);
            direction.setSnapshotJson(snapshotJson);
            directionMapper.updateById(direction);
        }
        return direction(userId, cycleId);
    }

    public List<GrowthReviewResponse> reviews(Long userId, Long cycleId) {
        getRequired(userId, cycleId);
        return reviewMapper.selectList(new LambdaQueryWrapper<GrowthWeeklyReview>()
                        .eq(GrowthWeeklyReview::getCycleId, cycleId)
                        .orderByDesc(GrowthWeeklyReview::getWeekStart))
                .stream()
                .map(this::toReviewResponse)
                .toList();
    }

    @Transactional
    public GrowthReviewResponse getOrCreateCurrentReview(Long userId, Long cycleId) {
        GrowthCycle cycle = getRequired(userId, cycleId);
        LocalDate weekStart = LocalDate.now(ZONE)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return getOrCreateReview(userId, cycle, weekStart, null);
    }

    @Transactional
    public GrowthReviewResponse saveReview(
            Long userId,
            Long cycleId,
            LocalDate weekStart,
            String note
    ) {
        GrowthCycle cycle = getRequired(userId, cycleId);
        requireActive(cycle);
        LocalDate normalized = weekStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return getOrCreateReview(userId, cycle, normalized, note);
    }

    private GrowthReviewResponse getOrCreateReview(
            Long userId,
            GrowthCycle cycle,
            LocalDate weekStart,
            String note
    ) {
        GrowthWeeklyReview review = reviewMapper.selectOne(
                new LambdaQueryWrapper<GrowthWeeklyReview>()
                        .eq(GrowthWeeklyReview::getCycleId, cycle.getId())
                        .eq(GrowthWeeklyReview::getWeekStart, weekStart)
                        .last("LIMIT 1")
        );
        if (review == null) {
            review = new GrowthWeeklyReview();
            review.setCycleId(cycle.getId());
            review.setWeekStart(weekStart);
            review.setWeekEnd(weekStart.plusDays(6));
            review.setEvidenceJson(buildEvidence(userId, cycle.getId(), weekStart, weekStart.plusDays(6)));
            review.setNote(normalizeNote(note));
            reviewMapper.insert(review);
        } else {
            review.setEvidenceJson(buildEvidence(userId, cycle.getId(), weekStart, weekStart.plusDays(6)));
            if (note != null) {
                review.setNote(normalizeNote(note));
            }
            reviewMapper.updateById(review);
        }
        return toReviewResponse(review);
    }

    private String buildEvidence(
            Long userId,
            Long cycleId,
            LocalDate weekStart,
            LocalDate weekEnd
    ) {
        LocalDateTime from = weekStart.atStartOfDay();
        LocalDateTime to = weekEnd.plusDays(1).atStartOfDay();
        List<LearningGoal> goals = goalMapper.selectList(new LambdaQueryWrapper<LearningGoal>()
                .eq(LearningGoal::getUserId, userId)
                .eq(LearningGoal::getCycleId, cycleId)
                .eq(LearningGoal::getStatus, COMPLETED)
                .ge(LearningGoal::getCompletedAt, from)
                .lt(LearningGoal::getCompletedAt, to));
        List<LearningTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<LearningTask>()
                .eq(LearningTask::getUserId, userId)
                .eq(LearningTask::getCycleId, cycleId)
                .eq(LearningTask::getStatus, COMPLETED)
                .ge(LearningTask::getCompletedAt, from)
                .lt(LearningTask::getCompletedAt, to));
        List<LearningRecordResponse> records = learningRecordService.list(userId, from, to);
        List<Long> planIds = planMapper.selectList(new LambdaQueryWrapper<LearningPlan>()
                        .eq(LearningPlan::getUserId, userId)
                        .eq(LearningPlan::getCycleId, cycleId))
                .stream()
                .map(LearningPlan::getId)
                .toList();
        List<LearningPlanItem> planItems = planIds.isEmpty()
                ? List.of()
                : planItemMapper.selectList(new LambdaQueryWrapper<LearningPlanItem>()
                        .in(LearningPlanItem::getPlanId, planIds)
                        .eq(LearningPlanItem::getStatus, "DONE")
                        .ge(LearningPlanItem::getUpdatedAt, from)
                        .lt(LearningPlanItem::getUpdatedAt, to));
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("weekStart", weekStart);
        evidence.put("weekEnd", weekEnd);
        evidence.put("completedGoals", goals.stream().map(LearningGoal::getTitle).toList());
        evidence.put("completedTasks", tasks.stream().map(LearningTask::getTitle).toList());
        evidence.put("completedPlanItems", planItems.stream().map(LearningPlanItem::getTitle).toList());
        evidence.put("learningRecordCount", records.size());
        evidence.put("scoredRecordCount", records.stream().filter(item -> item.score() != null).count());
        evidence.put("averageScore", records.stream()
                .filter(item -> item.score() != null)
                .mapToDouble(item -> item.score().doubleValue())
                .average()
                .orElse(0));
        try {
            return objectMapper.writeValueAsString(evidence);
        } catch (Exception exception) {
            return "{}";
        }
    }

    private void copyCarryOver(
            Long userId,
            Long newCycleId,
            List<Long> goalIds,
            List<Long> taskIds
    ) {
        Map<Long, Long> goalMapping = new LinkedHashMap<>();
        if (goalIds != null && !goalIds.isEmpty()) {
            List<LearningGoal> goals = goalMapper.selectList(new LambdaQueryWrapper<LearningGoal>()
                    .eq(LearningGoal::getUserId, userId)
                    .in(LearningGoal::getId, goalIds));
            for (LearningGoal source : goals) {
                LearningGoal copy = new LearningGoal();
                copy.setUserId(userId);
                copy.setCycleId(newCycleId);
                copy.setSourceGoalId(source.getId());
                copy.setSubjectId(source.getSubjectId());
                copy.setTitle(source.getTitle());
                copy.setDescription(source.getDescription());
                copy.setTargetDate(source.getTargetDate());
                copy.setPriority(source.getPriority());
                copy.setStatus(ACTIVE);
                goalMapper.insert(copy);
                goalMapping.put(source.getId(), copy.getId());
            }
        }
        if (taskIds != null && !taskIds.isEmpty()) {
            List<LearningTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<LearningTask>()
                    .eq(LearningTask::getUserId, userId)
                    .in(LearningTask::getId, taskIds));
            for (LearningTask source : tasks) {
                LearningTask copy = new LearningTask();
                copy.setUserId(userId);
                copy.setCycleId(newCycleId);
                copy.setSourceTaskId(source.getId());
                copy.setGoalId(source.getGoalId() == null ? null : goalMapping.get(source.getGoalId()));
                copy.setTitle(source.getTitle());
                copy.setDescription(source.getDescription());
                copy.setCategory(source.getCategory());
                copy.setDueAt(source.getDueAt());
                copy.setPriority(source.getPriority());
                copy.setStatus("TODO");
                taskMapper.insert(copy);
            }
        }
    }

    private GrowthCycle createDefaultCycle(Long userId) {
        LocalDate today = LocalDate.now(ZONE);
        GrowthCycle cycle = new GrowthCycle();
        cycle.setUserId(userId);
        cycle.setTitle(DEFAULT_TITLE);
        cycle.setStartDate(defaultSemesterStart(today));
        cycle.setEndDate(defaultSemesterEnd(today));
        cycle.setStatus(ACTIVE);
        try {
            cycleMapper.insert(cycle);
            return cycle;
        } catch (DataIntegrityViolationException exception) {
            GrowthCycle raced = findActive(userId);
            if (raced == null) {
                throw exception;
            }
            return raced;
        }
    }

    private void assignOrphanRecords(Long userId, Long cycleId) {
        goalMapper.update(null, new LambdaUpdateWrapper<LearningGoal>()
                .eq(LearningGoal::getUserId, userId)
                .isNull(LearningGoal::getCycleId)
                .set(LearningGoal::getCycleId, cycleId));
        taskMapper.update(null, new LambdaUpdateWrapper<LearningTask>()
                .eq(LearningTask::getUserId, userId)
                .isNull(LearningTask::getCycleId)
                .set(LearningTask::getCycleId, cycleId));
        planMapper.update(null, new LambdaUpdateWrapper<LearningPlan>()
                .eq(LearningPlan::getUserId, userId)
                .isNull(LearningPlan::getCycleId)
                .set(LearningPlan::getCycleId, cycleId));
    }

    private void archiveExtraActivePlans(Long userId, Long cycleId) {
        List<LearningPlan> activePlans = planMapper.selectList(new LambdaQueryWrapper<LearningPlan>()
                .eq(LearningPlan::getUserId, userId)
                .eq(LearningPlan::getCycleId, cycleId)
                .eq(LearningPlan::getStatus, "ACTIVE")
                .orderByDesc(LearningPlan::getUpdatedAt)
                .orderByDesc(LearningPlan::getId));
        for (int index = 1; index < activePlans.size(); index++) {
            planMapper.update(null, new LambdaUpdateWrapper<LearningPlan>()
                    .eq(LearningPlan::getId, activePlans.get(index).getId())
                    .set(LearningPlan::getStatus, ARCHIVED));
        }
    }

    private GrowthCycle findActive(Long userId) {
        return cycleMapper.selectOne(new LambdaQueryWrapper<GrowthCycle>()
                .eq(GrowthCycle::getUserId, userId)
                .eq(GrowthCycle::getStatus, ACTIVE)
                .last("LIMIT 1"));
    }

    private GrowthCycle getRequired(Long userId, Long cycleId) {
        GrowthCycle cycle = cycleMapper.selectOne(new LambdaQueryWrapper<GrowthCycle>()
                .eq(GrowthCycle::getId, cycleId)
                .eq(GrowthCycle::getUserId, userId)
                .last("LIMIT 1"));
        if (cycle == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "成长周期不存在");
        }
        return cycle;
    }

    private void requireActive(GrowthCycle cycle) {
        if (!ACTIVE.equals(cycle.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "历史周期只读");
        }
    }

    private LocalDate defaultSemesterStart(LocalDate today) {
        int month = today.getMonthValue();
        if (month >= 2 && month <= 7) {
            return today.withMonth(2).withDayOfMonth(1);
        }
        if (month >= 8) {
            return today.withMonth(9).withDayOfMonth(1);
        }
        return today.minusYears(1).withMonth(9).withDayOfMonth(1);
    }

    private LocalDate defaultSemesterEnd(LocalDate today) {
        int month = today.getMonthValue();
        if (month >= 2 && month <= 7) {
            return today.withMonth(7).withDayOfMonth(31);
        }
        if (month >= 8) {
            return today.plusYears(1).withMonth(1).withDayOfMonth(31);
        }
        return today.withMonth(1).withDayOfMonth(31);
    }

    private String normalizeNote(String note) {
        return StringUtils.hasText(note) ? note.trim() : null;
    }

    private GrowthCycleResponse toResponse(GrowthCycle cycle) {
        return new GrowthCycleResponse(
                cycle.getId(),
                cycle.getTitle(),
                cycle.getStartDate(),
                cycle.getEndDate(),
                cycle.getStatus(),
                cycle.getCompletedAt(),
                cycle.getArchivedAt(),
                cycle.getCreatedAt()
        );
    }

    private GrowthReviewResponse toReviewResponse(GrowthWeeklyReview review) {
        return new GrowthReviewResponse(
                review.getId(),
                review.getCycleId(),
                review.getWeekStart(),
                review.getWeekEnd(),
                review.getEvidenceJson(),
                review.getNote(),
                review.getUpdatedAt()
        );
    }
}
