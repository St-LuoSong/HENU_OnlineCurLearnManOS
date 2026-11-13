package com.onlinecurlearnmanos.ai.provider;

import com.onlinecurlearnmanos.ai.dto.LlmChatMessage;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenAiChatRequest(
        String model,
        List<LlmChatMessage> messages,
        Double temperature,
        Boolean stream,
        @JsonProperty("stream_options") StreamOptions streamOptions
) {

    public record StreamOptions(
            @JsonProperty("include_usage") boolean includeUsage
    ) {
    }

    public static OpenAiChatRequest of(
            String model,
            List<LlmChatMessage> messages,
            Double temperature
    ) {
        return new OpenAiChatRequest(model, messages, temperature, null, null);
    }

    public static OpenAiChatRequest streaming(
            String model,
            List<LlmChatMessage> messages,
            Double temperature
    ) {
        return new OpenAiChatRequest(
                model,
                messages,
                temperature,
                true,
                new StreamOptions(true)
        );
    }
}
