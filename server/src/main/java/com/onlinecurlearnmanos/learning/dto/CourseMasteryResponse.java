package com.onlinecurlearnmanos.learning.dto;

/** 学习概览柱形图的单课程掌握度。 */
public record CourseMasteryResponse(
        Long subjectId,
        String subjectName,
        int totalPoints,
        int masteredPoints,
        int percent
) {
}

