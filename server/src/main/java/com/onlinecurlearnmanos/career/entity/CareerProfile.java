package com.onlinecurlearnmanos.career.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("career_profile")
public class CareerProfile {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Integer consentGranted;

    private String consentVersion;

    private LocalDateTime consentedAt;

    private LocalDateTime revokedAt;

    private Integer includeLearningRecords;

    private Integer includeGoals;

    private Integer includeGrades;

    private String major;

    private String gradeLevel;

    private Integer weeklyHours;

    private String interestTags;

    private String strengthNotes;

    private String careerGoal;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;

    private Integer version;
}
