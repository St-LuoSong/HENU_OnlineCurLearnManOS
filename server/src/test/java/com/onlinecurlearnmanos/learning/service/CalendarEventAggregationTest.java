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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CalendarEventAggregationTest {

    @Mock
    private GoalService goalService;

    @Mock
    private TaskService taskService;

    @Mock
    private LearningPlanService learningPlanService;

    @Mock
    private CareerAnalysisService careerAnalysisService;

    @Mock
    private CloudClassService cloudClassService;

    @InjectMocks
    private CalendarEventService service;

    @Test
    void aggregatesGoalTaskPlanCareerAndClassEventsInRange() {
        LocalDate today = LocalDate.now();
        given(goalService.list(31L)).willReturn(List.of(new GoalResponse(
                1L, null, 10L, "高等数学", "补齐极限", null,
                today.plusDays(1), "HIGH", "ACTIVE", null, 1, 0, 0
        )));
        given(taskService.list(31L, null, null)).willReturn(List.of(new TaskResponse(
                11L, null, 1L, "补齐极限", "完成练习", null, "PRACTICE",
                today.plusDays(2).atTime(20, 0), "MEDIUM", "TODO", null
        )));
        given(learningPlanService.list(31L)).willReturn(List.of(new LearningPlanResponse(
                21L, null, "四周计划", null, "ACTIVE", null, null,
                LocalDateTime.now(), LocalDateTime.now(), 1, 0, 0,
                List.of(new LearningPlanItemResponse(
                        31L, "开始极限复习", null,
                        today.plusDays(3), null,
                        null, null, null, null, "TODO", 1
                ))
        )));
        given(careerAnalysisService.list(31L)).willReturn(List.of(new CareerAnalysisResponse(
                41L, "数据分析方向", null, "ACTIVE", null, null,
                List.of(), LocalDateTime.now(),
                List.of(new CareerInsightResponse(
                        51L, "ACTION", "完成 Python 练习", null, null,
                        today.plusDays(4), null, null, 80, 1
                ))
        )));
        given(cloudClassService.listLearning(31L)).willReturn(List.of(new CloudClassResponse(
                61L, 10L, "高等数学", "高等数学 A 班", null,
                21L, "李老师", "ABC234", "2026-2027", "第一学期",
                60, "ACTIVE", true, 40, 1, 0, LocalDateTime.now(),
                1, 0, 0, null
        )));
        given(cloudClassService.detail(31L, List.of("STUDENT"), 61L)).willReturn(
                new CloudClassDetailResponse(
                        new CloudClassResponse(
                                61L, 10L, "高等数学", "高等数学 A 班", null,
                                21L, "李老师", "ABC234", "2026-2027", "第一学期",
                        60, "ACTIVE", true, 40, 1, 0, LocalDateTime.now(),
                                1, 0, 0, null
                        ),
                        List.of(),
                        List.of(),
                        List.of(new CloudClassTaskResponse(
                                71L, "章节测试", null, "TEST", null, null,
                                today.plusDays(5).atTime(21, 0), 10, true, true,
                                100.0, "PUBLISHED", 0, null, null, null
                        ))
                )
        );

        List<CalendarEventResponse> events = service.events(
                31L,
                List.of("STUDENT"),
                today,
                today.plusDays(7)
        );

        assertThat(events).hasSize(5);
        assertThat(events).extracting(CalendarEventResponse::type)
                .containsExactly("GOAL", "TASK", "PLAN_START", "CAREER_ACTION", "CLASS_TASK");
        assertThat(events).isSortedAccordingTo(
                java.util.Comparator.comparing(CalendarEventResponse::date)
        );
    }
}
