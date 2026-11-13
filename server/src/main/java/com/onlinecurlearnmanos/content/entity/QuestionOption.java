package com.onlinecurlearnmanos.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("question_option")
public class QuestionOption {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long questionId;

    private String optionKey;

    private String optionText;

    private Integer sortOrder;
}
