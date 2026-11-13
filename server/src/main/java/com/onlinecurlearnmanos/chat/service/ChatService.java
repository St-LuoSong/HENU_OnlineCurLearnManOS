package com.onlinecurlearnmanos.chat.service;

import com.onlinecurlearnmanos.chat.dto.ChatMessageResponse;
import com.onlinecurlearnmanos.chat.dto.ChatMessageSendRequest;
import com.onlinecurlearnmanos.chat.dto.ChatSessionCreateRequest;
import com.onlinecurlearnmanos.chat.dto.ChatSessionResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface ChatService {

    List<ChatSessionResponse> listSessions(Long userId);

    ChatSessionResponse createSession(Long userId, ChatSessionCreateRequest request);

    List<ChatMessageResponse> listMessages(Long userId, Long sessionId);

    ChatMessageResponse sendMessage(Long userId, Long sessionId, ChatMessageSendRequest request);

    SseEmitter streamMessage(Long userId, Long sessionId, ChatMessageSendRequest request);

    boolean cancelStream(Long userId, Long sessionId, String requestId);

    void deleteSession(Long userId, Long sessionId);
}
