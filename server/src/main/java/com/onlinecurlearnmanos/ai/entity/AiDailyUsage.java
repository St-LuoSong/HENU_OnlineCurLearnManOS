package com.onlinecurlearnmanos.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("ai_daily_usage")
public class AiDailyUsage {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    private LocalDate usageDate;

    private Integer standardUsed;

    private Integer expertUsed;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
