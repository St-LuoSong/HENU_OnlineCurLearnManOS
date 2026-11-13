package com.onlinecurlearnmanos.ai.service;

import com.onlinecurlearnmanos.ai.dto.LlmModelResponse;
import com.onlinecurlearnmanos.ai.dto.LlmModelUpdateRequest;
import com.onlinecurlearnmanos.ai.entity.LlmModel;
import com.onlinecurlearnmanos.ai.entity.LlmProviderConfig;
import com.onlinecurlearnmanos.ai.mapper.LlmModelMapper;
import com.onlinecurlearnmanos.ai.mapper.LlmProviderMapper;
import com.onlinecurlearnmanos.common.error.ErrorCode;
import com.onlinecurlearnmanos.common.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LlmModelService {

    private final LlmModelMapper modelMapper;
    private final LlmProviderMapper providerMapper;

    /**
     * 面向用户的可用模型：模型启用、供应商启用、供应商已配置 API Key，
     * 三者同时满足才会出现在模型选择列表里，避免选中后调用必然失败。
     */
    public List<LlmModelResponse> listAvailable() {
        Map<Long, LlmProviderConfig> providers = providerMapper.selectList(null)
                .stream()
                .collect(Collectors.toMap(LlmProviderConfig::getId, Function.identity()));
        return modelMapper.selectList(new LambdaQueryWrapper<LlmModel>()
                        .eq(LlmModel::getEnabled, 1)
                        .orderByAsc(LlmModel::getSortOrder)
                        .orderByAsc(LlmModel::getId))
                .stream()
                .filter(model -> isUsable(model, providers.get(model.getProviderId())))
                .map(model -> toResponse(model, providers.get(model.getProviderId())))
                .toList();
    }

    static boolean isUsable(LlmModel model, LlmProviderConfig provider) {
        return model != null
                && Integer.valueOf(1).equals(model.getEnabled())
                && provider != null
                && Integer.valueOf(1).equals(provider.getEnabled())
                && StringUtils.hasText(provider.getEncryptedApiKey());
    }

    public List<LlmModelResponse> listAll() {
        return modelMapper.selectList(new LambdaQueryWrapper<LlmModel>()
                        .orderByAsc(LlmModel::getSortOrder)
                        .orderByAsc(LlmModel::getId))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public LlmModelResponse update(Long id, LlmModelUpdateRequest request) {
        LlmModel existing = getRequired(id);
        modelMapper.update(null, new LambdaUpdateWrapper<LlmModel>()
                .eq(LlmModel::getId, id)
                .set(LlmModel::getDisplayName, request.displayName().trim())
                .set(
                        LlmModel::getTier,
                        StringUtils.hasText(request.tier())
                                ? normalizeTier(request.tier())
                                : normalizeTier(existing.getTier())
                )
                .set(LlmModel::getPointCost, request.pointCost())
                .set(LlmModel::getEnabled, Boolean.FALSE.equals(request.enabled()) ? 0 : 1));
        return toResponse(getRequired(id));
    }

    private LlmModel getRequired(Long id) {
        LlmModel model = modelMapper.selectById(id);
        if (model == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "模型不存在");
        }
        return model;
    }

    private LlmModelResponse toResponse(LlmModel model) {
        return toResponse(model, providerMapper.selectById(model.getProviderId()));
    }

    private LlmModelResponse toResponse(LlmModel model, LlmProviderConfig provider) {
        return new LlmModelResponse(
                model.getId(),
                model.getProviderId(),
                provider == null ? null : provider.getCode(),
                provider == null ? null : provider.getName(),
                model.getModelCode(),
                model.getDisplayName(),
                normalizeTier(model.getTier()),
                model.getPointCost() == null ? 0 : model.getPointCost(),
                Integer.valueOf(1).equals(model.getEnabled()),
                model.getUpdatedAt()
        );
    }

    static String normalizeTier(String tier) {
        return "EXPERT".equalsIgnoreCase(tier) ? "EXPERT" : "STANDARD";
    }
}
