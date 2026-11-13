package com.onlinecurlearnmanos.point.service;

import com.onlinecurlearnmanos.common.exception.BusinessException;
import com.onlinecurlearnmanos.point.entity.DailyCheckin;
import com.onlinecurlearnmanos.point.mapper.DailyCheckinMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CheckinServiceImplTest {

    @Mock
    private DailyCheckinMapper checkinMapper;

    @Mock
    private PointService pointService;

    @InjectMocks
    private CheckinServiceImpl checkinService;

    @Test
    void awardsWeeklyBonusOnSeventhConsecutiveDay() {
        DailyCheckin previous = new DailyCheckin();
        previous.setCheckinDate(LocalDate.now().minusDays(1));
        previous.setStreakDays(6);
        given(checkinMapper.selectOne(any())).willReturn(null, previous);
        given(checkinMapper.insert(any(DailyCheckin.class))).willReturn(1);

        var result = checkinService.checkin(3L);

        assertThat(result.earnedPoints()).isEqualTo(40);
        verify(pointService).award(
                eq(3L),
                eq(40),
                eq("DAILY_CHECKIN"),
                anyString(),
                eq("CHECKIN"),
                any(),
                anyString()
        );
    }

    @Test
    void rejectsDuplicateCheckinWithoutAwardingPoints() {
        DailyCheckin today = new DailyCheckin();
        today.setCheckinDate(LocalDate.now());
        given(checkinMapper.selectOne(any())).willReturn(today);

        assertThatThrownBy(() -> checkinService.checkin(3L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已经签到");
        verify(pointService, never()).award(anyLong(), anyInt(), anyString(), anyString(), anyString(), any(), anyString());
    }
}
