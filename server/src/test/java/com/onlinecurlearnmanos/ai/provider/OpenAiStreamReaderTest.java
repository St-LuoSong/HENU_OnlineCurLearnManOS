package com.onlinecurlearnmanos.ai.provider;

import com.onlinecurlearnmanos.ai.dto.LlmChatResponse;
import com.onlinecurlearnmanos.ai.dto.LlmStreamDelta;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OpenAI 兼容流式分片的解析契约：增量透传、忽略心跳与空行、捕获 usage、
 * 单条脏数据不影响后续内容。
 */
class OpenAiStreamReaderTest {

    private final List<LlmStreamDelta> deltas = new ArrayList<>();
    private final OpenAiStreamReader reader =
            new OpenAiStreamReader(new ObjectMapper(), deltas::add);

    @Test
    void forwardsDeltasAndAggregatesFullContent() {
        reader.acceptLine(
                "data: {\"model\":\"gpt-4o-mini\",\"choices\":[{\"delta\":{\"role\":\"assistant\"},"
                        + "\"finish_reason\":null}]}"
        );
        reader.acceptLine("");
        reader.acceptLine(": keep-alive");
        reader.acceptLine("event: message");
        assertThat(reader.acceptLine(
                "data: {\"choices\":[{\"delta\":{\"content\":\"你\"}}]}"
        )).isFalse();
        reader.acceptLine(
                "data: {\"choices\":[{\"delta\":{\"content\":\"好\"},\"finish_reason\":\"stop\"}]}"
        );
        assertThat(reader.acceptLine("data: [DONE]")).isTrue();

        assertThat(deltas).containsExactly(
                LlmStreamDelta.answer("你"),
                LlmStreamDelta.answer("好")
        );
        LlmChatResponse response = reader.toResponse("fallback-model", "openai", "req-1");
        assertThat(response.content()).isEqualTo("你好");
        assertThat(response.model()).isEqualTo("gpt-4o-mini");
        assertThat(response.providerCode()).isEqualTo("openai");
        assertThat(response.requestId()).isEqualTo("req-1");
    }

    @Test
    void capturesUsageFromTrailingChunk() {
        reader.acceptLine("data: {\"choices\":[{\"delta\":{\"content\":\"答\"}}]}");
        reader.acceptLine(
                "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":11,"
                        + "\"completion_tokens\":7,\"total_tokens\":18}}"
        );

        LlmChatResponse response = reader.toResponse("m", "openai", "r");

        assertThat(response.usage()).isNotNull();
        assertThat(response.usage().promptTokens()).isEqualTo(11);
        assertThat(response.usage().completionTokens()).isEqualTo(7);
        assertThat(response.usage().totalTokens()).isEqualTo(18);
    }

    @Test
    void skipsMalformedChunkWithoutLosingLaterContent() {
        reader.acceptLine("data: {oops");
        reader.acceptLine("data: {\"choices\":[{\"delta\":{\"content\":\"后\"}}]}");

        assertThat(deltas).containsExactly(LlmStreamDelta.answer("后"));
        assertThat(reader.toResponse("m", "openai", "r").content()).isEqualTo("后");
    }

    @Test
    void separatesDeepSeekReasoningFromFinalAnswer() {
        reader.acceptLine(
                "data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"先分析题意\"}}]}"
        );
        reader.acceptLine(
                "data: {\"choices\":[{\"delta\":{\"content\":\"最终答案\"}}]}"
        );

        assertThat(deltas).containsExactly(
                LlmStreamDelta.reasoning("先分析题意"),
                LlmStreamDelta.answer("最终答案")
        );
        assertThat(reader.toResponse("m", "deepseek", "r").content())
                .isEqualTo("最终答案");
    }

    @Test
    void keepsWhitespaceOnlyDeltas() {
        reader.acceptLine("data: {\"choices\":[{\"delta\":{\"content\":\"## 要点\"}}]}");
        reader.acceptLine("data: {\"choices\":[{\"delta\":{\"content\":\"\\n\"}}]}");
        reader.acceptLine("data: {\"choices\":[{\"delta\":{\"content\":\"  \"}}]}");
        reader.acceptLine("data: {\"choices\":[{\"delta\":{\"content\":\"代码\"}}]}");

        assertThat(deltas).containsExactly(
                LlmStreamDelta.answer("## 要点"),
                LlmStreamDelta.answer("\n"),
                LlmStreamDelta.answer("  "),
                LlmStreamDelta.answer("代码")
        );
        assertThat(reader.toResponse("m", "openai", "r").content())
                .isEqualTo("## 要点\n  代码");
    }

    @Test
    void ignoresNonDataLinesAndBlankPayloads() {
        reader.acceptLine("id: 3");
        reader.acceptLine("data:");
        reader.acceptLine("data:    ");
        reader.acceptLine(null);

        assertThat(deltas).isEmpty();
        assertThat(reader.toResponse("m", "openai", "r").content()).isEmpty();
    }

    @Test
    void fallsBackToRequestedModelWhenProviderOmitsIt() {
        reader.acceptLine("data: {\"choices\":[{\"delta\":{\"content\":\"x\"}}]}");

        assertThat(reader.toResponse("gpt-4o-mini", "openai", "r").model())
                .isEqualTo("gpt-4o-mini");
    }
}
