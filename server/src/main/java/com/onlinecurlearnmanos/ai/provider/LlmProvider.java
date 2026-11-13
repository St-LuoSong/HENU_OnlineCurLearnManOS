package com.onlinecurlearnmanos.ai.provider;

import com.onlinecurlearnmanos.ai.dto.LlmChatRequest;
import com.onlinecurlearnmanos.ai.dto.LlmChatResponse;
import com.onlinecurlearnmanos.ai.dto.LlmStreamDelta;
import com.onlinecurlearnmanos.ai.entity.LlmProviderConfig;

import java.util.function.Consumer;
import java.util.function.BooleanSupplier;

public interface LlmProvider {

    String adapterCode();

    LlmChatResponse chat(
            LlmProviderConfig providerConfig,
            String apiKey,
            LlmChatRequest request
    );

    /**
     * 流式调用：每收到一段增量就回调 {@code onDelta}，返回累计后的完整响应。
     *
     * <p>默认实现退化为一次性调用并把整段内容当成单次增量下发，
     * 供暂不支持流式的适配器复用；OpenAI 兼容适配器会覆写为真流式。</p>
     */
    default LlmChatResponse chatStream(
            LlmProviderConfig providerConfig,
            String apiKey,
            LlmChatRequest request,
            Consumer<LlmStreamDelta> onDelta
    ) {
        return chatStream(providerConfig, apiKey, request, onDelta, () -> false);
    }

    /**
     * 支持协作式取消的流式调用。适配器应在每个增量之间检查取消信号，
     * 已停止的请求不再继续读取上游。
     */
    default LlmChatResponse chatStream(
            LlmProviderConfig providerConfig,
            String apiKey,
            LlmChatRequest request,
            Consumer<LlmStreamDelta> onDelta,
            BooleanSupplier cancelled
    ) {
        if (cancelled.getAsBoolean()) {
            throw new com.onlinecurlearnmanos.common.exception.StreamCancelledException("客户端已停止生成");
        }
        LlmChatResponse response = chat(providerConfig, apiKey, request);
        if (cancelled.getAsBoolean()) {
            throw new com.onlinecurlearnmanos.common.exception.StreamCancelledException("客户端已停止生成");
        }
        if (response.content() != null && !response.content().isEmpty()) {
            onDelta.accept(LlmStreamDelta.answer(response.content()));
        }
        return response;
    }
}
