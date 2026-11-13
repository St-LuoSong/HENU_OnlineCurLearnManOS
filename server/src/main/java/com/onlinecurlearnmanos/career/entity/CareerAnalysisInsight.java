package com.onlinecurlearnmanos.career.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("career_analysis_insight")
public class CareerAnalysisInsight {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long analysisId;

    private String category;

    private String title;

    private String content;

    private String reason;

    private LocalDate targetDate;

    private Long convertedGoalId;

    private Long convertedTaskId;

    private Integer score;

    private Integer sortOrder;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;

    private Integer version;
}
