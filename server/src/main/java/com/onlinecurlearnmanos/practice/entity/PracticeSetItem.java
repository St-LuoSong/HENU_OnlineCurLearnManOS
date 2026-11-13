package com.onlinecurlearnmanos.practice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("practice_set_item")
public class PracticeSetItem {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long setId;

    private Integer seq;

    private String questionType;

    private String stem;

    private String optionsJson;

    private String correctAnswer;

    private String explanation;

    private String studentAnswer;

    private Integer isCorrect;

    private Integer awardedScore;

    private Integer maxScore;

    private String aiFeedback;

    private String errorType;

    private LocalDateTime answeredAt;
}
