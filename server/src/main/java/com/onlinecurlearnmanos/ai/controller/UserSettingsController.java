package com.onlinecurlearnmanos.ai.controller;

import com.onlinecurlearnmanos.ai.dto.UserAiPreferenceResponse;
import com.onlinecurlearnmanos.ai.dto.UserAiPreferenceUpdateRequest;
import com.onlinecurlearnmanos.ai.service.UserAiPreferenceService;
import com.onlinecurlearnmanos.common.api.ApiResponse;
import com.onlinecurlearnmanos.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settings/ai")
@RequiredArgsConstructor
public class UserSettingsController {

    private final UserAiPreferenceService preferenceService;

    @GetMapping
    public ApiResponse<UserAiPreferenceResponse> get(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(preferenceService.get(principal.getId()));
    }

    @PutMapping
    public ApiResponse<UserAiPreferenceResponse> update(
            @Valid @RequestBody UserAiPreferenceUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(preferenceService.update(principal.getId(), request));
    }
}
