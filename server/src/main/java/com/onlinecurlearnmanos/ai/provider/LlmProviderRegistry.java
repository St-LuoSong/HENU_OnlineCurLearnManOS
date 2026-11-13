package com.onlinecurlearnmanos.ai.provider;

import com.onlinecurlearnmanos.common.error.ErrorCode;
import com.onlinecurlearnmanos.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class LlmProviderRegistry {

    private final Map<String, LlmProvider> providers;

    public LlmProviderRegistry(java.util.List<LlmProvider> providerList) {
        this.providers = providerList.stream()
                .collect(Collectors.toUnmodifiableMap(LlmProvider::adapterCode, Function.identity()));
    }

    public LlmProvider getRequiredAdapter(String adapterCode) {
        LlmProvider provider = providers.get(adapterCode);
        if (provider == null) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "LLM Provider Adapter 未配置: " + adapterCode
            );
        }
        return provider;
    }
}
