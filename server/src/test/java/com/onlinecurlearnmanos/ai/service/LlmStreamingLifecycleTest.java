package com.onlinecurlearnmanos.ai.service;

import com.onlinecurlearnmanos.ai.dto.LlmChatRequest;
import com.onlinecurlearnmanos.ai.dto.LlmChatResponse;
import com.onlinecurlearnmanos.ai.dto.LlmStreamDelta;
import com.onlinecurlearnmanos.ai.dto.LlmUsage;
import com.onlinecurlearnmanos.ai.entity.LlmCallRecord;
import com.onlinecurlearnmanos.ai.entity.LlmModel;
import com.onlinecurlearnmanos.ai.entity.LlmProviderConfig;
import com.onlinecurlearnmanos.ai.mapper.LlmCallRecordMapper;
import com.onlinecurlearnmanos.ai.mapper.LlmProviderMapper;
import com.onlinecurlearnmanos.ai.provider.LlmProvider;
import com.onlinecurlearnmanos.ai.provider.LlmProviderRegistry;
import com.onlinecurlearnmanos.common.error.ErrorCode;
import com.onlinecurlearnmanos.common.exception.BusinessException;
import com.onlinecurlearnmanos.common.security.CredentialCipher;
import com.onlinecurlearnmanos.point.entity.PointTransaction;
import com.onlinecurlearnmanos.point.service.PointService;
import com.onlinecurlearnmanos.support.MybatisLambdaCache;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 流式调用的计费时序：增量先推给调用方（SSE），全部产出后才落库并确认扣费；
 * 上游中断、客户端断开、落库失败都必须退款。
 */
@ExtendWith(MockitoExtension.class)
class LlmStreamingLifecycleTest {

    private static final Long USER_ID = 31L;
    private static final Long RECORD_ID = 7L;
    private static final Long TRANSACTION_ID = 99L;

    @Mock
    private LlmProviderRegistry providerRegistry;

    @Mock
    private LlmProviderMapper providerMapper;

    @Mock
    private LlmCallRecordMapper callRecordMapper;

    @Mock
    private PointService pointService;

    @Mock
    private CredentialCipher credentialCipher;

    @Mock
    private UserAiPreferenceService preferenceService;

    @Mock
    private LlmProvider provider;

    @InjectMocks
    private LlmService llmService;

    @BeforeAll
    static void registerLambdaCache() {
        MybatisLambdaCache.register(LlmCallRecord.class);
    }

    @Test
    void forwardsDeltasBeforePersistingAndConfirmsCharge() {
        stubUpstreamReady();
        List<String> events = new ArrayList<>();
        given(provider.chatStream(any(), any(), any(), any())).willAnswer(invocation -> {
            Consumer<LlmStreamDelta> onDelta = invocation.getArgument(3);
            onDelta.accept(LlmStreamDelta.answer("你"));
            events.add("provider:你");
            onDelta.accept(LlmStreamDelta.answer("好"));
            events.add("provider:好");
            return new LlmChatResponse(
                    "你好",
                    "gpt-4o-mini",
                    "openai",
                    new LlmUsage(1, 2, 3),
                    "req-1"
            );
        });

        String result = llmService.executeStream(
                USER_ID,
                request(),
                "AI_CHAT",
                "智能问答调用",
                delta -> events.add("sse:" + delta.content()),
                response -> {
                    events.add("persist:" + response.content());
                    return response.content();
                }
        );

        assertThat(result).isEqualTo("你好");
        assertThat(events).containsExactly(
                "sse:你",
                "provider:你",
                "sse:好",
                "provider:好",
                "persist:你好"
        );
        verify(pointService).confirm(TRANSACTION_ID);
        verify(pointService, never()).refund(anyLong(), anyString());
    }

