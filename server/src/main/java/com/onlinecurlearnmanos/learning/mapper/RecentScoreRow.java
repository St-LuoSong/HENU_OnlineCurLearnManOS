package com.onlinecurlearnmanos.learning.mapper;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class RecentScoreRow {

    private String sourceType;

    private Long sourceId;

    private String title;

    private String subjectName;

    private BigDecimal score;

    private BigDecimal maxScore;

    private LocalDateTime recordedAt;
}
