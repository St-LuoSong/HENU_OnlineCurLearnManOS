package com.onlinecurlearnmanos.chat.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 记录仍在生成中的流式请求，供前端“停止生成”触发协作式取消。
 */
@Component
public class ActiveStreamRegistry {

    public record ActiveStream(Long userId, Long sessionId, AtomicBoolean cancelled) {
    }

    private final ConcurrentHashMap<String, ActiveStream> streams = new ConcurrentHashMap<>();

    public ActiveStream register(String requestId, Long userId, Long sessionId) {
        ActiveStream stream = new ActiveStream(userId, sessionId, new AtomicBoolean(false));
        streams.put(requestId, stream);
        return stream;
    }

    public boolean cancel(String requestId, Long userId, Long sessionId) {
        ActiveStream stream = streams.get(requestId);
        if (stream == null
                || !stream.userId().equals(userId)
                || !stream.sessionId().equals(sessionId)) {
            return false;
        }
        stream.cancelled().set(true);
        return true;
    }

    public void unregister(String requestId) {
        streams.remove(requestId);
    }
}
