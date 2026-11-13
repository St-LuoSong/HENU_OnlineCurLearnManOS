package com.onlinecurlearnmanos.ai.service;

import com.onlinecurlearnmanos.ai.dto.LlmChatRequest;
import com.onlinecurlearnmanos.ai.dto.LlmChatResponse;
import com.onlinecurlearnmanos.ai.dto.LlmStreamDelta;
import com.onlinecurlearnmanos.ai.dto.LlmUsage;
import com.onlinecurlearnmanos.ai.dto.AiMode;
import com.onlinecurlearnmanos.ai.entity.LlmCallRecord;
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
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import java.util.UUID;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class LlmService {

    private static final Logger log = LoggerFactory.getLogger(LlmService.class);

    private final LlmProviderRegistry providerRegistry;
    private final LlmProviderMapper providerMapper;
    private final LlmCallRecordMapper callRecordMapper;
    private final PointService pointService;
    private final CredentialCipher credentialCipher;
    private final UserAiPreferenceService preferenceService;
    private final AiAccessService aiAccessService;

    /**
     * 唯一的 AI 计费入口。执行顺序：
     *
     * <ol>
     *     <li>原子抢占调用执行权（同一幂等键只允许一个请求进入）；</li>
     *     <li>预占积分；</li>
     *     <li>调用供应商；</li>
     *     <li>执行业务回调（解析、校验、持久化）；</li>
     *     <li>业务成功后才确认扣费，任意环节失败都退回预占积分。</li>
     * </ol>
     *
     * 这样"页面提示失败但积分已扣"不会发生，业务落库失败也不会让用户付费。
     */
    public <T> T execute(
            Long userId,
            LlmChatRequest request,
            String transactionType,
            String transactionRemark,
            Function<LlmChatResponse, T> businessAction
    ) {
        return execute(userId, request, null, transactionType, transactionRemark, businessAction);
    }

    public <T> T execute(
            Long userId,
            LlmChatRequest request,
            AiMode mode,
            String transactionType,
            String transactionRemark,
            Function<LlmChatResponse, T> businessAction
    ) {
        return executeWithBilling(
                userId,
                request,
                mode,
                transactionType,
                transactionRemark,
                businessAction,
                (provider, providerConfig, apiKey, providerRequest, cancelled) ->
                        provider.chat(providerConfig, apiKey, providerRequest)
        );
    }

    /**
     * 流式版本：供应商每产出一段增量立即回调 {@code onDelta}（用于 SSE 转发），
     * 全部产出结束后再执行落库回调；成功才确认扣费，任意环节失败退款。
     */
    public <T> T executeStream(
            Long userId,
            LlmChatRequest request,
            String transactionType,
            String transactionRemark,
            Consumer<LlmStreamDelta> onDelta,
            Function<LlmChatResponse, T> businessAction
    ) {
        return executeWithBilling(
                userId,
                request,
                null,
                transactionType,
                transactionRemark,
                businessAction,
                (provider, providerConfig, apiKey, providerRequest, cancelCheck) ->
                        provider.chatStream(providerConfig, apiKey, providerRequest, onDelta)
        );
    }

    public <T> T executeStream(
            Long userId,
            LlmChatRequest request,
            AiMode mode,
            String transactionType,
            String transactionRemark,
            Consumer<LlmStreamDelta> onDelta,
            Function<LlmChatResponse, T> businessAction,
            BooleanSupplier cancelled
    ) {
        return executeWithBilling(
                userId,
                request,
                mode,
                transactionType,
                transactionRemark,
                businessAction,
                (provider, providerConfig, apiKey, providerRequest, cancelCheck) ->
                        provider.chatStream(
                                providerConfig,
                                apiKey,
                                providerRequest,
                                onDelta,
                                cancelled
                        )
        );
    }

    public <T> T executeStream(
            Long userId,
            LlmChatRequest request,
            String transactionType,
            String transactionRemark,
            Consumer<LlmStreamDelta> onDelta,
            Function<LlmChatResponse, T> businessAction,
            BooleanSupplier cancelled
    ) {
        return executeWithBilling(
                userId,
                request,
                null,
                transactionType,
                transactionRemark,
                businessAction,
                (provider, providerConfig, apiKey, providerRequest, cancelCheck) ->
                        provider.chatStream(providerConfig, apiKey, providerRequest, onDelta)
        );
    }

    /**
     * 供应商调用抽象：非流式与流式共用同一套
     * 「抢占调用记录 → 预占积分 → 调用供应商 → 业务落库 → 确认扣费 / 退款」。
     */
    @FunctionalInterface
    private interface ProviderCall {

        LlmChatResponse call(
                LlmProvider provider,
                LlmProviderConfig providerConfig,
                String apiKey,
                LlmChatRequest request,
                BooleanSupplier cancelled
        );
    }

    private <T> T executeWithBilling(
            Long userId,
            LlmChatRequest request,
            AiMode mode,
            String transactionType,
            String transactionRemark,
            Function<LlmChatResponse, T> businessAction,
            ProviderCall providerCall
    ) {
        String requestId = StringUtils.hasText(request.requestId())
                ? request.requestId().trim()
                : UUID.randomUUID().toString().replace("-", "");
        String idempotencyKey = StringUtils.hasText(request.idempotencyKey())
                ? request.idempotencyKey().trim()
                : "chat:" + userId + ":" + requestId;

        LlmCallRecord existing = findByIdempotencyKey(idempotencyKey);
        if (existing != null) {
            throw conflictFor(existing);
        }

        boolean governed = mode != null;
        AiMode billingMode = mode == null ? AiMode.EXPERT : mode;
        ResolvedModelSelection selection = governed
                ? preferenceService.resolve(userId, billingMode)
                : preferenceService.resolve(userId);
        LlmProviderConfig providerConfig = selection.provider();
        String selectedModel = selection.model().getModelCode();
        String apiKey = credentialCipher.decrypt(
                providerConfig.getEncryptedApiKey(),
                providerConfig.getEncryptionIv()
        );
        LlmProvider provider = providerRegistry.getRequiredAdapter(providerConfig.getAdapterCode());

        LlmCallRecord record = new LlmCallRecord();
        record.setUserId(userId);
        record.setProviderCode(providerConfig.getCode());
        record.setModel(selectedModel);
        record.setAccessMode(governed ? billingMode.name() : "LEGACY");
        record.setRequestId(requestId);
        record.setIdempotencyKey(idempotencyKey);
        record.setStatus("RUNNING");
        record.setCreatedAt(LocalDateTime.now());
        try {
            callRecordMapper.insert(record);
        } catch (DataIntegrityViolationException exception) {
            LlmCallRecord raced = findByIdempotencyKey(idempotencyKey);
            if (raced == null) {
                throw exception;
            }
            throw conflictFor(raced);
        }

        AiAccessService.QuotaGrant quota = null;
        if (governed) {
            try {
                quota = aiAccessService.consume(userId, billingMode);
            } catch (RuntimeException exception) {
                updateFailure(record.getId(), exception, 0L);
                throw exception;
            }
        }

        PointTransaction transaction = null;
        try {
            if (billingMode == AiMode.EXPERT) {
                transaction = pointService.reserve(
                        userId,
                        selection.pointCost(),
                        transactionType,
                        idempotencyKey,
                        transactionType,
                        requestId,
                        transactionRemark
                );
            }
        } catch (RuntimeException exception) {
            updateFailure(record.getId(), exception, 0L);
            if (quota != null && aiAccessService != null) {
                aiAccessService.release(quota);
            }
            throw exception;
        }

        long startedAt = System.nanoTime();
        LlmChatRequest providerRequest = new LlmChatRequest(
                request.message(),
                request.systemPrompt(),
                selectedModel,
                request.temperature(),
                idempotencyKey,
                requestId
        );
        LlmChatResponse response;
        try {
            response = providerCall.call(
                    provider,
                    providerConfig,
                    apiKey,
                    providerRequest,
                    () -> false
            );
        } catch (RuntimeException exception) {
            updateFailure(record.getId(), exception, elapsedMillis(startedAt));
            refundFailure(transaction, quota, "LLM 调用失败退款");
            throw exception;
        }

        T result;
        try {
            result = businessAction.apply(response);
        } catch (RuntimeException exception) {
            updateFailure(record.getId(), exception, elapsedMillis(startedAt));
            refundFailure(transaction, quota, "业务处理失败退款");
            throw exception;
        }

        long durationMs = elapsedMillis(startedAt);
        try {
            updateSuccess(record.getId(), response, durationMs);
        } catch (RuntimeException exception) {
            updateFailure(record.getId(), exception, durationMs);
            refundFailure(transaction, quota, "调用结果保存失败退款");
            throw exception;
        }

        if (transaction == null) {
            return result;
        }
        try {
            pointService.confirm(transaction.getId());
        } catch (RuntimeException exception) {
            log.error(
                    "AI call succeeded but point confirmation failed, transactionId={}",
                    transaction.getId(),
                    exception
            );
        }
        return result;
    }

    private void refundFailure(
            PointTransaction transaction,
            AiAccessService.QuotaGrant quota,
            String reason
    ) {
        if (transaction != null) {
            pointService.refund(transaction.getId(), reason);
        }
        if (quota != null && aiAccessService != null) {
            aiAccessService.release(quota);
        }
    }

    public LlmChatResponse testProvider(Long providerId, String message) {
        LlmProviderConfig providerConfig = providerMapper.selectById(providerId);
        if (providerConfig == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "LLM 供应商不存在");
        }
        String apiKey = credentialCipher.decrypt(
                providerConfig.getEncryptedApiKey(),
                providerConfig.getEncryptionIv()
        );
        LlmProvider provider = providerRegistry.getRequiredAdapter(providerConfig.getAdapterCode());
        String requestId = UUID.randomUUID().toString().replace("-", "");
        return provider.chat(
                providerConfig,
                apiKey,
                new LlmChatRequest(
                        message,
                        "你是系统连通性测试助手，请只回复测试结果。",
                        null,
                        0.0,
                        "test:" + requestId,
                        requestId
                )
        );
    }

    private BusinessException conflictFor(LlmCallRecord record) {
        if ("SUCCESS".equals(record.getStatus())) {
            return new BusinessException(ErrorCode.CONFLICT, "该请求已完成，请勿重复提交");
        }
        if ("RUNNING".equals(record.getStatus())) {
            return new BusinessException(ErrorCode.CONFLICT, "相同请求正在处理中");
        }
        return new BusinessException(ErrorCode.CONFLICT, "该请求已失败，请使用新的幂等键重试");
    }

    private LlmCallRecord findByIdempotencyKey(String idempotencyKey) {
        return callRecordMapper.selectOne(new LambdaQueryWrapper<LlmCallRecord>()
                .eq(LlmCallRecord::getIdempotencyKey, idempotencyKey)
                .last("LIMIT 1"));
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private void updateSuccess(Long id, LlmChatResponse response, long durationMs) {
        LlmUsage usage = response.usage();
        callRecordMapper.update(null, new LambdaUpdateWrapper<LlmCallRecord>()
                .eq(LlmCallRecord::getId, id)
                .set(LlmCallRecord::getStatus, "SUCCESS")
                .set(LlmCallRecord::getModel, response.model())
                .set(LlmCallRecord::getResponseContent, response.content())
                .set(LlmCallRecord::getPromptTokens, usage == null ? null : usage.promptTokens())
                .set(LlmCallRecord::getCompletionTokens, usage == null ? null : usage.completionTokens())
                .set(LlmCallRecord::getTotalTokens, usage == null ? null : usage.totalTokens())
                .set(LlmCallRecord::getDurationMs, durationMs)
                .set(LlmCallRecord::getCompletedAt, LocalDateTime.now()));
    }

    private void updateFailure(Long id, RuntimeException exception, long durationMs) {
        String message = exception.getMessage();
        if (message == null) {
            message = "LLM 调用失败";
        }
        if (message.length() > 1000) {
            message = message.substring(0, 1000);
        }
        callRecordMapper.update(null, new LambdaUpdateWrapper<LlmCallRecord>()
                .eq(LlmCallRecord::getId, id)
                .set(LlmCallRecord::getStatus, "FAILED")
                .set(LlmCallRecord::getErrorCode, "LLM_CALL_FAILED")
                .set(LlmCallRecord::getErrorMessage, message)
                .set(LlmCallRecord::getDurationMs, durationMs)
                .set(LlmCallRecord::getCompletedAt, LocalDateTime.now()));
    }
}
