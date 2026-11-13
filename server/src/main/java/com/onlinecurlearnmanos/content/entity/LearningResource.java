package com.onlinecurlearnmanos.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("learning_resource")
public class LearningResource {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long subjectId;

    private String title;

    private String description;

    private String resourceType;

    private String content;

    private String externalUrl;

    private Long publisherId;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;

    private Integer version;
}
