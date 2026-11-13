package com.onlinecurlearnmanos.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("knowledge_favorite")
public class KnowledgeFavorite {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long messageId;

    private Long sessionId;

    private Long subjectId;

    private Long knowledgePointId;

    private String question;

    private String answer;

    private String note;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
