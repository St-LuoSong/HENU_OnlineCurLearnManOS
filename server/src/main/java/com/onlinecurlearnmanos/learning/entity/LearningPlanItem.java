package com.onlinecurlearnmanos.learning.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("learning_plan_item")
public class LearningPlanItem {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long planId;

    private String title;

    private String description;

    private LocalDate startDate;

    private LocalDate endDate;

    private Long resourceId;

    private Long knowledgePointId;

    private Long goalId;

    private Long taskId;

    private String status;

    private Integer sortOrder;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;

    private Integer version;
}
