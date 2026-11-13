package com.onlinecurlearnmanos.cloudclass.dto;

import java.util.List;

public record CloudClassDetailResponse(
        CloudClassResponse classInfo,
        List<CloudClassMemberResponse> members,
        List<CloudClassResourceResponse> resources,
        List<CloudClassTaskResponse> tasks
) {
}
