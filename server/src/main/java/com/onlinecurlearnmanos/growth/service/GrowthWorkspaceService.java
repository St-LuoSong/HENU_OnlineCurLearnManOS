package com.onlinecurlearnmanos.growth.service;

import com.onlinecurlearnmanos.career.dto.CareerAnalysisResponse;
import com.onlinecurlearnmanos.career.dto.CareerProfileResponse;
import com.onlinecurlearnmanos.career.service.CareerAnalysisService;
import com.onlinecurlearnmanos.career.service.CareerProfileService;
import com.onlinecurlearnmanos.growth.dto.GrowthAdviceResponse;
import com.onlinecurlearnmanos.growth.dto.GrowthCalendarEventResponse;
import com.onlinecurlearnmanos.growth.dto.GrowthCycleResponse;
import com.onlinecurlearnmanos.growth.dto.GrowthDirectionResponse;
import com.onlinecurlearnmanos.growth.dto.GrowthReviewResponse;
import com.onlinecurlearnmanos.growth.dto.GrowthSummaryResponse;
import com.onlinecurlearnmanos.growth.dto.GrowthWorkspaceResponse;
import com.onlinecurlearnmanos.growth.entity.GrowthCycle;
import com.onlinecurlearnmanos.learning.dto.GoalResponse;
import com.onlinecurlearnmanos.learning.dto.LearningPlanItemResponse;
import com.onlinecurlearnmanos.learning.dto.LearningPlanResponse;
import com.onlinecurlearnmanos.learning.dto.TaskResponse;
import com.onlinecurlearnmanos.learning.service.GoalService;
import com.onlinecurlearnmanos.learning.service.LearningPlanService;
import com.onlinecurlearnmanos.learning.service.TaskService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GrowthWorkspaceService {

    private static final int TODAY_TASK_LIMIT = 10;
    private static final int UPCOMING_TASK_LIMIT = 12;
    private static final int RECENT_ANALYSIS_LIMIT = 5;
    private static final int ADVICE_LIMIT = 5;

    private final GoalService goalService;
    private final TaskService taskService;
    private final LearningPlanService learningPlanService;
    private final CareerAnalysisService careerAnalysisService;
    private final CareerProfileService careerProfileService;
    private final GrowthCycleService growthCycleService;
    private final ObjectMapper objectMapper;

    public GrowthWorkspaceResponse workspace(Long userId) {
        GrowthCycle cycle = growthCycleService.getActive(userId);
        return workspace(userId, cycle.getId());
    }

    public GrowthWorkspaceResponse workspace(Long userId, Long cycleId) {
        GrowthCycle cycle = growthCycleService.getOwned(userId, cycleId);
        List<GoalResponse> goals = goalService.list(userId).stream()
                .filter(goal -> cycleId.equals(goal.cycleId()))
                .toList();
        List<TaskResponse> tasks = taskService.list(userId, null, null).stream()
                .filter(task -> cycleId.equals(task.cycleId()))
                .toList();
        List<LearningPlanResponse> plans = learningPlanService.list(userId).stream()
                .filter(plan -> cycleId.equals(plan.cycleId()))
                .toList();
        List<CareerAnalysisResponse> analyses = careerAnalysisService.list(userId);
        CareerProfileResponse profile = careerProfileService.get(userId);

        LocalDate today = LocalDate.now();
        LocalDate endDate = today.plusDays(30);
        List<TaskResponse> openTasks = tasks.stream().filter(this::isOpenTask).toList();
        List<TaskResponse> todayTasks = openTasks.stream()
                .filter(task -> task.dueAt() != null && !task.dueAt().toLocalDate().isAfter(today))
                .sorted(taskComparator())
                .limit(TODAY_TASK_LIMIT)
                .toList();
        List<TaskResponse> upcomingTasks = openTasks.stream()
                .filter(task -> task.dueAt() != null
                        && task.dueAt().toLocalDate().isAfter(today)
                        && !task.dueAt().toLocalDate().isAfter(endDate))
                .sorted(taskComparator())
                .limit(UPCOMING_TASK_LIMIT)
                .toList();

        long overdueTasks = openTasks.stream()
                .filter(task -> task.dueAt() != null && task.dueAt().isBefore(LocalDateTime.now()))
                .count();
        long dueToday = openTasks.stream()
                .filter(task -> task.dueAt() != null && task.dueAt().toLocalDate().equals(today))
                .count();
        long dueThisWeek = openTasks.stream()
                .filter(task -> task.dueAt() != null
                        && !task.dueAt().isBefore(LocalDateTime.now())
                        && !task.dueAt().toLocalDate().isAfter(today.plusDays(7)))
                .count();
        long activeGoals = goals.stream().filter(goal -> "ACTIVE".equals(goal.status())).count();
        long completedGoals = goals.stream().filter(goal -> "COMPLETED".equals(goal.status())).count();
        long activePlans = plans.stream().filter(plan -> "ACTIVE".equals(plan.status())).count();
        long totalPlanItems = plans.stream().mapToLong(LearningPlanResponse::totalItemCount).sum();
        long completedPlanItems = plans.stream().mapToLong(LearningPlanResponse::completedItemCount).sum();

        GrowthSummaryResponse summary = new GrowthSummaryResponse(
                activeGoals,
                completedGoals,
                openTasks.size(),
                overdueTasks,
                dueToday,
                dueThisWeek,
                activePlans,
                totalPlanItems,
                completedPlanItems,
                analyses.size(),
                profile.consentGranted()
        );
        LearningPlanResponse currentPlan = plans.stream()
                .filter(plan -> "ACTIVE".equals(plan.status()))
                .findFirst()
                .orElse(null);
        GrowthDirectionResponse direction = growthCycleService.direction(userId, cycleId);
        GrowthReviewResponse currentReview = "ACTIVE".equals(cycle.getStatus())
                ? growthCycleService.getOrCreateCurrentReview(userId, cycleId)
                : null;
        List<GrowthReviewResponse> reviews = growthCycleService.reviews(userId, cycleId);
        String currentStage = currentStage(cycle, direction, goals, currentPlan, openTasks);
        List<GrowthCalendarEventResponse> calendarEvents = buildCalendarEvents(
                goals, tasks, plans, analyses, today, endDate
        );
        List<GrowthAdviceResponse> advice = buildAdvice(summary, goals, plans, analyses, direction);
        List<CareerAnalysisResponse> recentAnalyses = analyses.stream()
                .sorted(Comparator.comparing(
                        CareerAnalysisResponse::createdAt,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .limit(RECENT_ANALYSIS_LIMIT)
                .toList();
        List<GrowthCycleResponse> cycles = growthCycleService.list(userId);

        return new GrowthWorkspaceResponse(
                summary,
                toCycleResponse(cycle),
                cycles,
                direction,
                currentStage,
                currentPlan,
                currentReview,
                reviews,
                goals,
                plans,
                todayTasks,
                upcomingTasks,
                calendarEvents,
                recentAnalyses,
                advice
        );
    }

    public GrowthDirectionResponse refreshDirection(Long userId, Long cycleId) {
        GrowthCycle cycle = growthCycleService.getOwned(userId, cycleId);
        CareerProfileResponse profile = careerProfileService.get(userId);
        List<CareerAnalysisResponse> analyses = careerAnalysisService.list(userId);
        CareerAnalysisResponse latest = analyses.stream()
                .sorted(Comparator.comparing(
                        CareerAnalysisResponse::createdAt,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .findFirst()
                .orElse(null);
        String summary = latest != null && StringUtils.hasText(latest.summary())
                ? latest.summary()
                : StringUtils.hasText(profile.careerGoal())
                ? profile.careerGoal()
                : "已保存当前方向快照";
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("profile", profile);
        snapshot.put("analysis", latest);
        String snapshotJson;
        try {
            snapshotJson = objectMapper.writeValueAsString(snapshot);
        } catch (Exception exception) {
            snapshotJson = "{}";
        }
        return growthCycleService.saveDirection(
                userId,
                cycle.getId(),
                latest == null ? null : latest.id(),
                summary,
                snapshotJson
        );
    }

    private String currentStage(
            GrowthCycle cycle,
            GrowthDirectionResponse direction,
            List<GoalResponse> goals,
            LearningPlanResponse currentPlan,
            List<TaskResponse> openTasks
    ) {
        if (!"ACTIVE".equals(cycle.getStatus())) {
            return "REVIEW";
        }
        if (direction == null) return "DIRECTION";
        if (goals.isEmpty()) return "GOALS";
        if (currentPlan == null) return "PLAN";
        boolean planOpen = currentPlan.items().stream()
                .anyMatch(item -> !"DONE".equals(item.status()));
        return (!openTasks.isEmpty() || planOpen) ? "ACTION" : "REVIEW";
    }

    private GrowthCycleResponse toCycleResponse(GrowthCycle cycle) {
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

    private boolean isOpenTask(TaskResponse task) {
        return !"COMPLETED".equals(task.status()) && !"CANCELLED".equals(task.status());
    }

    private Comparator<TaskResponse> taskComparator() {
        return Comparator.comparing(
                TaskResponse::dueAt,
                Comparator.nullsLast(Comparator.naturalOrder())
        );
    }

    private List<GrowthCalendarEventResponse> buildCalendarEvents(
            List<GoalResponse> goals,
            List<TaskResponse> tasks,
            List<LearningPlanResponse> plans,
            List<CareerAnalysisResponse> analyses,
            LocalDate today,
            LocalDate endDate
    ) {
        List<GrowthCalendarEventResponse> events = new ArrayList<>();
        for (GoalResponse goal : goals) {
            if (inRange(goal.targetDate(), today, endDate)) {
                events.add(new GrowthCalendarEventResponse(
                        "GOAL", goal.id(), goal.title(), goal.targetDate(), goal.status(), "目标截止日期"
                ));
            }
        }
        for (TaskResponse task : tasks) {
            if (task.dueAt() != null) {
                LocalDate date = task.dueAt().toLocalDate();
                if (!date.isBefore(today) && !date.isAfter(endDate)) {
                    events.add(new GrowthCalendarEventResponse(
                            "TASK", task.id(), task.title(), date, task.status(),
                            task.goalTitle() == null ? "独立任务" : "目标：" + task.goalTitle()
                    ));
                }
            }
        }
        for (LearningPlanResponse plan : plans) {
            for (LearningPlanItemResponse item : plan.items()) {
                if (inRange(item.startDate(), today, endDate)) {
                    events.add(new GrowthCalendarEventResponse(
                            "PLAN_START", item.id(), item.title(), item.startDate(), item.status(),
                            "计划开始：" + plan.title()
                    ));
                }
                if (inRange(item.endDate(), today, endDate)) {
                    events.add(new GrowthCalendarEventResponse(
                            "PLAN_END", item.id(), item.title(), item.endDate(), item.status(),
                            "计划结束：" + plan.title()
                    ));
                }
            }
        }
        for (CareerAnalysisResponse analysis : analyses) {
            analysis.insights().stream()
                    .filter(insight -> inRange(insight.targetDate(), today, endDate))
                    .forEach(insight -> events.add(new GrowthCalendarEventResponse(
                            "CAREER_ACTION", insight.id(), insight.title(), insight.targetDate(),
                            insight.convertedTaskId() != null || insight.convertedGoalId() != null
                                    ? "CONVERTED" : "TODO",
                            "生涯行动：" + analysis.title()
                    )));
        }
        events.sort(Comparator
                .comparing(GrowthCalendarEventResponse::date)
                .thenComparing(GrowthCalendarEventResponse::type)
                .thenComparing(GrowthCalendarEventResponse::sourceId,
                        Comparator.nullsLast(Comparator.naturalOrder())));
        return events;
    }

    private boolean inRange(LocalDate date, LocalDate start, LocalDate end) {
        return date != null && !date.isBefore(start) && !date.isAfter(end);
    }

    private List<GrowthAdviceResponse> buildAdvice(
            GrowthSummaryResponse summary,
            List<GoalResponse> goals,
            List<LearningPlanResponse> plans,
            List<CareerAnalysisResponse> analyses,
            GrowthDirectionResponse direction
    ) {
        List<GrowthAdviceResponse> advice = new ArrayList<>();
        if (!summary.careerConsentGranted()) {
            advice.add(new GrowthAdviceResponse(
                    "CAREER_CONSENT", "完成生涯数据授权",
                    "授权后可结合学习记录、目标和成绩生成方向性建议，数据范围可随时撤销。",
                    "INFO"
            ));
        }
        if (direction == null) {
            advice.add(new GrowthAdviceResponse(
                    "NO_DIRECTION", "先确认成长方向",
                    "当前周期还没有方向快照，先确认专业、兴趣和本学期最重要的成长方向。",
                    "INFO"
            ));
        }
        if (summary.overdueTasks() > 0) {
            advice.add(new GrowthAdviceResponse(
                    "OVERDUE_TASKS", "先收敛逾期任务",
                    "当前有 " + summary.overdueTasks() + " 个任务已逾期，建议今天只保留 1-2 个最关键任务并重新排期。",
                    "WARNING"
            ));
        }
        if (summary.dueThisWeek() > 5) {
            advice.add(new GrowthAdviceResponse(
                    "WEEK_LOAD", "本周负荷偏高",
                    "未来 7 天有 " + summary.dueThisWeek() + " 个任务到期，建议拆分到每天并预留复盘时间。",
                    "WARNING"
            ));
        }
        if (summary.activeGoals() == 0) {
            advice.add(new GrowthAdviceResponse(
                    "NO_GOAL", "建立第一个学习目标",
                    "先选一门课程建立一个可衡量的目标，再把第一步写成任务。",
                    "INFO"
            ));
        } else {
            goals.stream().filter(goal -> "ACTIVE".equals(goal.status()))
                    .min(Comparator.comparingInt(GoalResponse::progress))
                    .filter(goal -> goal.progress() < 30)
                    .ifPresent(goal -> advice.add(new GrowthAdviceResponse(
                            "LOW_GOAL_PROGRESS", "目标进度需要最小行动",
                            "目标“" + goal.title() + "”当前完成度 " + goal.progress()
                                    + "%，建议补一个 30 分钟内可完成的任务。",
                            "INFO"
                    )));
        }
        if (summary.activePlans() == 0) {
            advice.add(new GrowthAdviceResponse(
                    "NO_ACTIVE_PLAN", "生成一版学习计划",
                    "当前没有进行中的学习计划，可以用 AI 生成一版按周拆分的计划草案。",
                    "INFO"
            ));
        } else if (summary.totalPlanItems() > 0
                && summary.completedPlanItems() * 100 / summary.totalPlanItems() < 40) {
            advice.add(new GrowthAdviceResponse(
                    "PLAN_PROGRESS", "计划完成度偏低",
                    "进行中计划完成 " + summary.completedPlanItems() + "/" + summary.totalPlanItems()
                            + " 个条目，建议先完成最早到期的一项。",
                    "INFO"
            ));
        }
        if (direction != null && analyses.isEmpty()) {
            advice.add(new GrowthAdviceResponse(
                    "CAREER_FIRST_ANALYSIS", "生成第一份生涯路径分析",
                    "填写专业、兴趣与每周可投入时间后，生成一份可转为目标或任务的路径建议。",
                    "INFO"
            ));
        }
        return advice.stream().limit(ADVICE_LIMIT).toList();
    }
}
