package com.onlinecurlearnmanos.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("knowledge_point_detail")
public class KnowledgePointDetail {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long knowledgePointId;

    private String detailType;

    private String content;

    private Integer sortOrder;

    private LocalDateTime createdAt;
}
