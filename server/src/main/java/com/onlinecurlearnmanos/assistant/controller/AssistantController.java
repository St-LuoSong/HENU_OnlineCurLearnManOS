package com.onlinecurlearnmanos.assistant.controller;

import com.onlinecurlearnmanos.assistant.dto.AssistantSessionResponse;
import com.onlinecurlearnmanos.assistant.service.AssistantSessionService;
import com.onlinecurlearnmanos.common.api.ApiResponse;
import com.onlinecurlearnmanos.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/assistant")
@RequiredArgsConstructor
public class AssistantController {

    private final AssistantSessionService assistantSessionService;

    @GetMapping("/sessions")
    public ApiResponse<List<AssistantSessionResponse>> list(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(assistantSessionService.list(principal.getId()));
    }
}
