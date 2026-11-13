package com.onlinecurlearnmanos.chat.controller;

import com.onlinecurlearnmanos.chat.dto.ChatMessageResponse;
import com.onlinecurlearnmanos.chat.dto.ChatMessageSendRequest;
import com.onlinecurlearnmanos.chat.dto.ChatSessionCreateRequest;
import com.onlinecurlearnmanos.chat.dto.ChatSessionResponse;
import com.onlinecurlearnmanos.chat.service.ChatService;
import com.onlinecurlearnmanos.common.api.ApiResponse;
import com.onlinecurlearnmanos.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @GetMapping("/sessions")
    public ApiResponse<List<ChatSessionResponse>> listSessions(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(chatService.listSessions(principal.getId()));
    }

    @PostMapping("/sessions")
    public ApiResponse<ChatSessionResponse> createSession(
            @Valid @RequestBody ChatSessionCreateRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(chatService.createSession(principal.getId(), request));
    }

    @GetMapping("/sessions/{id}/messages")
    public ApiResponse<List<ChatMessageResponse>> listMessages(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(chatService.listMessages(principal.getId(), id));
    }

    @PostMapping("/sessions/{id}/messages")
    public ApiResponse<ChatMessageResponse> sendMessage(
            @PathVariable Long id,
            @Valid @RequestBody ChatMessageSendRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(chatService.sendMessage(principal.getId(), id, request));
    }

    @PostMapping(value = "/sessions/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> streamMessage(
            @PathVariable Long id,
            @Valid @RequestBody ChatMessageSendRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        SseEmitter emitter = chatService.streamMessage(principal.getId(), id, request);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "event-stream", StandardCharsets.UTF_8))
                .cacheControl(CacheControl.noCache().noTransform())
                .header("X-Accel-Buffering", "no")
                .body(emitter);
    }

    @PostMapping("/sessions/{id}/streams/{requestId}/cancel")
    public ApiResponse<Boolean> cancelStream(
            @PathVariable Long id,
            @PathVariable String requestId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(chatService.cancelStream(principal.getId(), id, requestId));
    }

    @DeleteMapping("/sessions/{id}")
    public ApiResponse<Void> deleteSession(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        chatService.deleteSession(principal.getId(), id);
        return ApiResponse.success(null);
    }
}
