package com.onlinecurlearnmanos.cloudclass.dto;

import java.util.List;

/**
 * 班级名单分页响应。
 *
 * <p>{@code readOnly} 为 true 表示当前是学生视角，名单只包含精简字段且仅含在读成员。</p>
 */
public record CloudClassRosterResponse(
        List<CloudClassRosterMemberResponse> members,
        long total,
        long page,
        long size,
        boolean readOnly
) {
}
