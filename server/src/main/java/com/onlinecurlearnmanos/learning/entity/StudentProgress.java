package com.onlinecurlearnmanos.learning.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("student_progress")
public class StudentProgress {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long subjectId;

    private Long knowledgePointId;

    private String currentGrade;

    private Integer attemptCount;

    private Integer correctCount;

    private Integer mastered;

    private Integer transferPassed;

    private LocalDateTime lastActivity;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;

    private Integer version;
}
