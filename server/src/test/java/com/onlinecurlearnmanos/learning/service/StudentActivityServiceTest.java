package com.onlinecurlearnmanos.learning.service;

import com.onlinecurlearnmanos.learning.mapper.ActivityCountRow;
import com.onlinecurlearnmanos.learning.mapper.LearningActivityMapper;
import com.onlinecurlearnmanos.practice.entity.PracticeSet;
import com.onlinecurlearnmanos.practice.mapper.PracticeSetMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class StudentActivityServiceTest {

    @Mock
    private LearningActivityMapper activityMapper;

    @Mock
    private PracticeSetMapper practiceSetMapper;

    @InjectMocks
    private StudentActivityService activityService;

    @Test
    void aggregatesSevenDayInteractionPulseAndAccuracy() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        ActivityCountRow ai = row(today, "AI_QUESTION", 3);
        ActivityCountRow practice = row(today.minusDays(1), "PRACTICE", 2);
        ActivityCountRow resource = row(today, "RESOURCE", 1);
        given(activityMapper.countActivities(eq(7L), any(), any()))
                .willReturn(List.of(ai, practice, resource));

        PracticeSet set = new PracticeSet();
        set.setCorrectCount(8);
        set.setQuestionCount(10);
        given(practiceSetMapper.selectList(any())).willReturn(List.of(set));

        var overview = activityService.overview(7L);

        assertThat(overview.trend()).hasSize(7);
        assertThat(overview.totalThisWeek()).isEqualTo(6);
        assertThat(overview.todayCount()).isEqualTo(4);
        assertThat(overview.activeDaysThisWeek()).isEqualTo(2);
        assertThat(overview.practiceAccuracy()).isEqualTo(80);
        assertThat(overview.breakdown().aiQuestions()).isEqualTo(3);
        assertThat(overview.breakdown().practiceAnswers()).isEqualTo(2);
        assertThat(overview.breakdown().resourceViews()).isEqualTo(1);
    }

    private ActivityCountRow row(LocalDate date, String type, long amount) {
        ActivityCountRow row = new ActivityCountRow();
        row.setActivityDate(date);
        row.setActivityType(type);
        row.setAmount(amount);
        return row;
    }
}
