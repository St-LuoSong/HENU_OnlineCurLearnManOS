package com.onlinecurlearnmanos.growth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("growth_weekly_review")
public class GrowthWeeklyReview {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long cycleId;

    private LocalDate weekStart;

    private LocalDate weekEnd;

    private String evidenceJson;

    private String note;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
