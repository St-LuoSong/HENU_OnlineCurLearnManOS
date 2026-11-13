package com.onlinecurlearnmanos.point.service;

import com.onlinecurlearnmanos.common.error.ErrorCode;
import com.onlinecurlearnmanos.common.exception.BusinessException;
import com.onlinecurlearnmanos.point.entity.PointAccount;
import com.onlinecurlearnmanos.point.entity.PointTransaction;
import com.onlinecurlearnmanos.point.mapper.PointAccountMapper;
import com.onlinecurlearnmanos.point.mapper.PointTransactionMapper;
import com.onlinecurlearnmanos.support.MybatisLambdaCache;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 积分状态机的原子性与幂等性：确认 / 退款都必须以「带 status 条件、影响 1 行」为前提，
 * 并发重复调用不能重复修改余额或累计支出。
 */
@ExtendWith(MockitoExtension.class)
class PointServiceAtomicityTest {

    private static final Long TRANSACTION_ID = 99L;

    @Mock
    private PointAccountMapper accountMapper;

    @Mock
    private PointTransactionMapper transactionMapper;

    @InjectMocks
    private PointServiceImpl pointService;

    @BeforeAll
    static void registerLambdaCache() {
        MybatisLambdaCache.register(PointTransaction.class, PointAccount.class);
    }

    @Test
    void confirmIsIdempotentForAlreadyConfirmedTransaction() {
        given(transactionMapper.selectById(TRANSACTION_ID)).willReturn(transaction("CONFIRMED"));

        pointService.confirm(TRANSACTION_ID);

        verify(transactionMapper, never()).update(any(), any());
        verify(accountMapper, never()).update(any(), any());
    }

    @Test
    void confirmRejectsTransactionThatWasAlreadyRefunded() {
        given(transactionMapper.selectById(TRANSACTION_ID)).willReturn(transaction("REFUNDED"));

        assertThatThrownBy(() -> pointService.confirm(TRANSACTION_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.CONFLICT));
        verify(accountMapper, never()).update(any(), any());
    }

    @Test
    void confirmDoesNotTouchAccountWhenConditionalUpdateLosesRace() {
        given(transactionMapper.selectById(TRANSACTION_ID))
                .willReturn(transaction("RESERVED"), transaction("CONFIRMED"));
        given(transactionMapper.update(any(), any())).willReturn(0);

        pointService.confirm(TRANSACTION_ID);

        verify(accountMapper, never()).update(any(), any());
    }

    @Test
    void confirmSpendsPointsOnlyWhenConditionalUpdateSucceeds() {
        given(transactionMapper.selectById(TRANSACTION_ID)).willReturn(transaction("RESERVED"));
        given(transactionMapper.update(any(), any())).willReturn(1);

        pointService.confirm(TRANSACTION_ID);

        verify(accountMapper, times(1)).update(any(), any());
    }

    @Test
    void refundReturnsPointsExactlyOnce() {
        given(transactionMapper.selectById(TRANSACTION_ID))
                .willReturn(transaction("RESERVED"), transaction("REFUNDED"));
        given(transactionMapper.update(any(), any())).willReturn(1);

        pointService.refund(TRANSACTION_ID, "首次退款");
        pointService.refund(TRANSACTION_ID, "重复退款");

        verify(accountMapper, times(1)).update(any(), any());
        verify(transactionMapper, times(1)).update(any(), any());
    }

    @Test
    void reserveFailsWithoutWritingTransactionWhenBalanceUpdateAffectsNoRow() {
        given(transactionMapper.selectOne(any())).willReturn(null);
        given(accountMapper.selectOne(any())).willReturn(account());
        given(accountMapper.update(any(), any())).willReturn(0);

        assertThatThrownBy(() -> pointService.reserve(
                31L,
                50,
                "CHAT",
                "chat:key-1",
                "CHAT",
                "req-1",
                "问答扣费"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("积分不足");
        verify(transactionMapper, never()).insert(any(PointTransaction.class));
    }

    @Test
    void reserveReusesExistingTransactionForSameIdempotencyKey() {
        PointTransaction existing = transaction("RESERVED");
        given(transactionMapper.selectOne(any())).willReturn(existing);

        PointTransaction result = pointService.reserve(
                31L,
                50,
                "CHAT",
                "chat:key-1",
                "CHAT",
                "req-1",
                "问答扣费"
        );

        assertThat(result).isSameAs(existing);
        verify(accountMapper, never()).update(any(), any());
    }

    @Test
    void reserveRejectsIdempotencyKeyThatWasAlreadyRefunded() {
        given(transactionMapper.selectOne(any())).willReturn(transaction("REFUNDED"));

        assertThatThrownBy(() -> pointService.reserve(
                31L,
                50,
                "CHAT",
                "chat:key-1",
                "CHAT",
                "req-1",
                "问答扣费"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已退款");
    }

    private PointAccount account() {
        PointAccount account = new PointAccount();
        account.setId(7L);
        account.setUserId(31L);
        account.setBalance(100);
        account.setTotalEarned(100);
        account.setTotalSpent(0);
        return account;
    }

    private PointTransaction transaction(String status) {
        PointTransaction transaction = new PointTransaction();
        transaction.setId(TRANSACTION_ID);
        transaction.setAccountId(7L);
        transaction.setUserId(31L);
        transaction.setType("CHAT");
        transaction.setAmount(50);
        transaction.setBalanceAfter(50);
        transaction.setStatus(status);
        transaction.setIdempotencyKey("chat:key-1");
        return transaction;
    }
}
