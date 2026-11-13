package com.onlinecurlearnmanos.ai.provider;

import com.onlinecurlearnmanos.ai.dto.LlmChatResponse;
import com.onlinecurlearnmanos.ai.dto.LlmStreamDelta;
import com.onlinecurlearnmanos.ai.dto.LlmUsage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.util.function.Consumer;

/**
 * 逐行解析 OpenAI 兼容的 SSE 流式响应。
 *
 * <p>与 HTTP 解耦，便于单测覆盖"分块截断、心跳注释、usage 结尾块、非法 JSON"等边界。</p>
 */
public class OpenAiStreamReader {

    private static final Logger log = LoggerFactory.getLogger(OpenAiStreamReader.class);
    private static final String DATA_PREFIX = "data:";
    private static final String DONE_MARKER = "[DONE]";

    private final ObjectMapper objectMapper;
    private final Consumer<LlmStreamDelta> onDelta;
    private final StringBuilder content = new StringBuilder();

    private LlmUsage usage;
    private String model;

    public OpenAiStreamReader(ObjectMapper objectMapper, Consumer<LlmStreamDelta> onDelta) {
        this.objectMapper = objectMapper;
        this.onDelta = onDelta;
    }

    /**
     * 处理一行 SSE 文本；返回 true 表示收到 {@code [DONE]}，可以停止读取。
     */
    public boolean acceptLine(String rawLine) {
        if (!StringUtils.hasText(rawLine)) {
            return false;
        }
        String line = rawLine.trim();
        if (line.startsWith(":") || !line.startsWith(DATA_PREFIX)) {
            return false;
        }
        String payload = line.substring(DATA_PREFIX.length()).trim();
        if (payload.isEmpty()) {
            return false;
        }
        if (DONE_MARKER.equals(payload)) {
            return true;
        }

        OpenAiChatStreamChunk chunk;
        try {
            chunk = objectMapper.readValue(payload, OpenAiChatStreamChunk.class);
        } catch (Exception exception) {
            log.debug("Skip unparsable stream chunk: {}", payload);
            return false;
        }
        if (StringUtils.hasText(chunk.model())) {
            model = chunk.model();
        }
        if (chunk.usage() != null) {
            usage = new LlmUsage(
                    chunk.usage().promptTokens(),
                    chunk.usage().completionTokens(),
                    chunk.usage().totalTokens()
            );
        }
        if (chunk.choices() == null || chunk.choices().isEmpty()) {
            return false;
        }
        OpenAiChatStreamChunk.Delta delta = chunk.choices().get(0).delta();
        // 空白增量（换行、缩进）同样是回答的一部分，不能因 hasText 判定被丢弃。
        if (delta != null && StringUtils.hasLength(delta.reasoningContent())) {
            onDelta.accept(LlmStreamDelta.reasoning(delta.reasoningContent()));
        }
        String piece = delta == null ? null : delta.content();
        if (StringUtils.hasLength(piece)) {
            content.append(piece);
            onDelta.accept(LlmStreamDelta.answer(piece));
        }
        return false;
    }

    public LlmChatResponse toResponse(String fallbackModel, String providerCode, String requestId) {
        return new LlmChatResponse(
                content.toString(),
                StringUtils.hasText(model) ? model : fallbackModel,
                providerCode,
                usage,
                requestId
        );
    }
}
