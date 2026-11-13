package com.onlinecurlearnmanos.admin.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 积分与 AI 调用的一致性异常清单。
 *
 * <ul>
 *     <li>{@code staleReservations}：超过补偿阈值仍未确认的预占积分</li>
 *     <li>{@code staleRunningCalls}：超过补偿阈值仍是 RUNNING 的调用记录</li>
 *     <li>{@code succeededButUnconfirmed}：调用已成功，但积分仍是预占（确认环节被吞掉）</li>
 *     <li>{@code failedButNotRefunded}：调用已失败，但积分仍未退回</li>
 * </ul>
 *
 * 为控制开销，后两类只在最近若干条调用记录范围内核对。
 */
public record PointAnomalyResponse(
        List<ReservationItem> staleReservations,
        List<CallItem> staleRunningCalls,
        List<CallItem> succeededButUnconfirmed,
        List<CallItem> failedButNotRefunded,
        int scannedCallCount
) {

    public record ReservationItem(
            Long transactionId,
            Long userId,
            String type,
            int amount,
            String idempotencyKey,
            String remark,
            LocalDateTime createdAt
    ) {
    }

    public record CallItem(
            Long callRecordId,
            Long userId,
            String providerCode,
            String model,
            String status,
            String errorMessage,
            String idempotencyKey,
            LocalDateTime createdAt
    ) {
    }
}
