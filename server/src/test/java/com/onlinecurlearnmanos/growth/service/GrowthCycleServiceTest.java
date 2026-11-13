package com.onlinecurlearnmanos.growth.service;

import com.onlinecurlearnmanos.common.exception.BusinessException;
import com.onlinecurlearnmanos.growth.dto.GrowthCycleCreateRequest;
import com.onlinecurlearnmanos.growth.entity.GrowthCycle;
import com.onlinecurlearnmanos.growth.mapper.GrowthCycleDirectionMapper;
import com.onlinecurlearnmanos.growth.mapper.GrowthCycleMapper;
import com.onlinecurlearnmanos.growth.mapper.GrowthWeeklyReviewMapper;
import com.onlinecurlearnmanos.learning.mapper.LearningGoalMapper;
import com.onlinecurlearnmanos.learning.mapper.LearningPlanItemMapper;
import com.onlinecurlearnmanos.learning.mapper.LearningPlanMapper;
import com.onlinecurlearnmanos.learning.mapper.LearningTaskMapper;
import com.onlinecurlearnmanos.learning.service.LearningRecordService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GrowthCycleServiceTest {

    @Mock private GrowthCycleMapper cycleMapper;
    @Mock private GrowthCycleDirectionMapper directionMapper;
    @Mock private GrowthWeeklyReviewMapper reviewMapper;
    @Mock private LearningGoalMapper goalMapper;
    @Mock private LearningTaskMapper taskMapper;
    @Mock private LearningPlanMapper planMapper;
    @Mock private LearningPlanItemMapper planItemMapper;
    @Mock private LearningRecordService learningRecordService;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks private GrowthCycleService service;

    @Test
    void rejectsSecondActiveCycle() {
        GrowthCycle active = new GrowthCycle();
        active.setId(1L);
        active.setUserId(7L);
        active.setStatus("ACTIVE");
        given(cycleMapper.selectOne(any())).willReturn(active);

        assertThatThrownBy(() -> service.create(
                7L,
                new GrowthCycleCreateRequest(
                        "下一周期",
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2027, 1, 31),
                        null,
                        null
                )
        )).isInstanceOf(BusinessException.class)
                .hasMessageContaining("先结束");
    }

    @Test
    void rejectsCycleWithInvalidDateRange() {
        given(cycleMapper.selectOne(any())).willReturn(null);

        assertThatThrownBy(() -> service.create(
                7L,
                new GrowthCycleCreateRequest(
                        "错误周期",
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 9, 1),
                        null,
                        null
                )
        )).isInstanceOf(BusinessException.class)
                .hasMessageContaining("结束日期");
    }

    @Test
    void completesOwnedActiveCycle() {
        GrowthCycle active = new GrowthCycle();
        active.setId(1L);
        active.setUserId(7L);
        active.setStatus("ACTIVE");
        given(cycleMapper.selectOne(any())).willReturn(active);

        var result = service.complete(7L, 1L);

        assertThat(result.status()).isEqualTo("COMPLETED");
        verify(cycleMapper).updateById(active);
    }
}
