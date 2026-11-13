package com.onlinecurlearnmanos.cloudclass.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/**
 * 班级名单成员。
 *
 * <p>教师与管理员视角返回完整学习统计；学生视角只返回学号、姓名、加入时间与成员状态，
 * 其余字段保持 {@code null} 并由 {@link JsonInclude} 直接从 JSON 中剔除，避免把同班同学的
 * 成绩、评语与学习活跃度暴露出去。</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CloudClassRosterMemberResponse(
        Long studentId,
        String loginId,
        String displayName,
        String status,
        LocalDateTime joinedAt,
        Long totalTaskCount,
        Long submittedTaskCount,
        Long pendingTaskCount,
        Long overdueTaskCount,
        Long gradedCount,
        Double averageScore
) {

    public static CloudClassRosterMemberResponse fullView(CloudClassMemberResponse member) {
        return new CloudClassRosterMemberResponse(
                member.studentId(),
                member.loginId(),
                member.displayName(),
                member.status(),
                member.joinedAt(),
                member.totalTaskCount(),
                member.submittedTaskCount(),
                member.pendingTaskCount(),
                member.overdueTaskCount(),
                member.gradedCount(),
                member.averageScore()
        );
    }

    public static CloudClassRosterMemberResponse basicView(
            Long studentId,
            String loginId,
            String displayName,
            String status,
            LocalDateTime joinedAt
    ) {
        return new CloudClassRosterMemberResponse(
                studentId,
                loginId,
                displayName,
                status,
                joinedAt,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
