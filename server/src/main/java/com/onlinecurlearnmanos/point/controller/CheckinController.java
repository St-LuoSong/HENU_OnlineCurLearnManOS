package com.onlinecurlearnmanos.point.controller;

import com.onlinecurlearnmanos.common.api.ApiResponse;
import com.onlinecurlearnmanos.point.dto.CheckinResultResponse;
import com.onlinecurlearnmanos.point.dto.CheckinStatusResponse;
import com.onlinecurlearnmanos.point.service.CheckinService;
import com.onlinecurlearnmanos.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/checkins")
@RequiredArgsConstructor
public class CheckinController {

    private final CheckinService checkinService;

    @GetMapping("/status")
    public ApiResponse<CheckinStatusResponse> status(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(checkinService.status(principal.getId()));
    }

    @PostMapping
    public ApiResponse<CheckinResultResponse> checkin(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(checkinService.checkin(principal.getId()));
    }
}
