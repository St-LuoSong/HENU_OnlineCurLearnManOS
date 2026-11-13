package com.onlinecurlearnmanos.system.recovery;

import com.onlinecurlearnmanos.ai.entity.LlmCallRecord;
import com.onlinecurlearnmanos.ai.mapper.LlmCallRecordMapper;
import com.onlinecurlearnmanos.point.entity.PointTransaction;
import com.onlinecurlearnmanos.point.mapper.PointTransactionMapper;
import com.onlinecurlearnmanos.point.service.PointService;
import com.onlinecurlearnmanos.support.MybatisLambdaCache;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 补偿任务契约：服务中断留下的 RUNNING 调用与 RESERVED 预占必须被收敛，
 * 单个退款失败不能中断其余预占的回收。
 */
@ExtendWith(MockitoExtension.class)
class PendingRecordRecoveryJobTest {

    @Mock
    private LlmCallRecordMapper callRecordMapper;

    @Mock
    private PointTransactionMapper transactionMapper;

    @Mock
    private PointService pointService;

    @InjectMocks
    private PendingRecordRecoveryJob recoveryJob;

    @BeforeAll
    static void registerLambdaCache() {
        MybatisLambdaCache.register(LlmCallRecord.class, PointTransaction.class);
    }

    @Test
    void skipsRefundsWhenNoStaleReservationExists() {
        given(callRecordMapper.update(any(), any())).willReturn(1);
        given(transactionMapper.selectList(any())).willReturn(List.of());

        recoveryJob.recover();

        verify(callRecordMapper).update(any(), any());
        verify(pointService, never()).refund(anyLong(), anyString());
    }

    @Test
    void refundsEveryStaleReservation() {
        given(callRecordMapper.update(any(), any())).willReturn(0);
        given(transactionMapper.selectList(any())).willReturn(List.of(
                reservedTransaction(101L),
                reservedTransaction(102L)
        ));

        recoveryJob.recover();

        verify(pointService).refund(101L, "预占积分超时未确认，已自动退回");
        verify(pointService).refund(102L, "预占积分超时未确认，已自动退回");
    }

    @Test
    void keepsRecoveringWhenOneRefundFails() {
        given(callRecordMapper.update(any(), any())).willReturn(0);
        given(transactionMapper.selectList(any())).willReturn(List.of(
                reservedTransaction(101L),
                reservedTransaction(102L)
        ));
        willThrow(new IllegalStateException("数据库瞬时不可用"))
                .given(pointService)
                .refund(101L, "预占积分超时未确认，已自动退回");

        assertThatCode(() -> recoveryJob.recover()).doesNotThrowAnyException();

        verify(pointService).refund(102L, "预占积分超时未确认，已自动退回");
    }

    private PointTransaction reservedTransaction(Long id) {
        PointTransaction transaction = new PointTransaction();
        transaction.setId(id);
        transaction.setAccountId(7L);
        transaction.setUserId(31L);
        transaction.setStatus("RESERVED");
        transaction.setAmount(20);
        return transaction;
    }
}
