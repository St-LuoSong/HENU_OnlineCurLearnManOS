package com.onlinecurlearnmanos.chat.service;

import com.onlinecurlearnmanos.ai.dto.LlmChatRequest;
import com.onlinecurlearnmanos.ai.dto.LlmChatResponse;
import com.onlinecurlearnmanos.ai.dto.LlmStreamDelta;
import com.onlinecurlearnmanos.ai.dto.AiMode;
import com.onlinecurlearnmanos.ai.service.LlmService;
import com.onlinecurlearnmanos.chat.dto.ChatMessageResponse;
import com.onlinecurlearnmanos.chat.dto.ChatMessageSendRequest;
import com.onlinecurlearnmanos.chat.dto.ChatSessionCreateRequest;
import com.onlinecurlearnmanos.chat.dto.ChatSessionResponse;
import com.onlinecurlearnmanos.chat.entity.ChatMessage;
import com.onlinecurlearnmanos.chat.entity.ChatSession;
import com.onlinecurlearnmanos.chat.mapper.ChatMessageMapper;
import com.onlinecurlearnmanos.chat.mapper.ChatSessionMapper;
import com.onlinecurlearnmanos.common.error.ErrorCode;
import com.onlinecurlearnmanos.common.exception.BusinessException;
import com.onlinecurlearnmanos.common.exception.StreamCancelledException;
import com.onlinecurlearnmanos.content.entity.KnowledgePoint;
import com.onlinecurlearnmanos.content.entity.Subject;
import com.onlinecurlearnmanos.content.mapper.KnowledgePointMapper;
import com.onlinecurlearnmanos.content.mapper.SubjectMapper;
import com.onlinecurlearnmanos.content.service.KnowledgeContextBuilder;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.time.Duration;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

