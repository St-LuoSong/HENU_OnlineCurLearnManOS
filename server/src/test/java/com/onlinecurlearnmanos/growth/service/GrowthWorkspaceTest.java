package com.onlinecurlearnmanos.growth.service;

import com.onlinecurlearnmanos.career.dto.CareerAnalysisResponse;
import com.onlinecurlearnmanos.career.dto.CareerInsightResponse;
import com.onlinecurlearnmanos.career.dto.CareerProfileResponse;
import com.onlinecurlearnmanos.career.service.CareerAnalysisService;
import com.onlinecurlearnmanos.career.service.CareerProfileService;
import com.onlinecurlearnmanos.growth.dto.GrowthWorkspaceResponse;
import com.onlinecurlearnmanos.growth.dto.GrowthCycleResponse;
import com.onlinecurlearnmanos.growth.entity.GrowthCycle;
import com.onlinecurlearnmanos.learning.dto.GoalResponse;
import com.onlinecurlearnmanos.learning.dto.LearningPlanItemResponse;
import com.onlinecurlearnmanos.learning.dto.LearningPlanResponse;
import com.onlinecurlearnmanos.learning.dto.TaskResponse;
import com.onlinecurlearnmanos.learning.service.GoalService;
import com.onlinecurlearnmanos.learning.service.LearningPlanService;
import com.onlinecurlearnmanos.learning.service.TaskService;
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
class GrowthWorkspaceTest {

    @Mock
    private GoalService goalService;

    @Mock
    private TaskService taskService;

    @Mock
    private LearningPlanService learningPlanService;

    @Mock
    private CareerAnalysisService careerAnalysisService;

    @Mock
    private CareerProfileService careerProfileService;

    @Mock
    private GrowthCycleService growthCycleService;

    @InjectMocks
    private GrowthWorkspaceService service;

    @Test
    void buildsUnifiedSummaryTasksCalendarAndAdvice() {
        LocalDate today = LocalDate.now();
        GrowthCycle cycle = new GrowthCycle();
        cycle.setId(71L);
        cycle.setUserId(31L);
        cycle.setTitle("当前成长周期");
        cycle.setStartDate(today.minusDays(10));
        cycle.setEndDate(today.plusDays(100));
        cycle.setStatus("ACTIVE");
        given(growthCycleService.getActive(31L)).willReturn(cycle);
        given(growthCycleService.getOwned(31L, 71L)).willReturn(cycle);
        given(growthCycleService.direction(31L, 71L)).willReturn(null);
        given(growthCycleService.getOrCreateCurrentReview(31L, 71L)).willReturn(null);
        given(growthCycleService.reviews(31L, 71L)).willReturn(List.of());
        given(growthCycleService.list(31L)).willReturn(List.of(new GrowthCycleResponse(
                71L, "当前成长周期", cycle.getStartDate(), cycle.getEndDate(),
                "ACTIVE", null, null, null
        )));
        given(goalService.list(31L)).willReturn(List.of(
                new GoalResponse(
                        1L, 71L, 10L, "高等数学", "补齐导数基础", null,
                        today.plusDays(5), "HIGH", "ACTIVE", null, 2, 1, 50
                ),
                new GoalResponse(
                        2L, 71L, 11L, "线性代数", "完成矩阵复习", null,
                        today.plusDays(3), "MEDIUM", "COMPLETED", null, 1, 1, 100
                )
        ));
        given(taskService.list(31L, null, null)).willReturn(List.of(
                new TaskResponse(
                        11L, 71L, 1L, "补齐导数基础", "整理错题", null, "REVIEW",
                        LocalDateTime.now().minusDays(1), "HIGH", "TODO", null
                ),
                new TaskResponse(
                        12L, 71L, 1L, "补齐导数基础", "完成一章练习", null, "PRACTICE",
                        // 必须落在「今天尚未到期」的区间内，否则 20:00 之后运行会把这条也算成逾期，
                        // 让 overdueTasks 断言随运行时刻漂移。
                        today.atTime(23, 59, 59, 999_999_999), "MEDIUM", "TODO", null
                ),
                new TaskResponse(
                        13L, 71L, null, null, "预习矩阵", null, "PREVIEW",
                        LocalDate.now().plusDays(3).atTime(19, 0), "LOW", "IN_PROGRESS", null
                ),
                new TaskResponse(
                        14L, 71L, null, null, "已完成任务", null, null,
                        LocalDate.now().plusDays(1).atTime(10, 0), "LOW", "COMPLETED", null
                )
        ));
        given(learningPlanService.list(31L)).willReturn(List.of(new LearningPlanResponse(
                21L,
                71L,
                "四周复习计划",
                "按周拆分",
                "ACTIVE",
                "deepseek",
                "deepseek-chat",
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now(),
                4,
                1,
                25,
                List.of(
                        new LearningPlanItemResponse(
                                31L, "完成极限复习", null,
                                today.plusDays(1), today.plusDays(2),
                                null, null, null, null, "TODO", 1
                        ),
                        new LearningPlanItemResponse(
                                32L, "完成导数练习", null,
                                today.plusDays(3), today.plusDays(4),
                                null, null, null, null, "TODO", 2
                        )
                )
        )));
        given(careerAnalysisService.list(31L)).willReturn(List.of(new CareerAnalysisResponse(
                41L,
                "数据分析方向分析",
                "结合兴趣与课程",
                "ACTIVE",
                "deepseek",
                "deepseek-chat",
                List.of("学习记录", "学习目标"),
                LocalDateTime.now().minusHours(1),
                List.of(new CareerInsightResponse(
                        51L,
                        "ACTION",
                        "完成 Python 数据分析练习",
                        "完成一个小型数据集分析",
                        "兴趣与课程匹配",
                        today.plusDays(2),
                        null,
                        null,
                        80,
                        1
                ))
        )));
        given(careerProfileService.get(31L)).willReturn(new CareerProfileResponse(
                61L, false, "v1", null, null,
                true, true, true,
                "计算机科学与技术", "大二", 8,
                List.of("数据分析"), null, null, null
        ));

        GrowthWorkspaceResponse result = service.workspace(31L);

        assertThat(result.summary().activeGoals()).isEqualTo(1);
        assertThat(result.summary().completedGoals()).isEqualTo(1);
        assertThat(result.summary().openTasks()).isEqualTo(3);
        assertThat(result.summary().overdueTasks()).isEqualTo(1);
        assertThat(result.summary().activePlans()).isEqualTo(1);
        assertThat(result.summary().careerConsentGranted()).isFalse();
        assertThat(result.todayTasks()).hasSize(2);
        assertThat(result.upcomingTasks()).hasSize(1);
        assertThat(result.calendarEvents())
                .extracting(event -> event.type())
                .contains("GOAL", "TASK", "PLAN_START", "PLAN_END", "CAREER_ACTION");
        assertThat(result.advice())
                .extracting(advice -> advice.code())
                .contains("OVERDUE_TASKS", "CAREER_CONSENT");
    }
}
