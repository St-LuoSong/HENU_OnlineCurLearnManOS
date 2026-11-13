package com.onlinecurlearnmanos.learning.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("learning_plan")
public class LearningPlan {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long cycleId;

    private String title;

    private String summary;

    private String status;

    private String providerCode;

    private String model;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;

    private Integer version;
}