@Service
public class ChatServiceImpl implements ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatServiceImpl.class);
    private static final long SSE_TIMEOUT_MILLIS = 180000L;
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(15);
    private static final String FAILURE_PROVIDER_CODE = "SYSTEM";
    private static final String FAILURE_MESSAGE = "生成失败，可重试";

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final SubjectMapper subjectMapper;
    private final KnowledgePointMapper knowledgePointMapper;
    private final KnowledgeContextBuilder knowledgeContextBuilder;
    private final LlmService llmService;
    private final TaskExecutor chatTaskExecutor;
    private final TaskScheduler chatHeartbeatScheduler;
    private final ActiveStreamRegistry activeStreamRegistry;

    public ChatServiceImpl(
            ChatSessionMapper sessionMapper,
            ChatMessageMapper messageMapper,
            SubjectMapper subjectMapper,
            KnowledgePointMapper knowledgePointMapper,
            KnowledgeContextBuilder knowledgeContextBuilder,
            LlmService llmService,
            @Qualifier("chatTaskExecutor") TaskExecutor chatTaskExecutor,
            @Qualifier("chatHeartbeatScheduler") TaskScheduler chatHeartbeatScheduler,
            ActiveStreamRegistry activeStreamRegistry
    ) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.subjectMapper = subjectMapper;
        this.knowledgePointMapper = knowledgePointMapper;
        this.knowledgeContextBuilder = knowledgeContextBuilder;
        this.llmService = llmService;
        this.chatTaskExecutor = chatTaskExecutor;
        this.chatHeartbeatScheduler = chatHeartbeatScheduler;
        this.activeStreamRegistry = activeStreamRegistry;
    }

    @Override
    public List<ChatSessionResponse> listSessions(Long userId) {
        return sessionMapper.selectList(new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getUserId, userId)
                        .orderByDesc(ChatSession::getUpdatedAt)
                        .orderByDesc(ChatSession::getId))
                .stream()
                .map(this::toSessionResponse)
                .toList();
    }

    @Override
    @Transactional
    public ChatSessionResponse createSession(Long userId, ChatSessionCreateRequest request) {
        validateRelations(request.subjectId(), request.knowledgePointId());
        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setSubjectId(request.subjectId());
        session.setKnowledgePointId(request.knowledgePointId());
        session.setTopic(StringUtils.hasText(request.topic()) ? request.topic().trim() : null);
        session.setTitle(StringUtils.hasText(request.title())
                ? request.title().trim()
                : defaultTitle(request.subjectId(), session.getTopic()));
        session.setStatus("ACTIVE");
        session.setDeleted(0);
        session.setVersion(0);
        sessionMapper.insert(session);
        return toSessionResponse(session);
    }

    @Override
    public List<ChatMessageResponse> listMessages(Long userId, Long sessionId) {
        getRequiredSession(userId, sessionId);
        return messageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .orderByAsc(ChatMessage::getCreatedAt)
                        .orderByAsc(ChatMessage::getId))
                .stream()
                .map(this::toMessageResponse)
                .toList();
    }

    @Override
    public ChatMessageResponse sendMessage(
            Long userId,
            Long sessionId,
            ChatMessageSendRequest request
    ) {
        ChatSession session = getRequiredSession(userId, sessionId);
        AiMode mode = AiMode.parse(request.mode());
        String content = request.retry()
                ? resolveRetryContent(sessionId)
                : request.content().trim();
        if (!request.retry()) {
            saveUserMessage(session, content);
        }
        return generateAssistantMessage(userId, session, content, mode);
    }

    @Override
    public SseEmitter streamMessage(
            Long userId,
            Long sessionId,
            ChatMessageSendRequest request
    ) {
        ChatSession session = getRequiredSession(userId, sessionId);
        AiMode mode = AiMode.parse(request.mode());
        String content = request.retry()
                ? resolveRetryContent(sessionId)
                : request.content().trim();
        if (!request.retry()) {
            saveUserMessage(session, content);
        }
        return startStream(userId, session, content, mode);
    }

    private SseEmitter startStream(
            Long userId,
            ChatSession session,
            String content,
            AiMode mode
    ) {
        Long sessionId = session.getId();
        String requestId = UUID.randomUUID().toString().replace("-", "");
        SseEmitter emitter = createEmitter();
        AtomicBoolean clientClosed = new AtomicBoolean(false);
        ActiveStreamRegistry.ActiveStream activeStream =
                activeStreamRegistry.register(requestId, userId, sessionId);
        BooleanSupplier cancelled = () ->
                clientClosed.get() || activeStream.cancelled().get();
        AtomicLong firstUpstreamDeltaAt = new AtomicLong();
        AtomicLong firstDownstreamDeltaAt = new AtomicLong();
        AtomicInteger chunkCount = new AtomicInteger();
        long startedAt = System.nanoTime();
        ScheduledFuture<?> heartbeat = scheduleHeartbeat(emitter, clientClosed);

        emitter.onTimeout(() -> {
            clientClosed.set(true);
            activeStream.cancelled().set(true);
            emitter.complete();
        });
        emitter.onError(error -> {
            clientClosed.set(true);
            activeStream.cancelled().set(true);
            emitter.complete();
        });
        emitter.onCompletion(() -> {
            heartbeat.cancel(false);
            activeStream.cancelled().set(true);
        });

        try {
            sendEvent(emitter, "meta", Map.of(
                    "sessionId", sessionId,
                    "requestId", requestId
            ));
            log.info(
                    "SSE stream opened, requestId={}, userId={}, sessionId={}, openedMs={}",
                    requestId,
                    userId,
                    sessionId,
                    elapsedMillis(startedAt)
            );
        } catch (IOException | IllegalStateException exception) {
            heartbeat.cancel(false);
            activeStreamRegistry.unregister(requestId);
            emitter.completeWithError(exception);
            return emitter;
        }

        try {
            chatTaskExecutor.execute(() -> {
                try {
                    ChatMessageResponse message = generateStreamingAssistantMessage(
                            userId,
                            session,
                            content,
                            delta -> {
                                firstUpstreamDeltaAt.compareAndSet(0L, System.nanoTime());
                                sendDelta(emitter, delta, clientClosed, requestId);
                                firstDownstreamDeltaAt.compareAndSet(0L, System.nanoTime());
                                chunkCount.incrementAndGet();
                            },
                            cancelled,
                            mode
                    );

                    if (cancelled.getAsBoolean()) {
                        log.info(
                                "SSE stream stopped by client, requestId={}, sessionId={}, chunks={}",
                                requestId,
                                sessionId,
                                chunkCount.get()
                        );
                        emitter.complete();
                        return;
                    }

                    /*
                     * done 帧写出失败只说明客户端在最后一帧之前断开：回答已经落库、计费也已确认，
                     * 这里必须就地吞掉异常，否则会被下面的 catch 误判为「生成失败」并写入失败标记。
                     */
                    try {
                        sendEvent(emitter, "done", Map.of(
                                "messageId", message.id(),
                                "totalTokens", message.totalTokens() == null ? 0 : message.totalTokens(),
                                "requestId", requestId
                        ));
                    } catch (IOException | IllegalStateException exception) {
                        log.info(
                                "SSE done frame dropped because the client closed early, "
                                        + "requestId={}, sessionId={}",
                                requestId,
                                sessionId
                        );
                    }
                    log.info(
                            "SSE stream completed, requestId={}, userId={}, sessionId={}, "
                                    + "firstUpstreamDeltaMs={}, firstDownstreamDeltaMs={}, "
                                    + "chunks={}, totalMs={}",
                            requestId,
                            userId,
                            sessionId,
                            elapsedMillis(firstUpstreamDeltaAt, startedAt),
                            elapsedMillis(firstDownstreamDeltaAt, startedAt),
                            chunkCount.get(),
                            elapsedMillis(startedAt)
                    );
                    emitter.complete();
                } catch (Exception exception) {
                    boolean stopped = cancelled.getAsBoolean()
                            || exception instanceof StreamCancelledException;
                    log.warn(
                            "SSE stream {}, requestId={}, userId={}, sessionId={}, message={}",
                            stopped ? "stopped" : "failed",
                            requestId,
                            userId,
                            sessionId,
                            readableMessage(exception)
                    );
                    if (!stopped) {
                        try {
                            sendEvent(emitter, "error", Map.of(
                                    "code", "CHAT_STREAM_FAILED",
                                    "message", readableMessage(exception),
                                    "requestId", requestId,
                                    "retryable", true
                            ));
                        } catch (Exception ignored) {
                            // The client may already have closed the connection.
                        }
                        try {
                            saveFailureMessage(session, content);
                        } catch (RuntimeException markerException) {
                            log.error(
                                    "Failed to persist chat failure marker, requestId={}, sessionId={}",
                                    requestId,
                                    sessionId,
                                    markerException
                            );
                        }
                    }
                    emitter.complete();
                } finally {
                    heartbeat.cancel(false);
                    activeStreamRegistry.unregister(requestId);
                }
            });
        } catch (RuntimeException exception) {
            heartbeat.cancel(false);
            activeStreamRegistry.unregister(requestId);
            emitter.completeWithError(exception);
        }
        return emitter;
    }

    @Override
    public boolean cancelStream(Long userId, Long sessionId, String requestId) {
        getRequiredSession(userId, sessionId);
        return activeStreamRegistry.cancel(requestId, userId, sessionId);
    }

    protected SseEmitter createEmitter() {
        return new SseEmitter(SSE_TIMEOUT_MILLIS);
    }

    private ScheduledFuture<?> scheduleHeartbeat(
            SseEmitter emitter,
            AtomicBoolean clientClosed
    ) {
        return chatHeartbeatScheduler.scheduleAtFixedRate(() -> {
            if (clientClosed.get()) {
                return;
            }
            try {
                emitter.send(SseEmitter.event().comment("keep-alive"));
            } catch (IOException | IllegalStateException exception) {
                clientClosed.set(true);
                emitter.completeWithError(exception);
            }
        }, HEARTBEAT_INTERVAL);
    }

    private void sendEvent(SseEmitter emitter, String eventName, Map<String, ?> data)
            throws IOException {
        emitter.send(SseEmitter.event().name(eventName).data(data));
    }

    /**
     * 客户端断开时抛出业务异常，让计费层把这次调用判为失败并退回预占积分。
     */
    private void sendDelta(
            SseEmitter emitter,
            LlmStreamDelta delta,
            AtomicBoolean clientClosed,
            String requestId
    ) {
        if (clientClosed.get()) {
            throw new StreamCancelledException("客户端已停止生成");
        }
        String content = delta.content() == null ? "" : delta.content();
        try {
            emitter.send(SseEmitter.event()
                    .name("delta")
                    .data(Map.of(
                            "content", content,
                            "phase", delta.phase(),
                            "requestId", requestId
                    )));
        } catch (IOException | IllegalStateException exception) {
            clientClosed.set(true);
            throw new StreamCancelledException("客户端已停止生成");
        }
    }

    @Override
    @Transactional
    public void deleteSession(Long userId, Long sessionId) {
        getRequiredSession(userId, sessionId);
        messageMapper.delete(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId));
        sessionMapper.deleteById(sessionId);
    }

    private ChatMessageResponse generateAssistantMessage(
            Long userId,
            ChatSession session,
            String userContent,
            AiMode mode
    ) {
        return llmService.execute(
                userId,
                buildChatRequest(session, userContent),
                mode,
                "AI_CHAT",
                "智能问答调用",
                response -> persistAssistantMessage(session, userContent, response)
        );
    }

    private ChatMessageResponse generateStreamingAssistantMessage(
            Long userId,
            ChatSession session,
            String userContent,
            Consumer<LlmStreamDelta> onDelta,
            BooleanSupplier cancelled,
            AiMode mode
    ) {
        return llmService.executeStream(
                userId,
                buildChatRequest(session, userContent),
                mode,
                "AI_CHAT",
                "智能问答调用",
                onDelta,
                response -> persistAssistantMessage(session, userContent, response),
                cancelled
        );
    }

    private LlmChatRequest buildChatRequest(ChatSession session, String userContent) {
        String requestId = UUID.randomUUID().toString().replace("-", "");
        String idempotencyKey = "chat:" + session.getId() + ":" + requestId;
        return new LlmChatRequest(
                userContent,
                buildSystemPrompt(session),
                null,
                0.7,
                idempotencyKey,
                requestId
        );
    }

    private ChatMessageResponse persistAssistantMessage(
            ChatSession session,
            String userContent,
            LlmChatResponse response
    ) {
        ChatMessage assistant = new ChatMessage();
        assistant.setSessionId(session.getId());
        assistant.setRole("assistant");
        assistant.setContent(response.content());
        assistant.setProviderCode(response.providerCode());
        assistant.setModel(response.model());
        assistant.setTotalTokens(
                response.usage() == null ? null : response.usage().totalTokens()
        );
        messageMapper.insert(assistant);

        refreshSession(session, userContent);
        return toMessageResponse(assistant);
    }

    private void saveUserMessage(ChatSession session, String content) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(session.getId());
        message.setRole("user");
        message.setContent(content);
        messageMapper.insert(message);
    }

    private void saveFailureMessage(ChatSession session, String userContent) {
        refreshSession(session, userContent);
        ChatMessage failure = new ChatMessage();
        failure.setSessionId(session.getId());
        failure.setRole("assistant");
        failure.setContent(FAILURE_MESSAGE);
        failure.setProviderCode(FAILURE_PROVIDER_CODE);
        messageMapper.insert(failure);
    }

    private void refreshSession(ChatSession session, String userContent) {
        String newTitle = session.getTitle();
        String courseTitle = defaultTitle(session.getSubjectId(), session.getTopic());
        long messageCount = messageMapper.selectCount(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, session.getId()));
        if (messageCount <= 2
                && !StringUtils.hasText(session.getTopic())
                && (!StringUtils.hasText(newTitle)
                || "新对话".equals(newTitle)
                || courseTitle.equals(newTitle))) {
            newTitle = userContent.length() <= 30 ? userContent : userContent.substring(0, 30) + "...";
        }
        sessionMapper.update(null, new LambdaUpdateWrapper<ChatSession>()
                .eq(ChatSession::getId, session.getId())
                .set(ChatSession::getTitle, newTitle)
                .set(ChatSession::getUpdatedAt, LocalDateTime.now()));
    }

    private String buildSystemPrompt(ChatSession session) {
        StringBuilder prompt = new StringBuilder(
                "你是面向大学生的学习助手。回答要准确、简洁、循序渐进，优先解释概念和思路。"
        );
        if (session.getSubjectId() != null) {
            Subject subject = subjectMapper.selectById(session.getSubjectId());
            if (subject != null) {
                prompt.append("当前课程：").append(subject.getName()).append("。");
            }
        }
        if (StringUtils.hasText(session.getTopic())) {
            prompt.append("当前学习主题：").append(session.getTopic()).append("。");
        }
        if (session.getKnowledgePointId() != null) {
            KnowledgePoint point = knowledgePointMapper.selectById(session.getKnowledgePointId());
            if (point != null) {
                prompt.append("当前知识点：").append(point.getName()).append("。");
            }
            String context = knowledgeContextBuilder.build(
                    session.getSubjectId(),
                    session.getKnowledgePointId()
            );
            if (StringUtils.hasText(context)) {
                prompt.append("\n\n## 本知识点资料\n").append(context)
                        .append("\n回答时优先依据上面的资料，资料没有覆盖的内容要明确说明。");
            }
        }
        return prompt.toString();
    }

    private void validateRelations(Long subjectId, Long knowledgePointId) {
        if (knowledgePointId != null && subjectId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "选择知识点前请先选择课程");
        }
        if (subjectId != null && subjectMapper.selectById(subjectId) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "课程不存在");
        }
        if (knowledgePointId != null) {
            KnowledgePoint point = knowledgePointMapper.selectById(knowledgePointId);
            if (point == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "知识点不存在");
            }
            if (subjectId != null && !subjectId.equals(point.getSubjectId())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "知识点不属于所选课程");
            }
        }
    }

    private String resolveRetryContent(Long sessionId) {
        List<ChatMessage> recentMessages = messageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .orderByDesc(ChatMessage::getCreatedAt)
                        .orderByDesc(ChatMessage::getId)
                        .last("LIMIT 2")
        );
        if (!recentMessages.isEmpty() && "user".equals(recentMessages.get(0).getRole())) {
            return recentMessages.get(0).getContent();
        }
        if (recentMessages.size() >= 2
                && isFailureMarker(recentMessages.get(0))
                && "user".equals(recentMessages.get(1).getRole())) {
            messageMapper.deleteById(recentMessages.get(0).getId());
            return recentMessages.get(1).getContent();
        }
        throw new BusinessException(
                ErrorCode.CONFLICT,
                "当前没有可重试的提问，请刷新页面查看最新状态"
        );
    }

    private boolean isFailureMarker(ChatMessage message) {
        return "assistant".equals(message.getRole())
                && FAILURE_PROVIDER_CODE.equals(message.getProviderCode())
                && FAILURE_MESSAGE.equals(message.getContent());
    }

    private long elapsedMillis(AtomicLong firstEventAt, long startedAt) {
        long value = firstEventAt.get();
        return value == 0L ? -1L : (value - startedAt) / 1_000_000L;
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    private ChatSession getRequiredSession(Long userId, Long sessionId) {
        ChatSession session = sessionMapper.selectOne(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getId, sessionId)
                .eq(ChatSession::getUserId, userId)
                .last("LIMIT 1"));
        if (session == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "对话不存在");
        }
        return session;
    }

    private ChatSessionResponse toSessionResponse(ChatSession session) {
        Subject subject = session.getSubjectId() == null ? null : subjectMapper.selectById(session.getSubjectId());
        KnowledgePoint point = session.getKnowledgePointId() == null
                ? null
                : knowledgePointMapper.selectById(session.getKnowledgePointId());
        long messageCount = messageMapper.selectCount(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, session.getId()));
        ChatMessage lastMessage = messageMapper.selectOne(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, session.getId())
                .orderByDesc(ChatMessage::getCreatedAt)
                .orderByDesc(ChatMessage::getId)
                .last("LIMIT 1"));
        return new ChatSessionResponse(
                session.getId(),
                session.getSubjectId(),
                subject == null ? null : subject.getName(),
                session.getKnowledgePointId(),
                point == null ? null : point.getName(),
                session.getTitle(),
                session.getTopic(),
                session.getStatus(),
                messageCount,
                lastMessage == null ? "" : preview(lastMessage.getContent()),
                session.getUpdatedAt()
        );
    }

    private ChatMessageResponse toMessageResponse(ChatMessage message) {
        return new ChatMessageResponse(
                message.getId(),
                message.getRole(),
                message.getContent(),
                message.getProviderCode(),
                message.getModel(),
                message.getTotalTokens(),
                message.getCreatedAt()
        );
    }

    private String defaultTitle(Long subjectId, String topic) {
        if (StringUtils.hasText(topic)) {
            return topic.trim();
        }
        if (subjectId == null) {
            return "自定义主题问答";
        }
        Subject subject = subjectMapper.selectById(subjectId);
        return subject == null ? "新对话" : subject.getName() + "学习问答";
    }

    private String preview(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String normalized = content.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 60 ? normalized : normalized.substring(0, 60) + "...";
    }

    private String readableMessage(Exception exception) {
        return exception.getMessage() == null ? "对话服务异常" : exception.getMessage();
    }
}
