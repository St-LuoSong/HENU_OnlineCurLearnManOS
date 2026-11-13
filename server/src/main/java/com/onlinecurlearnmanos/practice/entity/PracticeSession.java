package com.onlinecurlearnmanos.practice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("practice_session")
public class PracticeSession {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long subjectId;

    private Long knowledgePointId;

    private String title;

    private String state;

    private String currentGrade;

    private Integer consecutiveFailures;

    private Long currentQuestionId;

    private Integer currentTransfer;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;

    private Integer version;
}
