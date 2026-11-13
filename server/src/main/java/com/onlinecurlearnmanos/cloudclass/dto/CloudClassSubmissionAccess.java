package com.onlinecurlearnmanos.cloudclass.dto;

/**
 * 作业提交的访问上下文，用于附件等衍生资源复用云班级的成员、任务与重交策略校验。
 */
public record CloudClassSubmissionAccess(
        Long submissionId,
        Long taskId,
        Long classId,
        Long teacherId,
        Long studentId,
        Integer versionNo,
        String submissionStatus
) {
}
