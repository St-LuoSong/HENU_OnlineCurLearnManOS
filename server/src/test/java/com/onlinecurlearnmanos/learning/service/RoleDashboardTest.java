package com.onlinecurlearnmanos.learning.service;

import com.onlinecurlearnmanos.cloudclass.dto.CloudClassResponse;
import com.onlinecurlearnmanos.cloudclass.dto.CloudClassStatisticsResponse;
import com.onlinecurlearnmanos.cloudclass.dto.TeacherStudentResponse;
import com.onlinecurlearnmanos.cloudclass.service.CloudClassService;
import com.onlinecurlearnmanos.learning.dto.DashboardOverviewResponse;
import com.onlinecurlearnmanos.learning.dto.InteractionBreakdownResponse;
import com.onlinecurlearnmanos.learning.dto.InteractionOverviewResponse;
import com.onlinecurlearnmanos.learning.dto.InteractionTrendPointResponse;
import com.onlinecurlearnmanos.learning.dto.RecentScoreResponse;
import com.onlinecurlearnmanos.learning.dto.ProgressResponse;
import com.onlinecurlearnmanos.learning.entity.LearningGoal;
import com.onlinecurlearnmanos.learning.entity.LearningTask;
import com.onlinecurlearnmanos.learning.entity.StudentProgress;
import com.onlinecurlearnmanos.learning.mapper.LearningGoalMapper;
import com.onlinecurlearnmanos.learning.mapper.LearningTaskMapper;
import com.onlinecurlearnmanos.learning.mapper.StudentProgressMapper;
import com.onlinecurlearnmanos.practice.entity.PracticeSet;
import com.onlinecurlearnmanos.practice.mapper.PracticeSetMapper;
import com.onlinecurlearnmanos.support.MybatisLambdaCache;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class RoleDashboardTest {

    @Mock
    private LearningGoalMapper goalMapper;

    @Mock
    private LearningTaskMapper taskMapper;

    @Mock
    private StudentProgressMapper progressMapper;

    @Mock
    private CloudClassService cloudClassService;

    @Mock
    private PracticeSetMapper practiceSetMapper;

    @Mock
    private StudentActivityService studentActivityService;

    @Mock
    private com.onlinecurlearnmanos.content.mapper.SubjectMapper subjectMapper;

    @Mock
    private ProgressService progressService;

    @InjectMocks
    private DashboardServiceImpl service;

    @BeforeAll
    static void registerLambdaCache() {
        MybatisLambdaCache.register(
                LearningGoal.class,
                LearningTask.class,
                StudentProgress.class
        );
    }

    @Test
    void studentOverviewIncludesTeacherAndClassName() {
        given(goalMapper.selectCount(any())).willReturn(2L, 1L);
        given(taskMapper.selectCount(any())).willReturn(3L, 4L);
        given(progressMapper.selectCount(any())).willReturn(5L);
        given(cloudClassService.listLearning(31L)).willReturn(List.of(
                new CloudClassResponse(
                        11L, 10L, "高等数学", "高等数学 A 班", null,
                        21L, "李老师", "ABC234", "2026-2027", "第一学期",
                        60, "ACTIVE", true, 40, 6, 3, LocalDateTime.now(),
                        1, 0, 2, 82.5
                )
        ));

        LocalDateTime todayNoon = LocalDate.now().atTime(12, 0);
        given(progressService.list(31L)).willReturn(List.of(
                new ProgressResponse(
                        51L, 10L, "高等数学", 100L, "导数", "A",
                        3, 3, true, true, todayNoon
                ),
                new ProgressResponse(
                        52L, 10L, "高等数学", 101L, "极限", "C",
                        2, 1, false, false, todayNoon
                )
        ));
        PracticeSet unjoinedPractice = new PracticeSet();
        unjoinedPractice.setSubjectId(99L);
        unjoinedPractice.setSubmittedAt(todayNoon);
        given(practiceSetMapper.selectList(any())).willReturn(List.of(unjoinedPractice));
        InteractionBreakdownResponse breakdown = new InteractionBreakdownResponse(4, 3, 2, 1, 1);
        given(studentActivityService.overview(31L)).willReturn(new InteractionOverviewResponse(
                11,
                3,
                4,
                80,
                breakdown,
                List.of(new InteractionTrendPointResponse(todayNoon.toLocalDate(), breakdown))
        ));
        given(studentActivityService.recentScores(31L)).willReturn(List.of(
                new RecentScoreResponse(
                        "LEARNING_RECORD", 41L, "导数", "高等数学",
                        88.0, 100.0, todayNoon
                )
        ));

        DashboardOverviewResponse result = service.overview(
                31L,
                List.of("STUDENT")
        );

        assertThat(result.role()).isEqualTo("STUDENT");
        assertThat(result.teacher()).isNull();
        assertThat(result.student().summary().activeGoals()).isEqualTo(2);
        assertThat(result.student().teacherNames()).containsExactly("李老师");
        assertThat(result.student().classNames()).containsExactly("高等数学 A 班");
        assertThat(result.student().interaction().totalThisWeek()).isEqualTo(11);
        assertThat(result.student().interaction().activeDaysThisWeek()).isEqualTo(4);
        assertThat(result.student().myCourses()).singleElement().satisfies(item -> {
            assertThat(item.subjectName()).isEqualTo("高等数学");
            assertThat(item.totalPoints()).isEqualTo(2);
            assertThat(item.masteredPoints()).isEqualTo(1);
            assertThat(item.masteryPercent()).isEqualTo(50);
        });
        assertThat(result.student().recentScores()).singleElement()
                .satisfies(item -> assertThat(item.score()).isEqualTo(88.0));
    }

    @Test
    void teacherOverviewAggregatesClassesStudentsAndSubmissions() {
        given(cloudClassService.listTeaching(21L)).willReturn(List.of(
                new CloudClassResponse(
                        11L, 10L, "高等数学", "高等数学 A 班", null,
                        21L, "李老师", "ABC234", "2026-2027", "第一学期",
                        60, "ACTIVE", true, 40, 6, 3, LocalDateTime.now(),
                        0, 0, 0, null
                )
        ));
        given(cloudClassService.teacherStudents(21L)).willReturn(List.of(
                new TeacherStudentResponse(
                        11L, "高等数学 A 班", 31L, "2024001", "张三",
                        "ACTIVE", 6, 4, 2, 1, 3, 78.0
                )
        ));
        given(cloudClassService.statistics(21L, 11L)).willReturn(
                new CloudClassStatisticsResponse(40, 6, 12, 8, 80.5)
        );

        DashboardOverviewResponse result = service.overview(
                21L,
                List.of("TEACHER")
        );

        assertThat(result.role()).isEqualTo("TEACHER");
        assertThat(result.student()).isNull();
        assertThat(result.teacher().classCount()).isEqualTo(1);
        assertThat(result.teacher().studentCount()).isEqualTo(1);
        assertThat(result.teacher().pendingSubmissionCount()).isEqualTo(4);
        assertThat(result.teacher().averageScore()).isEqualTo(80.5);
        assertThat(result.teacher().classes()).hasSize(1);
        assertThat(result.teacher().riskStudents()).singleElement().satisfies(item -> {
            assertThat(item.displayName()).isEqualTo("张三");
            assertThat(item.className()).isEqualTo("高等数学 A 班");
            assertThat(item.overdueTaskCount()).isEqualTo(1);
            assertThat(item.pendingTaskCount()).isEqualTo(2);
        });
    }
}
