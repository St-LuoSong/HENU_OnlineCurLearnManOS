package com.onlinecurlearnmanos.point.service;

import com.onlinecurlearnmanos.common.api.PageResult;
import com.onlinecurlearnmanos.point.dto.PointAccountResponse;
import com.onlinecurlearnmanos.point.dto.PointTransactionResponse;
import com.onlinecurlearnmanos.point.entity.PointTransaction;

public interface PointService {

    void initializeAccount(Long userId);

    PointAccountResponse getAccount(Long userId);

    PageResult<PointTransactionResponse> listTransactions(Long userId, long page, long size);

    PointTransaction reserve(
            Long userId,
            int amount,
            String type,
            String idempotencyKey,
            String referenceType,
            String referenceId,
            String remark
    );

    void confirm(Long transactionId);

    void refund(Long transactionId, String reason);

    void award(
            Long userId,
            int amount,
            String type,
            String idempotencyKey,
            String referenceType,
            String referenceId,
            String remark
    );

    PointAccountResponse adjust(Long userId, int amount, String remark, Long operatorId);
}
