package com.onlinecurlearnmanos.practice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("practice_set")
public class PracticeSet {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long subjectId;

    private Long knowledgePointId;

    private String title;

    private String billingMode;

    private String status;

    private Integer questionCount;

    private Integer answeredCount;

    private Integer correctCount;

    private Integer score;

    private Integer durationSeconds;

    private LocalDateTime startedAt;

    private LocalDateTime submittedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
