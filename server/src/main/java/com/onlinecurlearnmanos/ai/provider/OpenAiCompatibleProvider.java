package com.onlinecurlearnmanos.ai.provider;

import com.onlinecurlearnmanos.ai.dto.LlmChatMessage;
import com.onlinecurlearnmanos.ai.dto.LlmChatRequest;
import com.onlinecurlearnmanos.ai.dto.LlmChatResponse;
import com.onlinecurlearnmanos.ai.dto.LlmStreamDelta;
import com.onlinecurlearnmanos.ai.dto.LlmUsage;
import com.onlinecurlearnmanos.ai.entity.LlmProviderConfig;
import com.onlinecurlearnmanos.common.error.ErrorCode;
import com.onlinecurlearnmanos.common.exception.BusinessException;
import com.onlinecurlearnmanos.common.exception.StreamCancelledException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;

@Component
public class OpenAiCompatibleProvider implements LlmProvider {

    public static final String ADAPTER_CODE = "openai-compatible";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public OpenAiCompatibleProvider(
            @Value("${llm.timeout-seconds:60}") long timeoutSeconds,
            ObjectMapper objectMapper
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
        this.objectMapper = objectMapper;
    }

    @Override
    public String adapterCode() {
        return ADAPTER_CODE;
    }

    @Override
    public LlmChatResponse chat(
            LlmProviderConfig providerConfig,
            String apiKey,
            LlmChatRequest request
    ) {
        requireApiKey(apiKey);
        String baseUrl = normalizeBaseUrl(providerConfig.getBaseUrl());
        String model = resolveModel(providerConfig, request);
        OpenAiChatRequest providerRequest = OpenAiChatRequest.of(
                model,
                buildMessages(request),
                request.temperature() == null ? 0.7 : request.temperature()
        );

        try {
            OpenAiChatResponse response = restClient.post()
                    .uri(baseUrl + "/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .body(providerRequest)
                    .retrieve()
                    .body(OpenAiChatResponse.class);
            if (response == null || response.choices() == null || response.choices().isEmpty()) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "LLM 返回结果为空");
            }
            String content = response.choices().get(0).message() == null
                    ? ""
                    : response.choices().get(0).message().content();
            LlmUsage usage = response.usage() == null
                    ? new LlmUsage(null, null, null)
                    : new LlmUsage(
                            response.usage().promptTokens(),
                            response.usage().completionTokens(),
                            response.usage().totalTokens()
                    );
            return new LlmChatResponse(
                    content == null ? "" : content,
                    model,
                    providerConfig.getCode(),
                    usage,
                    request.requestId()
            );
        } catch (RestClientResponseException exception) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "LLM 调用失败，HTTP " + exception.getStatusCode().value()
            );
        }
    }

    /**
     * 真流式：请求体带 {@code stream: true}，边读边把增量交给调用方，最后返回累计结果。
     */
    @Override
    public LlmChatResponse chatStream(
            LlmProviderConfig providerConfig,
            String apiKey,
            LlmChatRequest request,
            Consumer<LlmStreamDelta> onDelta,
            BooleanSupplier cancelled
    ) {
        requireApiKey(apiKey);
        String baseUrl = normalizeBaseUrl(providerConfig.getBaseUrl());
        String model = resolveModel(providerConfig, request);
        OpenAiChatRequest providerRequest = OpenAiChatRequest.streaming(
                model,
                buildMessages(request),
                request.temperature() == null ? 0.7 : request.temperature()
        );

        try {
            return restClient.post()
                    .uri(baseUrl + "/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .header("Authorization", "Bearer " + apiKey)
                    .body(providerRequest)
                    .exchange((clientRequest, clientResponse) -> {
                        if (clientResponse.getStatusCode().isError()) {
                            throw new BusinessException(
                                    ErrorCode.INTERNAL_ERROR,
                                    "LLM 调用失败，HTTP "
                                            + clientResponse.getStatusCode().value()
                            );
                        }
                        OpenAiStreamReader reader =
                                new OpenAiStreamReader(objectMapper, onDelta);
                        try (BufferedReader bufferedReader = new BufferedReader(
                                new InputStreamReader(
                                        clientResponse.getBody(),
                                        StandardCharsets.UTF_8
                                )
                        )) {
                            String line;
                            while ((line = bufferedReader.readLine()) != null) {
                                if (cancelled.getAsBoolean()) {
                                    throw new StreamCancelledException("客户端已停止生成");
                                }
                                if (reader.acceptLine(line)) {
                                    break;
                                }
                            }
                        }
                        LlmChatResponse response = reader.toResponse(
                                model,
                                providerConfig.getCode(),
                                request.requestId()
                        );
                        if (!StringUtils.hasText(response.content())) {
                            throw new BusinessException(
                                    ErrorCode.INTERNAL_ERROR,
                                    "LLM 流式返回为空"
                            );
                        }
                        return response;
                    });
        } catch (RestClientResponseException exception) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "LLM 调用失败，HTTP " + exception.getStatusCode().value()
            );
        }
    }

    private void requireApiKey(String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "请先在管理员设置中配置该供应商的 API Key"
            );
        }
    }

    private String resolveModel(LlmProviderConfig providerConfig, LlmChatRequest request) {
        return StringUtils.hasText(request.model())
                ? request.model().trim()
                : providerConfig.getDefaultModel();
    }

    private List<LlmChatMessage> buildMessages(LlmChatRequest request) {
        List<LlmChatMessage> messages = new ArrayList<>();
        if (StringUtils.hasText(request.systemPrompt())) {
            messages.add(new LlmChatMessage("system", request.systemPrompt().trim()));
        }
        messages.add(new LlmChatMessage("user", request.message().trim()));
        return messages;
    }

    private String normalizeBaseUrl(String baseUrl) {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
