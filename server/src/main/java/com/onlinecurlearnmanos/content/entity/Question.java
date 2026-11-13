package com.onlinecurlearnmanos.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("question")
public class Question {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long subjectId;

    private Long knowledgePointId;

    private String questionType;

    private Integer difficulty;

    private String content;

    private String correctAnswer;

    private String explanation;

    private Integer isTransfer;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;

    private Integer version;
}
