package com.onlinecurlearnmanos.practice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("practice_answer")
public class PracticeAnswer {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    private Long subjectId;

    private Long knowledgePointId;

    private Long questionId;

    private Integer isTransfer;

    private String answerContent;

    private Integer isCorrect;

    private String grade;

    private String errorType;

    private String errorLabel;

    private String feedback;

    private Integer consecutiveFailures;

    private LocalDateTime answeredAt;

    @TableLogic
    private Integer deleted;

    private Integer version;
}