    @Test
    void refundsWhenProviderFailsMidStream() {
        stubUpstreamReady();
        List<LlmStreamDelta> forwarded = new ArrayList<>();
        given(provider.chatStream(any(), any(), any(), any())).willAnswer(invocation -> {
            Consumer<LlmStreamDelta> onDelta = invocation.getArgument(3);
            onDelta.accept(LlmStreamDelta.answer("半"));
            throw new IllegalStateException("上游中断");
        });

        assertThatThrownBy(() -> llmService.executeStream(
                USER_ID,
                request(),
                "AI_CHAT",
                "智能问答调用",
                forwarded::add,
                response -> "ok"
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("上游中断");

        assertThat(forwarded).containsExactly(LlmStreamDelta.answer("半"));
        verify(pointService).refund(TRANSACTION_ID, "LLM 调用失败退款");
        verify(pointService, never()).confirm(anyLong());
    }

    @Test
    void refundsWhenClientDisconnectsDuringStream() {
        stubUpstreamReady();
        given(provider.chatStream(any(), any(), any(), any())).willAnswer(invocation -> {
            Consumer<LlmStreamDelta> onDelta = invocation.getArgument(3);
            onDelta.accept(LlmStreamDelta.answer("第一段"));
            onDelta.accept(LlmStreamDelta.answer("第二段"));
            return new LlmChatResponse("第一段第二段", "gpt-4o-mini", "openai", null, "req-1");
        });
        Consumer<LlmStreamDelta> disconnectedClient = delta -> {
            throw new BusinessException(ErrorCode.CONFLICT, "客户端已断开，本次流式回答已终止");
        };

        assertThatThrownBy(() -> llmService.executeStream(
                USER_ID,
                request(),
                "AI_CHAT",
                "智能问答调用",
                disconnectedClient,
                response -> "ok"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("客户端已断开");

        verify(pointService).refund(eq(TRANSACTION_ID), anyString());
        verify(pointService, never()).confirm(anyLong());
    }

    @Test
    void refundsWithBusinessReasonWhenPersistenceFailsAfterStream() {
        stubUpstreamReady();
        given(provider.chatStream(any(), any(), any(), any())).willReturn(
                new LlmChatResponse("完整回答", "gpt-4o-mini", "openai", null, "req-1")
        );

        assertThatThrownBy(() -> llmService.executeStream(
                USER_ID,
                request(),
                "AI_CHAT",
                "智能问答调用",
                delta -> {
                    // 正常转发
                },
                response -> {
                    throw new BusinessException(ErrorCode.INTERNAL_ERROR, "消息落库失败");
                }
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("消息落库失败");

        verify(pointService).refund(TRANSACTION_ID, "业务处理失败退款");
        verify(pointService, never()).confirm(anyLong());
    }

    private void stubUpstreamReady() {
        given(callRecordMapper.selectOne(any())).willReturn(null);
        given(preferenceService.resolve(USER_ID))
                .willReturn(new ResolvedModelSelection(providerConfig(), model(), 20));
        given(credentialCipher.decrypt(any(), any())).willReturn("sk-test-key");
        given(providerRegistry.getRequiredAdapter("openai-compatible")).willReturn(provider);
        given(callRecordMapper.insert(any(LlmCallRecord.class))).willAnswer(invocation -> {
            LlmCallRecord record = invocation.getArgument(0);
            record.setId(RECORD_ID);
            return 1;
        });
        given(pointService.reserve(any(), anyInt(), any(), any(), any(), any(), any()))
                .willReturn(reservedTransaction());
    }

    private LlmChatRequest request() {
        return new LlmChatRequest(
                "什么是导数",
                "你是学习助教",
                null,
                0.3,
                "chat:key-1",
                "req-1"
        );
    }

    private LlmProviderConfig providerConfig() {
        LlmProviderConfig config = new LlmProviderConfig();
        config.setId(3L);
        config.setCode("openai");
        config.setAdapterCode("openai-compatible");
        config.setEncryptedApiKey("cipher");
        config.setEncryptionIv("iv");
        return config;
    }

    private LlmModel model() {
        LlmModel model = new LlmModel();
        model.setId(5L);
        model.setProviderId(3L);
        model.setModelCode("gpt-4o-mini");
        model.setPointCost(20);
        return model;
    }

    private PointTransaction reservedTransaction() {
        PointTransaction transaction = new PointTransaction();
        transaction.setId(TRANSACTION_ID);
        transaction.setAccountId(7L);
        transaction.setUserId(USER_ID);
        transaction.setAmount(20);
        transaction.setStatus("RESERVED");
        return transaction;
    }
}
