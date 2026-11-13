package com.onlinecurlearnmanos.chat.service;

import com.onlinecurlearnmanos.ai.service.LlmService;
import com.onlinecurlearnmanos.chat.dto.ChatMessageSendRequest;
import com.onlinecurlearnmanos.chat.entity.ChatMessage;
import com.onlinecurlearnmanos.chat.entity.ChatSession;
import com.onlinecurlearnmanos.chat.mapper.ChatMessageMapper;
import com.onlinecurlearnmanos.chat.mapper.ChatSessionMapper;
import com.onlinecurlearnmanos.content.mapper.KnowledgePointMapper;
import com.onlinecurlearnmanos.content.mapper.SubjectMapper;
import com.onlinecurlearnmanos.content.service.KnowledgeContextBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

class ChatServiceSseTimingTest {

    @Test
    void writesMetaBeforeDispatchingAsyncWork() {
        ChatSessionMapper sessionMapper = mock(ChatSessionMapper.class);
        ChatMessageMapper messageMapper = mock(ChatMessageMapper.class);
        SubjectMapper subjectMapper = mock(SubjectMapper.class);
        KnowledgePointMapper knowledgePointMapper = mock(KnowledgePointMapper.class);
        KnowledgeContextBuilder contextBuilder = mock(KnowledgeContextBuilder.class);
        LlmService llmService = mock(LlmService.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        ScheduledFuture<?> heartbeat = mock(ScheduledFuture.class);

        ChatSession session = new ChatSession();
        session.setId(9L);
        session.setUserId(1L);
        session.setTitle("新对话");
        given(sessionMapper.selectOne(any())).willReturn(session);
        given(messageMapper.insert(any(ChatMessage.class))).willReturn(1);
        doReturn(heartbeat)
                .when(scheduler)
                .scheduleAtFixedRate(any(Runnable.class), any(Duration.class));

        AtomicInteger sequence = new AtomicInteger();
        RecordingTaskExecutor executor = new RecordingTaskExecutor(sequence);
        RecordingChatService service = new RecordingChatService(
                sessionMapper,
                messageMapper,
                subjectMapper,
                knowledgePointMapper,
                contextBuilder,
                llmService,
                executor,
                scheduler,
                sequence
        );

        service.streamMessage(1L, 9L, new ChatMessageSendRequest("你好", false, "STANDARD"));

        assertThat(service.emitter.metaOrder).isPositive();
        assertThat(executor.dispatchOrder).isGreaterThan(service.emitter.metaOrder);
        assertThat(executor.captured).isNotNull();
    }

    private static final class RecordingChatService extends ChatServiceImpl {

        private final RecordingSseEmitter emitter;

        private RecordingChatService(
                ChatSessionMapper sessionMapper,
                ChatMessageMapper messageMapper,
                SubjectMapper subjectMapper,
                KnowledgePointMapper knowledgePointMapper,
                KnowledgeContextBuilder contextBuilder,
                LlmService llmService,
                TaskExecutor chatTaskExecutor,
                TaskScheduler chatHeartbeatScheduler,
                AtomicInteger sequence
        ) {
            super(
                    sessionMapper,
                    messageMapper,
                    subjectMapper,
                    knowledgePointMapper,
                    contextBuilder,
                    llmService,
                    chatTaskExecutor,
                    chatHeartbeatScheduler,
                    new ActiveStreamRegistry()
            );
            this.emitter = new RecordingSseEmitter(sequence);
        }

        @Override
        protected SseEmitter createEmitter() {
            return emitter;
        }
    }

    private static final class RecordingTaskExecutor implements TaskExecutor {

        private final AtomicInteger sequence;
        private Runnable captured;
        private int dispatchOrder;

        private RecordingTaskExecutor(AtomicInteger sequence) {
            this.sequence = sequence;
        }

        @Override
        public void execute(Runnable task) {
            this.captured = task;
            this.dispatchOrder = sequence.incrementAndGet();
        }
    }

    private static final class RecordingSseEmitter extends SseEmitter {

        private final AtomicInteger sequence;
        private int metaOrder;

        private RecordingSseEmitter(AtomicInteger sequence) {
            super(180000L);
            this.sequence = sequence;
        }

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            this.metaOrder = sequence.incrementAndGet();
        }
    }
}
