package com.onlinecurlearnmanos.ai.service;

import com.onlinecurlearnmanos.ai.dto.LlmChatRequest;
import com.onlinecurlearnmanos.ai.dto.LlmChatResponse;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * AI 调用与积分的时序契约：先抢占调用记录、再预占积分、业务落地成功后才确认扣费，
 * 供应商失败或业务回调失败都必须退款。
 */
@ExtendWith(MockitoExtension.class)
class LlmBillingLifecycleTest {

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
    void confirmsChargeOnlyAfterBusinessActionSucceeds() {
        stubUpstreamReady();
        given(provider.chat(any(), any(), any())).willReturn(response());

        String result = llmService.execute(
                USER_ID,
                request(),
                "CHAT",
                "问答扣费",
                chatResponse -> "ok:" + chatResponse.content()
        );

        assertThat(result).isEqualTo("ok:模型回答");
        verify(pointService).confirm(TRANSACTION_ID);
        verify(pointService, never()).refund(anyLong(), anyString());
    }

    @Test
    void refundsReservedPointsWhenBusinessActionFails() {
        stubUpstreamReady();
        given(provider.chat(any(), any(), any())).willReturn(response());

        assertThatThrownBy(() -> llmService.execute(
                USER_ID,
                request(),
                "CHAT",
                "问答扣费",
                chatResponse -> {
                    throw new IllegalStateException("结构化解析失败");
                }
        )).isInstanceOf(IllegalStateException.class);

        verify(pointService).refund(TRANSACTION_ID, "业务处理失败退款");
        verify(pointService, never()).confirm(anyLong());
    }

    @Test
    void refundsReservedPointsWhenProviderCallFails() {
        stubUpstreamReady();
        given(provider.chat(any(), any(), any())).willThrow(new IllegalStateException("上游超时"));

        assertThatThrownBy(() -> llmService.execute(
                USER_ID,
                request(),
                "CHAT",
                "问答扣费",
                chatResponse -> "ok"
        )).isInstanceOf(IllegalStateException.class);

        verify(pointService).refund(TRANSACTION_ID, "LLM 调用失败退款");
        verify(pointService, never()).confirm(anyLong());
    }

    @Test
    void propagatesReservationFailureWithoutChargingOrRefunding() {
        stubUpstreamReady();
        given(pointService.reserve(any(), anyInt(), any(), any(), any(), any(), any()))
                .willThrow(new BusinessException(ErrorCode.CONFLICT, "积分不足或并发扣减冲突"));

        assertThatThrownBy(() -> llmService.execute(
                USER_ID,
                request(),
                "CHAT",
                "问答扣费",
                chatResponse -> "ok"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("积分不足");

        verify(pointService, never()).confirm(anyLong());
        verify(pointService, never()).refund(anyLong(), anyString());
    }

    @Test
    void rejectsDuplicateIdempotencyKeyBeforeTouchingPoints() {
        LlmCallRecord existing = new LlmCallRecord();
        existing.setId(RECORD_ID);
        existing.setIdempotencyKey("chat:key-1");
        existing.setStatus("SUCCESS");
        given(callRecordMapper.selectOne(any())).willReturn(existing);

        assertThatThrownBy(() -> llmService.execute(
                USER_ID,
                request(),
                "CHAT",
                "问答扣费",
                chatResponse -> "ok"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已完成");

        verify(pointService, never()).reserve(any(), anyInt(), any(), any(), any(), any(), any());
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

    private LlmChatResponse response() {
        return new LlmChatResponse("模型回答", "gpt-4o-mini", "openai", null, "req-1");
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
