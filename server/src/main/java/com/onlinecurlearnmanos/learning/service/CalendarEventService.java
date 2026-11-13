package com.onlinecurlearnmanos.learning.service;

import com.onlinecurlearnmanos.career.dto.CareerAnalysisResponse;
import com.onlinecurlearnmanos.career.dto.CareerInsightResponse;
import com.onlinecurlearnmanos.career.service.CareerAnalysisService;
import com.onlinecurlearnmanos.cloudclass.dto.CloudClassDetailResponse;
import com.onlinecurlearnmanos.cloudclass.dto.CloudClassResponse;
import com.onlinecurlearnmanos.cloudclass.dto.CloudClassTaskResponse;
import com.onlinecurlearnmanos.cloudclass.service.CloudClassService;
import com.onlinecurlearnmanos.learning.dto.CalendarEventResponse;
import com.onlinecurlearnmanos.learning.dto.GoalResponse;
import com.onlinecurlearnmanos.learning.dto.LearningPlanItemResponse;
import com.onlinecurlearnmanos.learning.dto.LearningPlanResponse;
import com.onlinecurlearnmanos.learning.dto.TaskResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CalendarEventService {

    private final GoalService goalService;
    private final TaskService taskService;
    private final LearningPlanService learningPlanService;
    private final CareerAnalysisService careerAnalysisService;
    private final CloudClassService cloudClassService;

    public List<CalendarEventResponse> events(
            Long userId,
            List<String> roles,
            LocalDate from,
            LocalDate to
    ) {
        LocalDate start = from == null ? LocalDate.now() : from;
        LocalDate end = to == null ? start.plusDays(30) : to;
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("结束日期不能早于开始日期");
        }

        List<CalendarEventResponse> events = new ArrayList<>();
        for (GoalResponse goal : goalService.list(userId)) {
            if (inRange(goal.targetDate(), start, end)) {
                events.add(new CalendarEventResponse(
                        "GOAL",
                        goal.id(),
                        goal.title(),
                        goal.targetDate(),
                        goal.status(),
                        goal.subjectName()
                ));
            }
        }
        for (TaskResponse task : taskService.list(userId, null, null)) {
            if (task.dueAt() != null) {
                LocalDate date = task.dueAt().toLocalDate();
                if (inRange(date, start, end)) {
                    events.add(new CalendarEventResponse(
                            "TASK",
                            task.id(),
                            task.title(),
                            date,
                            task.status(),
                            task.goalTitle()
                    ));
                }
            }
        }
        for (LearningPlanResponse plan : learningPlanService.list(userId)) {
            for (LearningPlanItemResponse item : plan.items()) {
                if (inRange(item.startDate(), start, end)) {
                    events.add(new CalendarEventResponse(
                            "PLAN_START",
                            item.id(),
                            item.title(),
                            item.startDate(),
                            item.status(),
                            plan.title()
                    ));
                }
                if (inRange(item.endDate(), start, end)) {
                    events.add(new CalendarEventResponse(
                            "PLAN_END",
                            item.id(),
                            item.title(),
                            item.endDate(),
                            item.status(),
                            plan.title()
                    ));
                }
            }
        }
        for (CareerAnalysisResponse analysis : careerAnalysisService.list(userId)) {
            for (CareerInsightResponse insight : analysis.insights()) {
                if (inRange(insight.targetDate(), start, end)) {
                    events.add(new CalendarEventResponse(
                            "CAREER_ACTION",
                            insight.id(),
                            insight.title(),
                            insight.targetDate(),
                            insight.convertedTaskId() != null || insight.convertedGoalId() != null
                                    ? "CONVERTED"
                                    : "TODO",
                            analysis.title()
                    ));
                }
            }
        }
        boolean teacher = roles != null && roles.contains("TEACHER");
        List<CloudClassResponse> classes = teacher
                ? cloudClassService.listTeaching(userId)
                : cloudClassService.listLearning(userId);
        for (CloudClassResponse cloudClass : classes) {
            CloudClassDetailResponse detail = cloudClassService.detail(
                    userId,
                    roles,
                    cloudClass.id()
            );
            for (CloudClassTaskResponse task : detail.tasks()) {
                if (task.dueAt() != null) {
                    LocalDate date = task.dueAt().toLocalDate();
                    if (inRange(date, start, end)) {
                        events.add(new CalendarEventResponse(
                                "CLASS_TASK",
                                task.id(),
                                task.title(),
                                date,
                                task.status(),
                                cloudClass.name()
                        ));
                    }
                }
            }
        }
        events.sort(Comparator
                .comparing(CalendarEventResponse::date)
                .thenComparing(CalendarEventResponse::type)
                .thenComparing(
                        CalendarEventResponse::sourceId,
                        Comparator.nullsLast(Comparator.naturalOrder())
                ));
        return events;
    }

    private boolean inRange(LocalDate date, LocalDate start, LocalDate end) {
        return date != null && !date.isBefore(start) && !date.isAfter(end);
    }
}
