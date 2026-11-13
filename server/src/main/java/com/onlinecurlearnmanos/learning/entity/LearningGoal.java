package com.onlinecurlearnmanos.learning.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("learning_goal")
public class LearningGoal {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long cycleId;

    private Long sourceGoalId;

    private Long subjectId;

    private String title;

    private String description;

    private LocalDate targetDate;

    private String priority;

    private String status;

    private LocalDateTime completedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;

    private Integer version;
}
