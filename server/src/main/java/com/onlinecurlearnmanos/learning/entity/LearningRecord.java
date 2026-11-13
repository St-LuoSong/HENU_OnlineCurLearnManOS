package com.onlinecurlearnmanos.learning.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("learning_record")
public class LearningRecord {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long subjectId;

    private Long knowledgePointId;

    private Long resourceId;

    private String recordType;

    private Integer durationMinutes;

    private BigDecimal score;

    private Integer mastered;

    private String remark;

    private LocalDateTime recordedAt;

    private LocalDateTime createdAt;

    @TableLogic
    private Integer deleted;

    private Integer version;
}
