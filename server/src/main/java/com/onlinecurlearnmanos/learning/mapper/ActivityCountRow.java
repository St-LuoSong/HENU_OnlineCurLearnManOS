package com.onlinecurlearnmanos.learning.mapper;

import lombok.Data;

import java.time.LocalDate;

@Data
public class ActivityCountRow {

    private LocalDate activityDate;

    private String activityType;

    private long amount;
}
