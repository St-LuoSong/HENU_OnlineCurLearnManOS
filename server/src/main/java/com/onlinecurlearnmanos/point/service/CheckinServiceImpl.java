package com.onlinecurlearnmanos.point.service;

import com.onlinecurlearnmanos.common.error.ErrorCode;
import com.onlinecurlearnmanos.common.exception.BusinessException;
import com.onlinecurlearnmanos.point.dto.CheckinResultResponse;
import com.onlinecurlearnmanos.point.dto.CheckinStatusResponse;
import com.onlinecurlearnmanos.point.entity.DailyCheckin;
import com.onlinecurlearnmanos.point.mapper.DailyCheckinMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CheckinServiceImpl implements CheckinService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final int BASE_REWARD = 10;
    private static final int WEEK_BONUS = 30;

    private final DailyCheckinMapper checkinMapper;
    private final PointService pointService;

    @Override
    public CheckinStatusResponse status(Long userId) {
        LocalDate today = LocalDate.now(ZONE);
        DailyCheckin latest = latest(userId);
        boolean checkedToday = latest != null && today.equals(latest.getCheckinDate());
        int streak = latest == null ? 0 : latest.getStreakDays();
        if (latest != null
                && !today.equals(latest.getCheckinDate())
                && !today.minusDays(1).equals(latest.getCheckinDate())) {
            streak = 0;
        }
        int currentStreak = checkedToday ? streak : Math.max(streak, 0);
        int todayReward = checkedToday && latest != null
                ? latest.getRewardPoints()
                : rewardFor(checkedToday ? currentStreak : currentStreak + 1);
        long total = checkinMapper.selectCount(new LambdaQueryWrapper<DailyCheckin>()
                .eq(DailyCheckin::getUserId, userId));
        List<LocalDate> recent = checkinMapper.selectList(new LambdaQueryWrapper<DailyCheckin>()
                        .eq(DailyCheckin::getUserId, userId)
                        .orderByDesc(DailyCheckin::getCheckinDate)
                        .last("LIMIT 30"))
                .stream()
                .map(DailyCheckin::getCheckinDate)
                .toList();
        return new CheckinStatusResponse(
                checkedToday,
                checkedToday ? currentStreak : currentStreak,
                todayReward,
                rewardFor(Math.max(1, currentStreak + 1)),
                total,
                recent
        );
    }

    @Override
    @Transactional
    public CheckinResultResponse checkin(Long userId) {
        LocalDate today = LocalDate.now(ZONE);
        DailyCheckin existing = checkinMapper.selectOne(new LambdaQueryWrapper<DailyCheckin>()
                .eq(DailyCheckin::getUserId, userId)
                .eq(DailyCheckin::getCheckinDate, today)
                .last("LIMIT 1"));
        if (existing != null) {
            throw new BusinessException(ErrorCode.CONFLICT, "今天已经签到过了");
        }

        DailyCheckin previous = latest(userId);
        int streak = previous != null
                && today.minusDays(1).equals(previous.getCheckinDate())
                ? previous.getStreakDays() + 1
                : 1;
        int reward = rewardFor(streak);

        DailyCheckin checkin = new DailyCheckin();
        checkin.setUserId(userId);
        checkin.setCheckinDate(today);
        checkin.setStreakDays(streak);
        checkin.setRewardPoints(reward);
        try {
            checkinMapper.insert(checkin);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "今天已经签到过了");
        }

        pointService.award(
                userId,
                reward,
                "DAILY_CHECKIN",
                "checkin:" + userId + ":" + today,
                "CHECKIN",
                String.valueOf(checkin.getId()),
                streak % 7 == 0 ? "连续签到 " + streak + " 天奖励" : "每日签到奖励"
        );
        return new CheckinResultResponse(reward, status(userId));
    }

    private DailyCheckin latest(Long userId) {
        return checkinMapper.selectOne(new LambdaQueryWrapper<DailyCheckin>()
                .eq(DailyCheckin::getUserId, userId)
                .orderByDesc(DailyCheckin::getCheckinDate)
                .last("LIMIT 1"));
    }

    private int rewardFor(int streak) {
        return BASE_REWARD + (streak > 0 && streak % 7 == 0 ? WEEK_BONUS : 0);
    }
}
