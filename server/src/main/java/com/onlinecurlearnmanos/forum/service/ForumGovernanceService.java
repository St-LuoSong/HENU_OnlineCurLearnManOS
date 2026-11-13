package com.onlinecurlearnmanos.forum.service;

import com.onlinecurlearnmanos.common.api.PageResult;
import com.onlinecurlearnmanos.forum.dto.ForumModerationLogResponse;
import com.onlinecurlearnmanos.forum.dto.ForumModerationRequest;
import com.onlinecurlearnmanos.forum.dto.ForumReportRequest;
import com.onlinecurlearnmanos.forum.dto.ForumReportResponse;
import com.onlinecurlearnmanos.security.UserPrincipal;

import java.util.List;

public interface ForumGovernanceService {

    ForumReportResponse report(UserPrincipal principal, ForumReportRequest request);

    PageResult<ForumReportResponse> listReports(
            long page,
            long size,
            String status,
            String targetType
    );

    List<ForumReportResponse> listPendingReportsFor(
            String targetType,
            Long targetId
    );

    ForumModerationLogResponse moderate(Long adminId, ForumModerationRequest request);

    PageResult<ForumModerationLogResponse> listLogs(long page, long size);
}
