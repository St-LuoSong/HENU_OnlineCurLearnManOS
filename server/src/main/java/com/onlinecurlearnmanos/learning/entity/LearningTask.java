package com.onlinecurlearnmanos.learning.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("learning_task")
public class LearningTask {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long goalId;

    private Long userId;

    private Long cycleId;

    private Long sourceTaskId;

    private String title;

    private String description;

    private String category;

    private LocalDateTime dueAt;

    private String priority;

    private String status;

    private LocalDateTime completedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;

    private Integer version;
}
