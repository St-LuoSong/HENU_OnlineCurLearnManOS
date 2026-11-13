package com.onlinecurlearnmanos.cloudclass.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("cloud_class_task")
public class CloudClassTask {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long classId;

    private Long knowledgePointId;

    private Long teacherId;

    private String title;

    private String description;

    private String taskType;

    private LocalDateTime dueAt;

    private Integer rewardPoints;

    private Integer allowResubmit;

    private Integer allowLateSubmit;

    private BigDecimal maxScore;

    private LocalDateTime publishedAt;

    private LocalDateTime closedAt;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;

    private Integer version;
}
