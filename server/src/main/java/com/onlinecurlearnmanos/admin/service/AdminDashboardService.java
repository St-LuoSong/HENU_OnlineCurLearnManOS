package com.onlinecurlearnmanos.admin.service;

import com.onlinecurlearnmanos.admin.dto.AdminDashboardSummaryResponse;
import com.onlinecurlearnmanos.ai.entity.LlmCallRecord;
import com.onlinecurlearnmanos.ai.entity.LlmModel;
import com.onlinecurlearnmanos.ai.entity.LlmProviderConfig;
import com.onlinecurlearnmanos.ai.mapper.LlmCallRecordMapper;
import com.onlinecurlearnmanos.ai.mapper.LlmModelMapper;
import com.onlinecurlearnmanos.ai.mapper.LlmProviderMapper;
import com.onlinecurlearnmanos.cloudclass.entity.CloudClass;
import com.onlinecurlearnmanos.cloudclass.mapper.CloudClassMapper;
import com.onlinecurlearnmanos.point.entity.PointTransaction;
import com.onlinecurlearnmanos.point.mapper.PointTransactionMapper;
import com.onlinecurlearnmanos.user.mapper.SysRoleMapper;
import com.onlinecurlearnmanos.user.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 管理后台概览统计。全部指标由数据库聚合得出，不再由前端抽样计算。
 */
@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final CloudClassMapper cloudClassMapper;
    private final LlmCallRecordMapper callRecordMapper;
    private final LlmProviderMapper providerMapper;
    private final LlmModelMapper modelMapper;
    private final PointTransactionMapper transactionMapper;

    /** 与补偿任务保持同一阈值：只有超过该时长仍未确认的预占才算异常。 */
    @Value("${app.recovery.stale-reserved-minutes:15}")
    private long staleReservedMinutes;

    public AdminDashboardSummaryResponse summary() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime staleBefore = LocalDateTime.now()
                .minusMinutes(Math.max(staleReservedMinutes, 1));

        long totalUsers = userMapper.selectCount(null);
        long studentUsers = roleMapper.countUsersByRoleCode("STUDENT");
        long teacherUsers = roleMapper.countUsersByRoleCode("TEACHER");
        long pendingTeachers = roleMapper.countPendingTeachers();

        long activeCloudClasses = cloudClassMapper.selectCount(
                new LambdaQueryWrapper<CloudClass>()
                        .eq(CloudClass::getStatus, "ACTIVE")
        );
        long llmCallsToday = callRecordMapper.selectCount(
                new LambdaQueryWrapper<LlmCallRecord>()
                        .ge(LlmCallRecord::getCreatedAt, startOfDay)
        );
        long llmFailuresToday = callRecordMapper.selectCount(
                new LambdaQueryWrapper<LlmCallRecord>()
                        .ge(LlmCallRecord::getCreatedAt, startOfDay)
                        .eq(LlmCallRecord::getStatus, "FAILED")
        );
        long reservedTransactions = transactionMapper.selectCount(
                new LambdaQueryWrapper<PointTransaction>()
                        .eq(PointTransaction::getStatus, "RESERVED")
                        .lt(PointTransaction::getCreatedAt, staleBefore)
        );
        long configuredProviders = providerMapper.selectCount(
                new LambdaQueryWrapper<LlmProviderConfig>()
                        .eq(LlmProviderConfig::getEnabled, 1)
                        .isNotNull(LlmProviderConfig::getEncryptedApiKey)
        );
        long enabledModels = modelMapper.selectCount(
                new LambdaQueryWrapper<LlmModel>()
                        .eq(LlmModel::getEnabled, 1)
        );

        return new AdminDashboardSummaryResponse(
                totalUsers,
                studentUsers,
                teacherUsers,
                pendingTeachers,
                activeCloudClasses,
                llmCallsToday,
                llmFailuresToday,
                reservedTransactions,
                configuredProviders,
                enabledModels
        );
    }
}
