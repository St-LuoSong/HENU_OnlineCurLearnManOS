package com.onlinecurlearnmanos.learning.service;

import com.onlinecurlearnmanos.learning.dto.InteractionBreakdownResponse;
import com.onlinecurlearnmanos.learning.dto.InteractionOverviewResponse;
import com.onlinecurlearnmanos.learning.dto.InteractionTrendPointResponse;
import com.onlinecurlearnmanos.learning.dto.RecentScoreResponse;
import com.onlinecurlearnmanos.learning.mapper.ActivityCountRow;
import com.onlinecurlearnmanos.learning.mapper.LearningActivityMapper;
import com.onlinecurlearnmanos.learning.mapper.RecentScoreRow;
import com.onlinecurlearnmanos.practice.entity.PracticeSet;
import com.onlinecurlearnmanos.practice.mapper.PracticeSetMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StudentActivityService {

    private static final int TREND_DAYS = 7;
    private static final int RECENT_SCORE_LIMIT = 8;
    private static final int ACCURACY_DAYS = 90;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final LearningActivityMapper activityMapper;
    private final PracticeSetMapper practiceSetMapper;

    public InteractionOverviewResponse overview(Long userId) {
        LocalDate today = LocalDate.now(ZONE);
        LocalDate fromDate = today.minusDays(TREND_DAYS - 1L);
        List<ActivityCountRow> rows = activityMapper.countActivities(
                userId,
                fromDate.atStartOfDay(),
                today.plusDays(1).atStartOfDay()
        );
        Map<LocalDate, int[]> byDate = new LinkedHashMap<>();
        for (int offset = 0; offset < TREND_DAYS; offset++) {
            byDate.put(fromDate.plusDays(offset), new int[5]);
        }
        for (ActivityCountRow row : rows) {
            int[] values = byDate.get(row.getActivityDate());
            if (values == null) {
                continue;
            }
            int index = typeIndex(row.getActivityType());
            if (index >= 0) {
                values[index] += (int) Math.max(0, row.getAmount());
            }
        }

        int[] totals = new int[5];
        List<InteractionTrendPointResponse> trend = new ArrayList<>(TREND_DAYS);
        int activeDays = 0;
        for (Map.Entry<LocalDate, int[]> entry : byDate.entrySet()) {
            InteractionBreakdownResponse breakdown = toBreakdown(entry.getValue());
            trend.add(new InteractionTrendPointResponse(entry.getKey(), breakdown));
            addTo(totals, entry.getValue());
            if (breakdown.total() > 0) {
                activeDays++;
            }
        }
        InteractionBreakdownResponse totalBreakdown = toBreakdown(totals);
        int todayCount = trend.isEmpty() ? 0 : trend.get(trend.size() - 1).total();
        return new InteractionOverviewResponse(
                totalBreakdown.total(),
                todayCount,
                activeDays,
                practiceAccuracy(userId),
                totalBreakdown,
                trend
        );
    }

    public List<RecentScoreResponse> recentScores(Long userId) {
        return activityMapper.recentScores(userId, RECENT_SCORE_LIMIT).stream()
                .map(this::toRecentScore)
                .toList();
    }

    private RecentScoreResponse toRecentScore(RecentScoreRow row) {
        return new RecentScoreResponse(
                row.getSourceType(),
                row.getSourceId(),
                row.getTitle(),
                row.getSubjectName(),
                row.getScore() == null ? null : row.getScore().doubleValue(),
                row.getMaxScore() == null ? null : row.getMaxScore().doubleValue(),
                row.getRecordedAt()
        );
    }

    private int practiceAccuracy(Long userId) {
        List<PracticeSet> sets = practiceSetMapper.selectList(
                new LambdaQueryWrapper<PracticeSet>()
                        .eq(PracticeSet::getUserId, userId)
                        .eq(PracticeSet::getStatus, "SUBMITTED")
                        .ge(PracticeSet::getSubmittedAt, LocalDateTime.now(ZONE).minusDays(ACCURACY_DAYS))
        );
        int correct = sets.stream()
                .map(PracticeSet::getCorrectCount)
                .filter(value -> value != null)
                .mapToInt(Integer::intValue)
                .sum();
        int total = sets.stream()
                .map(PracticeSet::getQuestionCount)
                .filter(value -> value != null)
                .mapToInt(Integer::intValue)
                .sum();
        return total == 0 ? 0 : (int) Math.round(correct * 100.0 / total);
    }

    private int typeIndex(String type) {
        return switch (type == null ? "" : type) {
            case "AI_QUESTION" -> 0;
            case "PRACTICE" -> 1;
            case "RESOURCE" -> 2;
            case "FORUM" -> 3;
            case "CLASS" -> 4;
            default -> -1;
        };
    }

    private InteractionBreakdownResponse toBreakdown(int[] values) {
        return new InteractionBreakdownResponse(
                values[0],
                values[1],
                values[2],
                values[3],
                values[4]
        );
    }

    private void addTo(int[] target, int[] source) {
        for (int i = 0; i < target.length; i++) {
            target[i] += source[i];
        }
    }
}
