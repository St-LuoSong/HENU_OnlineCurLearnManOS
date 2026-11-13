package com.onlinecurlearnmanos.ai.service;

import com.onlinecurlearnmanos.ai.dto.AiAccessResponse;
import com.onlinecurlearnmanos.ai.dto.AiMode;
import com.onlinecurlearnmanos.ai.entity.AiDailyUsage;
import com.onlinecurlearnmanos.ai.mapper.AiDailyUsageMapper;
import com.onlinecurlearnmanos.common.error.ErrorCode;
import com.onlinecurlearnmanos.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class AiAccessService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final AiDailyUsageMapper usageMapper;

    @Value("${app.ai.standard-daily-limit:50}")
    private int standardDailyLimit;

    @Value("${app.ai.expert-daily-limit:3}")
    private int expertDailyLimit;

    public record QuotaGrant(Long userId, AiMode mode, LocalDate usageDate) {
    }

    @Transactional
    public QuotaGrant consume(Long userId, AiMode mode) {
        LocalDate today = LocalDate.now(ZONE);
        usageMapper.ensureRow(userId, today);
        int affected = mode == AiMode.EXPERT
                ? usageMapper.consumeExpert(userId, today, expertDailyLimit)
                : usageMapper.consumeStandard(userId, today, standardDailyLimit);
        if (affected == 0) {
            throw new BusinessException(
                    ErrorCode.TOO_MANY_REQUESTS,
                    mode == AiMode.EXPERT
                            ? "今日专家模式次数已用完，请明天再试"
                            : "今日普通模式次数已用完，请明天再试"
            );
        }
        return new QuotaGrant(userId, mode, today);
    }

    @Transactional
    public void release(QuotaGrant grant) {
        if (grant == null) {
            return;
        }
        if (grant.mode() == AiMode.EXPERT) {
            usageMapper.releaseExpert(grant.userId(), grant.usageDate());
        } else {
            usageMapper.releaseStandard(grant.userId(), grant.usageDate());
        }
    }

    public AiAccessResponse status(Long userId) {
        LocalDate today = LocalDate.now(ZONE);
        AiDailyUsage usage = usageMapper.find(userId, today);
        int standardUsed = usage == null || usage.getStandardUsed() == null ? 0 : usage.getStandardUsed();
        int expertUsed = usage == null || usage.getExpertUsed() == null ? 0 : usage.getExpertUsed();
        return new AiAccessResponse(
                standardUsed,
                standardDailyLimit,
                Math.max(0, standardDailyLimit - standardUsed),
                expertUsed,
                expertDailyLimit,
                Math.max(0, expertDailyLimit - expertUsed)
        );
    }
}
