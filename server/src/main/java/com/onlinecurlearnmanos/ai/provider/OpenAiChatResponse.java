package com.onlinecurlearnmanos.ai.provider;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record OpenAiChatResponse(
        List<Choice> choices,
        Usage usage
) {

    public record Choice(
            Message message
    ) {
    }

    public record Message(
            String content
    ) {
    }

    public record Usage(
            @JsonProperty("prompt_tokens") Integer promptTokens,
            @JsonProperty("completion_tokens") Integer completionTokens,
            @JsonProperty("total_tokens") Integer totalTokens
    ) {
    }
}
