package com.onlinecurlearnmanos.growth.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("growth_cycle_direction")
public class GrowthCycleDirection {

    @TableId(value = "cycle_id", type = IdType.INPUT)
    private Long cycleId;

    private Long sourceAnalysisId;

    private String directionSummary;

    private String snapshotJson;

    private LocalDateTime updatedAt;
}
