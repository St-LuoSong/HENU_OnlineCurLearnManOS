package com.onlinecurlearnmanos.ai.service;

import com.onlinecurlearnmanos.ai.dto.LlmChatResponse;
import com.onlinecurlearnmanos.ai.dto.LlmProviderResponse;
import com.onlinecurlearnmanos.ai.dto.LlmProviderTestRequest;
import com.onlinecurlearnmanos.ai.dto.LlmProviderTestResponse;
import com.onlinecurlearnmanos.ai.dto.LlmProviderUpdateRequest;
import com.onlinecurlearnmanos.ai.entity.LlmProviderConfig;
import com.onlinecurlearnmanos.ai.entity.LlmCallRecord;
import com.onlinecurlearnmanos.ai.entity.UserAiPreference;
import com.onlinecurlearnmanos.ai.mapper.LlmCallRecordMapper;
import com.onlinecurlearnmanos.ai.mapper.LlmProviderMapper;
import com.onlinecurlearnmanos.ai.mapper.UserAiPreferenceMapper;
import com.onlinecurlearnmanos.common.error.ErrorCode;
import com.onlinecurlearnmanos.common.exception.BusinessException;
import com.onlinecurlearnmanos.common.security.CredentialCipher;
import com.onlinecurlearnmanos.common.security.EncryptedValue;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LlmProviderService {

    private final LlmProviderMapper providerMapper;
    private final CredentialCipher credentialCipher;
    private final LlmService llmService;
    private final LlmCallRecordMapper callRecordMapper;
    private final UserAiPreferenceMapper preferenceMapper;

    public List<LlmProviderResponse> listProviders() {
        List<LlmProviderConfig> providers = providerMapper.selectList(
                new LambdaQueryWrapper<LlmProviderConfig>()
                        .orderByAsc(LlmProviderConfig::getSortOrder)
                        .orderByAsc(LlmProviderConfig::getId)
        );
        if (providers.isEmpty()) {
            return List.of();
        }

        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        List<LlmCallRecord> todayCalls = callRecordMapper.selectList(
                new LambdaQueryWrapper<LlmCallRecord>()
                        .ge(LlmCallRecord::getCreatedAt, startOfDay)
                        .orderByDesc(LlmCallRecord::getCreatedAt)
        );
        Map<String, long[]> counters = new HashMap<>();
        Map<String, String> lastErrorByProvider = new HashMap<>();
        for (LlmCallRecord call : todayCalls) {
            long[] counter = counters.computeIfAbsent(call.getProviderCode(), key -> new long[2]);
            counter[0]++;
            if ("FAILED".equals(call.getStatus())) {
                counter[1]++;
                lastErrorByProvider.putIfAbsent(call.getProviderCode(), call.getErrorMessage());
            }
        }
        Map<Long, Long> affectedByProvider = loadAffectedUsers();

        return providers.stream()
                .map(provider -> toResponse(
                        provider,
                        counters.get(provider.getCode()),
                        lastErrorByProvider.get(provider.getCode()),
                        affectedByProvider.getOrDefault(provider.getId(), 0L)
                ))
                .toList();
    }

    /**
     * 统计有多少用户当前选中的模型属于该供应商：停用前需要知道影响面。
     */
    private Map<Long, Long> loadAffectedUsers() {
        Map<Long, Long> result = new HashMap<>();
        for (UserAiPreference preference : preferenceMapper.selectList(null)) {
            result.merge(preference.getProviderId(), 1L, Long::sum);
        }
        return result;
    }

    @Transactional
    public LlmProviderResponse updateProvider(
            Long id,
            LlmProviderUpdateRequest request,
            Long operatorId
    ) {
        LlmProviderConfig provider = getRequired(id);
        LambdaUpdateWrapper<LlmProviderConfig> wrapper = new LambdaUpdateWrapper<LlmProviderConfig>()
                .eq(LlmProviderConfig::getId, id)
                .set(LlmProviderConfig::getBaseUrl, request.baseUrl().trim())
                .set(LlmProviderConfig::getDefaultModel, request.defaultModel().trim())
                .set(LlmProviderConfig::getEnabled, Boolean.FALSE.equals(request.enabled()) ? 0 : 1)
                .set(LlmProviderConfig::getUpdatedBy, operatorId);

        if (StringUtils.hasText(request.apiKey())) {
            EncryptedValue encrypted = credentialCipher.encrypt(request.apiKey().trim());
            wrapper.set(LlmProviderConfig::getEncryptedApiKey, encrypted.ciphertext());
            wrapper.set(LlmProviderConfig::getEncryptionIv, encrypted.iv());
            wrapper.set(
                    LlmProviderConfig::getKeyVersion,
                    (provider.getKeyVersion() == null ? 1 : provider.getKeyVersion()) + 1
            );
        }
        providerMapper.update(null, wrapper);
        return toResponse(getRequired(id));
    }

    public LlmProviderTestResponse testProvider(Long id, LlmProviderTestRequest request) {
        getRequired(id);
        String message = StringUtils.hasText(request.message())
                ? request.message().trim()
                : "请回复：连接成功";
        LlmChatResponse response = llmService.testProvider(id, message);
        return new LlmProviderTestResponse(
                true,
                response.providerCode(),
                response.model(),
                response.content()
        );
    }

    private LlmProviderConfig getRequired(Long id) {
        LlmProviderConfig provider = providerMapper.selectById(id);
        if (provider == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "LLM 供应商不存在");
        }
        return provider;
    }

    private LlmProviderResponse toResponse(LlmProviderConfig provider) {
        return toResponse(provider, null, null, 0L);
    }

    private LlmProviderResponse toResponse(
            LlmProviderConfig provider,
            long[] counter,
            String lastErrorMessage,
            long affectedUserCount
    ) {
        return new LlmProviderResponse(
                provider.getId(),
                provider.getCode(),
                provider.getAdapterCode(),
                provider.getName(),
                provider.getBaseUrl(),
                provider.getDefaultModel(),
                Integer.valueOf(1).equals(provider.getEnabled()),
                StringUtils.hasText(provider.getEncryptedApiKey()),
                provider.getUpdatedAt(),
                counter == null ? 0 : counter[0],
                counter == null ? 0 : counter[1],
                lastErrorMessage,
                affectedUserCount
        );
    }
}
