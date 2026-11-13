package com.onlinecurlearnmanos.system.recovery;

import com.onlinecurlearnmanos.ai.entity.LlmCallRecord;
import com.onlinecurlearnmanos.ai.mapper.LlmCallRecordMapper;
import com.onlinecurlearnmanos.point.entity.PointTransaction;
import com.onlinecurlearnmanos.point.mapper.PointTransactionMapper;
import com.onlinecurlearnmanos.point.service.PointService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 补偿任务：服务在"预占积分之后、确认或退款之前"中断时，会留下 RUNNING 调用记录和
 * RESERVED 积分事务。这里按超时阈值把它们收敛为失败与退款，避免用户余额被永久占用。
 */
@Component
@RequiredArgsConstructor
public class PendingRecordRecoveryJob {

    private static final Logger log = LoggerFactory.getLogger(PendingRecordRecoveryJob.class);

    private static final int BATCH_LIMIT = 200;

    private final LlmCallRecordMapper callRecordMapper;
    private final PointTransactionMapper transactionMapper;
    private final PointService pointService;

    @Value("${app.recovery.stale-running-minutes:15}")
    private long staleRunningMinutes;

    @Value("${app.recovery.stale-reserved-minutes:15}")
    private long staleReservedMinutes;

    @Scheduled(
            fixedDelayString = "${app.recovery.interval-ms:300000}",
            initialDelayString = "${app.recovery.initial-delay-ms:60000}"
    )
    public void recover() {
        int failedCalls = markStaleRunningCallsFailed();
        int refunded = refundStaleReservations();
        if (failedCalls > 0 || refunded > 0) {
            log.warn(
                    "Recovered stale AI records: failedCalls={}, refundedReservations={}",
                    failedCalls,
                    refunded
            );
        }
    }

    private int markStaleRunningCallsFailed() {
        LocalDateTime threshold = LocalDateTime.now()
                .minusMinutes(Math.max(staleRunningMinutes, 1));
        return callRecordMapper.update(null, new LambdaUpdateWrapper<LlmCallRecord>()
                .eq(LlmCallRecord::getStatus, "RUNNING")
                .lt(LlmCallRecord::getCreatedAt, threshold)
                .set(LlmCallRecord::getStatus, "FAILED")
                .set(LlmCallRecord::getErrorCode, "STALE_RUNNING")
                .set(LlmCallRecord::getErrorMessage, "调用超时未完成，已由补偿任务标记失败")
                .set(LlmCallRecord::getCompletedAt, LocalDateTime.now()));
    }

    private int refundStaleReservations() {
        LocalDateTime threshold = LocalDateTime.now()
                .minusMinutes(Math.max(staleReservedMinutes, 1));
        List<PointTransaction> staleTransactions = transactionMapper.selectList(
                new LambdaQueryWrapper<PointTransaction>()
                        .eq(PointTransaction::getStatus, "RESERVED")
                        .lt(PointTransaction::getCreatedAt, threshold)
                        .orderByAsc(PointTransaction::getId)
                        .last("LIMIT " + BATCH_LIMIT)
        );
        int refunded = 0;
        for (PointTransaction transaction : staleTransactions) {
            try {
                pointService.refund(transaction.getId(), "预占积分超时未确认，已自动退回");
                refunded++;
            } catch (RuntimeException exception) {
                log.error(
                        "Failed to refund stale point transaction {}",
                        transaction.getId(),
                        exception
                );
            }
        }
        return refunded;
    }
}
