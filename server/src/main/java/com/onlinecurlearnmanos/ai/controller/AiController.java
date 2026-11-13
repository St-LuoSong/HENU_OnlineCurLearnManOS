package com.onlinecurlearnmanos.ai.controller;

import com.onlinecurlearnmanos.ai.dto.LlmChatRequest;
import com.onlinecurlearnmanos.ai.dto.LlmChatResponse;
import com.onlinecurlearnmanos.ai.dto.LlmProviderResponse;
import com.onlinecurlearnmanos.ai.dto.AiAccessResponse;
import com.onlinecurlearnmanos.ai.service.AiAccessService;
import com.onlinecurlearnmanos.ai.service.LlmProviderService;
import com.onlinecurlearnmanos.ai.service.LlmService;
import com.onlinecurlearnmanos.common.api.ApiResponse;
import com.onlinecurlearnmanos.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiController {

    private final LlmService llmService;
    private final LlmProviderService providerService;
    private final AiAccessService aiAccessService;

    @GetMapping("/providers")
    public ApiResponse<List<LlmProviderResponse>> providers() {
        return ApiResponse.success(providerService.listProviders());
    }

    @GetMapping("/access")
    public ApiResponse<AiAccessResponse> access(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(aiAccessService.status(principal.getId()));
    }

    @PostMapping("/chat")
    public ApiResponse<LlmChatResponse> chat(
            @Valid @RequestBody LlmChatRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        LlmChatResponse response = llmService.execute(
                principal.getId(),
                request,
                "AI_CHAT",
                "智能问答调用",
                result -> result
        );
        return ApiResponse.success(response);
    }
}
