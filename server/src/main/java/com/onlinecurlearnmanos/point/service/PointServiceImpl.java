package com.onlinecurlearnmanos.point.service;

import com.onlinecurlearnmanos.common.api.PageResult;
import com.onlinecurlearnmanos.common.error.ErrorCode;
import com.onlinecurlearnmanos.common.exception.BusinessException;
import com.onlinecurlearnmanos.point.dto.PointAccountResponse;
import com.onlinecurlearnmanos.point.dto.PointTransactionResponse;
import com.onlinecurlearnmanos.point.entity.PointAccount;
import com.onlinecurlearnmanos.point.entity.PointTransaction;
import com.onlinecurlearnmanos.point.mapper.PointAccountMapper;
import com.onlinecurlearnmanos.point.mapper.PointTransactionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PointServiceImpl implements PointService {

    private final PointAccountMapper accountMapper;
    private final PointTransactionMapper transactionMapper;

    @Value("${point.initial-balance:1000}")
    private int initialBalance;

    @Override
    @Transactional
    public void initializeAccount(Long userId) {
        getOrCreateAccount(userId);
    }

    @Override
    @Transactional
    public PointAccountResponse getAccount(Long userId) {
        return toAccountResponse(getOrCreateAccount(userId));
    }

    @Override
    public PageResult<PointTransactionResponse> listTransactions(Long userId, long page, long size) {
        long safePage = Math.max(page, 1);
        long safeSize = Math.min(Math.max(size, 1), 50);
        Page<PointTransaction> result = transactionMapper.selectPage(
                new Page<>(safePage, safeSize),
                new LambdaQueryWrapper<PointTransaction>()
                        .eq(PointTransaction::getUserId, userId)
                        .orderByDesc(PointTransaction::getCreatedAt)
        );
        return new PageResult<>(
                result.getRecords().stream().map(this::toTransactionResponse).toList(),
                result.getTotal(),
                safePage,
                safeSize
        );
    }

    @Override
    @Transactional
    public PointTransaction reserve(
            Long userId,
            int amount,
            String type,
            String idempotencyKey,
            String referenceType,
            String referenceId,
            String remark
    ) {
        if (amount <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "积分扣减数量必须大于 0");
        }
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "幂等键不能为空");
        }

        PointTransaction existing = findByKey(idempotencyKey);
        if (existing != null) {
            if ("REFUNDED".equals(existing.getStatus())) {
                throw new BusinessException(ErrorCode.CONFLICT, "该请求已退款，请使用新的幂等键");
            }
            return existing;
        }

        PointAccount account = getOrCreateAccount(userId);
        int affected = accountMapper.update(null, new LambdaUpdateWrapper<PointAccount>()
                .eq(PointAccount::getId, account.getId())
                .ge(PointAccount::getBalance, amount)
                .setSql("balance = balance - " + amount));
        if (affected != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "积分不足或并发扣减冲突");
        }

        PointAccount updatedAccount = accountMapper.selectById(account.getId());
        PointTransaction transaction = new PointTransaction();
        transaction.setAccountId(account.getId());
        transaction.setUserId(userId);
        transaction.setType(type);
        transaction.setAmount(amount);
        transaction.setBalanceAfter(updatedAccount.getBalance());
        transaction.setStatus("RESERVED");
        transaction.setIdempotencyKey(idempotencyKey);
        transaction.setReferenceType(normalizeNullable(referenceType));
        transaction.setReferenceId(normalizeNullable(referenceId));
        transaction.setRemark(normalizeNullable(remark));
        transactionMapper.insert(transaction);
        return transaction;
    }

    @Override
    @Transactional
    public void confirm(Long transactionId) {
        PointTransaction transaction = getRequiredTransaction(transactionId);
        if ("CONFIRMED".equals(transaction.getStatus())) {
            return;
        }
        if (!"RESERVED".equals(transaction.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "积分事务状态不允许确认");
        }

        int updated = transactionMapper.update(null, new LambdaUpdateWrapper<PointTransaction>()
                .eq(PointTransaction::getId, transactionId)
                .eq(PointTransaction::getStatus, "RESERVED")
                .set(PointTransaction::getStatus, "CONFIRMED")
                .set(PointTransaction::getCompletedAt, LocalDateTime.now()));
        if (updated == 0) {
            PointTransaction current = getRequiredTransaction(transactionId);
            if ("CONFIRMED".equals(current.getStatus())) {
                return;
            }
            throw new BusinessException(ErrorCode.CONFLICT, "积分事务已被其他请求处理");
        }
        accountMapper.update(null, new LambdaUpdateWrapper<PointAccount>()
                .eq(PointAccount::getId, transaction.getAccountId())
                .setSql("total_spent = total_spent + " + transaction.getAmount()));
    }

    @Override
    @Transactional
    public void refund(Long transactionId, String reason) {
        PointTransaction transaction = getRequiredTransaction(transactionId);
        if ("REFUNDED".equals(transaction.getStatus())) {
            return;
        }
        if (!"RESERVED".equals(transaction.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "只有预占中的积分事务可以退款");
        }

        int updated = transactionMapper.update(null, new LambdaUpdateWrapper<PointTransaction>()
                .eq(PointTransaction::getId, transactionId)
                .eq(PointTransaction::getStatus, "RESERVED")
                .set(PointTransaction::getStatus, "REFUNDED")
                .set(
                        PointTransaction::getRemark,
                        StringUtils.hasText(reason) ? reason.trim() : transaction.getRemark()
                )
                .set(PointTransaction::getCompletedAt, LocalDateTime.now()));
        if (updated == 0) {
            PointTransaction current = getRequiredTransaction(transactionId);
            if ("REFUNDED".equals(current.getStatus())) {
                return;
            }
            throw new BusinessException(ErrorCode.CONFLICT, "积分事务已被其他请求处理");
        }
        accountMapper.update(null, new LambdaUpdateWrapper<PointAccount>()
                .eq(PointAccount::getId, transaction.getAccountId())
                .setSql("balance = balance + " + transaction.getAmount()));
    }

    @Override
    @Transactional
    public void award(
            Long userId,
            int amount,
            String type,
            String idempotencyKey,
            String referenceType,
            String referenceId,
            String remark
    ) {
        if (amount <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "奖励积分必须大于 0");
        }
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "幂等键不能为空");
        }
        if (findByKey(idempotencyKey) != null) {
            return;
        }

        PointAccount account = getOrCreateAccount(userId);
        accountMapper.update(null, new LambdaUpdateWrapper<PointAccount>()
                .eq(PointAccount::getId, account.getId())
                .setSql("balance = balance + " + amount)
                .setSql("total_earned = total_earned + " + amount));

        PointAccount updated = accountMapper.selectById(account.getId());
        PointTransaction transaction = new PointTransaction();
        transaction.setAccountId(account.getId());
        transaction.setUserId(userId);
        transaction.setType(type);
        transaction.setAmount(amount);
        transaction.setBalanceAfter(updated.getBalance());
        transaction.setStatus("CONFIRMED");
        transaction.setIdempotencyKey(idempotencyKey);
        transaction.setReferenceType(normalizeNullable(referenceType));
        transaction.setReferenceId(normalizeNullable(referenceId));
        transaction.setRemark(normalizeNullable(remark));
        transaction.setCompletedAt(LocalDateTime.now());
        transactionMapper.insert(transaction);
    }

    @Override
    @Transactional
    public PointAccountResponse adjust(Long userId, int amount, String remark, Long operatorId) {
        if (amount == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "积分变动值不能为 0");
        }
        PointAccount account = getOrCreateAccount(userId);
        if (amount > 0) {
            accountMapper.update(null, new LambdaUpdateWrapper<PointAccount>()
                    .eq(PointAccount::getId, account.getId())
                    .setSql("balance = balance + " + amount)
                    .setSql("total_earned = total_earned + " + amount));
        } else {
            int deduction = Math.abs(amount);
            int affected = accountMapper.update(null, new LambdaUpdateWrapper<PointAccount>()
                    .eq(PointAccount::getId, account.getId())
                    .ge(PointAccount::getBalance, deduction)
                    .setSql("balance = balance - " + deduction)
                    .setSql("total_spent = total_spent + " + deduction));
            if (affected != 1) {
                throw new BusinessException(ErrorCode.CONFLICT, "积分不足或并发扣减冲突");
            }
        }

        PointAccount updated = accountMapper.selectById(account.getId());
        PointTransaction transaction = new PointTransaction();
        transaction.setAccountId(account.getId());
        transaction.setUserId(userId);
        transaction.setType("ADMIN_ADJUST");
        transaction.setAmount(amount);
        transaction.setBalanceAfter(updated.getBalance());
        transaction.setStatus("CONFIRMED");
        transaction.setIdempotencyKey("admin-adjust:" + UUID.randomUUID());
        transaction.setReferenceType("ADMIN");
        transaction.setReferenceId(operatorId == null ? null : String.valueOf(operatorId));
        transaction.setRemark(normalizeNullable(remark));
        transaction.setCompletedAt(LocalDateTime.now());
        transactionMapper.insert(transaction);
        return toAccountResponse(updated);
    }

    private PointAccount getOrCreateAccount(Long userId) {
        PointAccount account = findByUserId(userId);
        if (account != null) {
            return account;
        }
        if (initialBalance < 0) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "初始积分配置不能为负数");
        }
        try {
            PointAccount created = new PointAccount();
            created.setUserId(userId);
            created.setBalance(initialBalance);
            created.setTotalEarned(initialBalance);
            created.setTotalSpent(0);
            created.setDeleted(0);
            created.setVersion(0);
            accountMapper.insert(created);
            recordInitialGrant(created);
            return created;
        } catch (DataIntegrityViolationException exception) {
            PointAccount existing = findByUserId(userId);
            if (existing == null) {
                throw exception;
            }
            return existing;
        }
    }

    /**
     * 初始积分同样要写入流水，否则"累计获得"与账本明细无法对齐，
     * 余额也就无法通过流水重建。
     */
    private void recordInitialGrant(PointAccount account) {
        int granted = intValue(account.getBalance());
        if (granted <= 0) {
            return;
        }
        PointTransaction transaction = new PointTransaction();
        transaction.setAccountId(account.getId());
        transaction.setUserId(account.getUserId());
        transaction.setType("INITIAL_GRANT");
        transaction.setAmount(granted);
        transaction.setBalanceAfter(granted);
        transaction.setStatus("CONFIRMED");
        transaction.setIdempotencyKey("initial-grant:" + account.getUserId());
        transaction.setReferenceType("SYSTEM");
        transaction.setRemark("新用户注册初始积分");
        transaction.setCompletedAt(LocalDateTime.now());
        transactionMapper.insert(transaction);
    }

    private PointAccount findByUserId(Long userId) {
        return accountMapper.selectOne(new LambdaQueryWrapper<PointAccount>()
                .eq(PointAccount::getUserId, userId)
                .last("LIMIT 1"));
    }

    private PointTransaction findByKey(String idempotencyKey) {
        return transactionMapper.selectOne(new LambdaQueryWrapper<PointTransaction>()
                .eq(PointTransaction::getIdempotencyKey, idempotencyKey)
                .last("LIMIT 1"));
    }

    private PointTransaction getRequiredTransaction(Long id) {
        PointTransaction transaction = transactionMapper.selectById(id);
        if (transaction == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "积分事务不存在");
        }
        return transaction;
    }

    private PointAccountResponse toAccountResponse(PointAccount account) {
        return new PointAccountResponse(
                account.getId(),
                account.getUserId(),
                intValue(account.getBalance()),
                intValue(account.getTotalEarned()),
                intValue(account.getTotalSpent()),
                account.getUpdatedAt()
        );
    }

    private PointTransactionResponse toTransactionResponse(PointTransaction transaction) {
        return new PointTransactionResponse(
                transaction.getId(),
                transaction.getType(),
                intValue(transaction.getAmount()),
                directionOf(transaction),
                intValue(transaction.getBalanceAfter()),
                transaction.getStatus(),
                transaction.getReferenceType(),
                transaction.getReferenceId(),
                transaction.getRemark(),
                transaction.getCreatedAt(),
                transaction.getCompletedAt()
        );
    }

    /**
     * AI 调用与预占类流水在库中保存为正数金额，但业务语义是支出；
     * 管理调整则直接按存储符号判断方向。
     */
    private String directionOf(PointTransaction transaction) {
        int amount = intValue(transaction.getAmount());
        if ("ADMIN_ADJUST".equals(transaction.getType())) {
            return amount < 0 ? "DEBIT" : "CREDIT";
        }
        if ("INITIAL_GRANT".equals(transaction.getType())
                || "CLASS_TASK_REWARD".equals(transaction.getType())
                || "DAILY_CHECKIN".equals(transaction.getType())) {
            return "CREDIT";
        }
        return "DEBIT";
    }

    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private int intValue(Integer value) {
        return value == null ? 0 : value;
    }
}
