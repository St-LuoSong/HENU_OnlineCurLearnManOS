package com.onlinecurlearnmanos.chat;

import com.onlinecurlearnmanos.ai.dto.LlmChatRequest;
import com.onlinecurlearnmanos.ai.dto.LlmChatResponse;
import com.onlinecurlearnmanos.ai.dto.LlmStreamDelta;
import com.onlinecurlearnmanos.ai.entity.LlmProviderConfig;
import com.onlinecurlearnmanos.ai.provider.OpenAiCompatibleProvider;
import com.onlinecurlearnmanos.chat.controller.ChatController;
import com.onlinecurlearnmanos.chat.dto.ChatMessageSendRequest;
import com.onlinecurlearnmanos.chat.service.ChatService;
import com.onlinecurlearnmanos.security.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * 真实 HTTP 上游的流式回归：DeepSeek Chat / Reasoner 的 content、reasoning_content、
 * 脏块、无尾空行和客户端中断都必须符合既定契约。
 */
class DeepSeekStreamingIntegrationTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void receivesReasoningAndMultipleAnswerDeltasBeforeDone() throws Exception {
        List<TimedDelta> events = new ArrayList<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", this::streamUpstream);
        server.start();

        OpenAiCompatibleProvider provider = new OpenAiCompatibleProvider(30, new ObjectMapper());
        LlmChatResponse response = provider.chatStream(
                providerConfig(server.getAddress().getPort()),
                "sk-test",
                request(),
                delta -> events.add(new TimedDelta(delta, System.nanoTime()))
        );
        long completedAt = System.nanoTime();

        assertThat(response.content()).isEqualTo("第一段第二段");
        assertThat(events).extracting(TimedDelta::delta).containsExactly(
                LlmStreamDelta.reasoning("先分析题意"),
                LlmStreamDelta.answer("第一段"),
                LlmStreamDelta.answer("第二段")
        );
        assertThat(events).allSatisfy(event -> assertThat(event.at()).isLessThan(completedAt));
        assertThat(events.get(2).at() - events.get(1).at()).isGreaterThan(80_000_000L);
    }

    @Test
    void propagatesClientInterruptionDuringStream() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", this::streamUpstream);
        server.start();
        OpenAiCompatibleProvider provider = new OpenAiCompatibleProvider(30, new ObjectMapper());
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> provider.chatStream(
                providerConfig(server.getAddress().getPort()),
                "sk-test",
                request(),
                delta -> {
                    calls.incrementAndGet();
                    throw new IllegalStateException("浏览器断开");
                }
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("浏览器断开");
        assertThat(calls).hasValue(1);
    }

    @Test
    void controllerAddsProxySafeSseHeaders() {
        ChatService chatService = mock(ChatService.class);
        given(chatService.streamMessage(anyLong(), anyLong(), any())).willReturn(new SseEmitter());
        ChatController controller = new ChatController(chatService);
        UserPrincipal principal = new UserPrincipal(
                1L,
                "2024001",
                "",
                "学生",
                null,
                1,
                List.of("STUDENT")
        );

        ResponseEntity<SseEmitter> response = controller.streamMessage(
                9L,
                new ChatMessageSendRequest("你好", false, "STANDARD"),
                principal
        );

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().toString())
                .startsWith("text/event-stream");
        assertThat(response.getHeaders().getCacheControl())
                .contains("no-cache", "no-transform");
        assertThat(response.getHeaders().getFirst("X-Accel-Buffering")).isEqualTo("no");
    }

    private void streamUpstream(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "text/event-stream; charset=UTF-8");
        exchange.sendResponseHeaders(200, 0);
        try (OutputStream output = exchange.getResponseBody()) {
            write(output, "data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"先分析题意\"}}]}\n\n");
            Thread.sleep(150L);
            write(output, "data: {not-json\n\n");
            write(output, "data: {\"choices\":[{\"delta\":{\"content\":\"第一段\"}}]}\n\n");
            Thread.sleep(150L);
            write(output, "data: {\"choices\":[{\"delta\":{\"content\":\"第二段\"}}]}\n\n");
            Thread.sleep(150L);
            write(output, "data: [DONE]");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("mock upstream interrupted", exception);
        }
    }

    private void write(OutputStream output, String payload) throws IOException {
        output.write(payload.getBytes(StandardCharsets.UTF_8));
        output.flush();
    }

    private LlmProviderConfig providerConfig(int port) {
        LlmProviderConfig config = new LlmProviderConfig();
        config.setCode("deepseek");
        config.setAdapterCode(OpenAiCompatibleProvider.ADAPTER_CODE);
        config.setBaseUrl("http://127.0.0.1:" + port + "/v1");
        config.setDefaultModel("deepseek-reasoner");
        return config;
    }

    private LlmChatRequest request() {
        return new LlmChatRequest(
                "什么是导数",
                "你是学习助教",
                null,
                0.3,
                "chat:test-key",
                "req-test"
        );
    }

    private record TimedDelta(LlmStreamDelta delta, long at) {
    }
}
