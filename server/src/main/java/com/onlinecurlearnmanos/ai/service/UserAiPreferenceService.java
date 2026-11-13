package com.onlinecurlearnmanos.ai.service;

import com.onlinecurlearnmanos.ai.dto.LlmModelResponse;
import com.onlinecurlearnmanos.ai.dto.AiMode;
import com.onlinecurlearnmanos.ai.dto.UserAiPreferenceResponse;
import com.onlinecurlearnmanos.ai.dto.UserAiPreferenceUpdateRequest;
import com.onlinecurlearnmanos.ai.entity.LlmModel;
import com.onlinecurlearnmanos.ai.entity.LlmProviderConfig;
import com.onlinecurlearnmanos.ai.entity.UserAiPreference;
import com.onlinecurlearnmanos.ai.mapper.LlmModelMapper;
import com.onlinecurlearnmanos.ai.mapper.LlmProviderMapper;
import com.onlinecurlearnmanos.ai.mapper.UserAiPreferenceMapper;
import com.onlinecurlearnmanos.common.error.ErrorCode;
import com.onlinecurlearnmanos.common.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserAiPreferenceService {

    private final UserAiPreferenceMapper preferenceMapper;
    private final LlmModelMapper modelMapper;
    private final LlmProviderMapper providerMapper;
    private final LlmModelService modelService;

    public UserAiPreferenceResponse get(Long userId) {
        List<LlmModelResponse> available = modelService.listAvailable();
        UserAiPreference preference = find(userId);
        Long selectedModelId = preference == null ? null : preference.getModelId();
        LlmModelResponse selected = available.stream()
                .filter(model -> model.id().equals(selectedModelId))
                .findFirst()
                .orElse(available.isEmpty() ? null : available.get(0));
        return new UserAiPreferenceResponse(
                selected == null ? null : selected.id(),
                selected,
                available
        );
    }

    @Transactional
    public UserAiPreferenceResponse update(Long userId, UserAiPreferenceUpdateRequest request) {
        LlmModel model = modelMapper.selectById(request.modelId());
        if (model == null || !Integer.valueOf(1).equals(model.getEnabled())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "所选模型不可用");
        }
        LlmProviderConfig provider = providerMapper.selectById(model.getProviderId());
        if (provider == null || !Integer.valueOf(1).equals(provider.getEnabled())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "模型供应商不可用");
        }

        UserAiPreference preference = find(userId);
        if (preference == null) {
            preference = new UserAiPreference();
            preference.setUserId(userId);
            preference.setVersion(0);
            preference.setProviderId(provider.getId());
            preference.setModelId(model.getId());
            preferenceMapper.insert(preference);
        } else {
            preference.setProviderId(provider.getId());
            preference.setModelId(model.getId());
            preferenceMapper.updateById(preference);
        }
        return get(userId);
    }

    public ResolvedModelSelection resolve(Long userId) {
        UserAiPreference preference = find(userId);
        LlmModel model = preference == null ? null : modelMapper.selectById(preference.getModelId());
        LlmProviderConfig provider = model == null
                ? null
                : providerMapper.selectById(model.getProviderId());
        if (LlmModelService.isUsable(model, provider)) {
            return new ResolvedModelSelection(
                    provider,
                    model,
                    model.getPointCost() == null ? 1 : model.getPointCost()
            );
        }
        return resolveFallback();
    }

    /**
     * 按普通 / 专家模式解析模型：用户偏好仅在同一分级内生效。
     * 普通模式不扣积分，专家模式按模型价格扣积分。
     */
    public ResolvedModelSelection resolve(Long userId, AiMode mode) {
        AiMode resolvedMode = mode == null ? AiMode.STANDARD : mode;
        List<LlmModelResponse> tierModels = modelService.listAvailable().stream()
                .filter(model -> resolvedMode.name().equals(LlmModelService.normalizeTier(model.tier())))
                .toList();
        if (tierModels.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.NOT_FOUND,
                    resolvedMode == AiMode.EXPERT
                            ? "系统尚未配置可用的专家模型"
                            : "系统尚未配置可用的普通模型"
            );
        }
        UserAiPreference preference = find(userId);
        Long preferredId = preference == null ? null : preference.getModelId();
        LlmModelResponse selected = tierModels.stream()
                .filter(model -> model.id().equals(preferredId))
                .findFirst()
                .orElse(tierModels.get(0));
        LlmModel model = modelMapper.selectById(selected.id());
        LlmProviderConfig provider = model == null ? null : providerMapper.selectById(model.getProviderId());
        if (!LlmModelService.isUsable(model, provider)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "所选模型当前不可用");
        }
        int pointCost = resolvedMode == AiMode.EXPERT
                ? Math.max(1, model.getPointCost() == null ? 1 : model.getPointCost())
                : 0;
        return new ResolvedModelSelection(provider, model, pointCost);
    }

    /**
     * 用户所选模型不可用（被禁用、供应商停用或未配置 Key）时，
     * 自动回退到价格排序最靠前的可用模型，而不是直接让调用失败。
     */
    private ResolvedModelSelection resolveFallback() {
        List<LlmModelResponse> available = modelService.listAvailable();
        if (available.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "系统尚未配置可用模型");
        }
        LlmModel model = modelMapper.selectById(available.get(0).id());
        LlmProviderConfig provider = model == null
                ? null
                : providerMapper.selectById(model.getProviderId());
        if (!LlmModelService.isUsable(model, provider)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "系统尚未配置可用模型");
        }
        return new ResolvedModelSelection(
                provider,
                model,
                model.getPointCost() == null ? 1 : model.getPointCost()
        );
    }

    private UserAiPreference find(Long userId) {
        return preferenceMapper.selectOne(new LambdaQueryWrapper<UserAiPreference>()
                .eq(UserAiPreference::getUserId, userId)
                .last("LIMIT 1"));
    }
}
