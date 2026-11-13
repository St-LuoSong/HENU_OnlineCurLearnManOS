package com.onlinecurlearnmanos.admin.service;

import com.onlinecurlearnmanos.admin.dto.PointAnomalyResponse;
import com.onlinecurlearnmanos.ai.entity.LlmCallRecord;
import com.onlinecurlearnmanos.ai.mapper.LlmCallRecordMapper;
import com.onlinecurlearnmanos.point.entity.PointTransaction;
import com.onlinecurlearnmanos.point.mapper.PointTransactionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminPointAnomalyService {

    private static final int SCAN_LIMIT = 300;
    private static final int LIST_LIMIT = 50;

    private final PointTransactionMapper transactionMapper;
    private final LlmCallRecordMapper callRecordMapper;

    @Value("${app.recovery.stale-reserved-minutes:15}")
    private long staleReservedMinutes;

    @Value("${app.recovery.stale-running-minutes:15}")
    private long staleRunningMinutes;

    public PointAnomalyResponse anomalies() {
        LocalDateTime now = LocalDateTime.now();

        List<PointAnomalyResponse.ReservationItem> staleReservations = transactionMapper
                .selectList(new LambdaQueryWrapper<PointTransaction>()
                        .eq(PointTransaction::getStatus, "RESERVED")
                        .lt(
                                PointTransaction::getCreatedAt,
                                now.minusMinutes(Math.max(staleReservedMinutes, 1))
                        )
                        .orderByAsc(PointTransaction::getCreatedAt)
                        .last("LIMIT " + LIST_LIMIT))
                .stream()
                .map(this::toReservationItem)
                .toList();

        List<LlmCallRecord> staleRunning = callRecordMapper.selectList(
                new LambdaQueryWrapper<LlmCallRecord>()
                        .eq(LlmCallRecord::getStatus, "RUNNING")
                        .lt(
                                LlmCallRecord::getCreatedAt,
                                now.minusMinutes(Math.max(staleRunningMinutes, 1))
                        )
                        .orderByAsc(LlmCallRecord::getCreatedAt)
                        .last("LIMIT " + LIST_LIMIT)
        );

        List<LlmCallRecord> recentCalls = callRecordMapper.selectList(
                new LambdaQueryWrapper<LlmCallRecord>()
                        .in(LlmCallRecord::getStatus, List.of("SUCCESS", "FAILED"))
                        .orderByDesc(LlmCallRecord::getCreatedAt)
                        .last("LIMIT " + SCAN_LIMIT)
        );
        Map<String, PointTransaction> transactionsByKey = loadTransactions(recentCalls);

        List<PointAnomalyResponse.CallItem> succeededButUnconfirmed = new ArrayList<>();
        List<PointAnomalyResponse.CallItem> failedButNotRefunded = new ArrayList<>();
        for (LlmCallRecord call : recentCalls) {
            PointTransaction transaction = transactionsByKey.get(call.getIdempotencyKey());
            if (transaction == null || !"RESERVED".equals(transaction.getStatus())) {
                continue;
            }
            if ("SUCCESS".equals(call.getStatus())) {
                succeededButUnconfirmed.add(toCallItem(call));
            } else {
                failedButNotRefunded.add(toCallItem(call));
            }
        }

        return new PointAnomalyResponse(
                staleReservations,
                staleRunning.stream().map(this::toCallItem).toList(),
                succeededButUnconfirmed,
                failedButNotRefunded,
                recentCalls.size()
        );
    }

    private Map<String, PointTransaction> loadTransactions(List<LlmCallRecord> calls) {
        List<String> keys = calls.stream()
                .map(LlmCallRecord::getIdempotencyKey)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (keys.isEmpty()) {
            return Map.of();
        }
        Map<String, PointTransaction> result = new HashMap<>();
        for (PointTransaction transaction : transactionMapper.selectList(
                new LambdaQueryWrapper<PointTransaction>()
                        .in(PointTransaction::getIdempotencyKey, keys))) {
            result.put(transaction.getIdempotencyKey(), transaction);
        }
        return result;
    }

    private PointAnomalyResponse.ReservationItem toReservationItem(PointTransaction transaction) {
        return new PointAnomalyResponse.ReservationItem(
                transaction.getId(),
                transaction.getUserId(),
                transaction.getType(),
                transaction.getAmount() == null ? 0 : transaction.getAmount(),
                transaction.getIdempotencyKey(),
                transaction.getRemark(),
                transaction.getCreatedAt()
        );
    }

    private PointAnomalyResponse.CallItem toCallItem(LlmCallRecord call) {
        return new PointAnomalyResponse.CallItem(
                call.getId(),
                call.getUserId(),
                call.getProviderCode(),
                call.getModel(),
                call.getStatus(),
                call.getErrorMessage(),
                call.getIdempotencyKey(),
                call.getCreatedAt()
        );
    }
}
