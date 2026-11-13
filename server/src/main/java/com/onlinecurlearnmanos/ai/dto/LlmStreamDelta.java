package com.onlinecurlearnmanos.ai.dto;

/**
 * LLM 流式增量。reasoning 只用于前端展示推理过程，不进入最终答案。
 */
public record LlmStreamDelta(
        String phase,
        String content
) {

    public static final String PHASE_REASONING = "reasoning";
    public static final String PHASE_ANSWER = "answer";

    public static LlmStreamDelta reasoning(String content) {
        return new LlmStreamDelta(PHASE_REASONING, content);
    }

    public static LlmStreamDelta answer(String content) {
        return new LlmStreamDelta(PHASE_ANSWER, content);
    }
}
