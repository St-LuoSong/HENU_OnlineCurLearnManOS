package com.onlinecurlearnmanos.career.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 分析时点与当前时点的可量化变化。由后端计算差值，AI 只负责解释，不负责算数。
 */
public record CareerComparisonResponse(
        Long analysisId,
        LocalDateTime analysisCreatedAt,
        Integer learningRecordCountBefore,
        int learningRecordCountAfter,
        Integer learningMinutesBefore,
        int learningMinutesAfter,
        Integer masteredPointCountBefore,
        int masteredPointCountAfter,
        Integer completedTaskCountBefore,
        int completedTaskCountAfter,
        Integer gradedSubmissionCountBefore,
        int gradedSubmissionCountAfter,
        Double averageScoreBefore,
        Double averageScoreAfter,
        List<String> notes
) {
}
