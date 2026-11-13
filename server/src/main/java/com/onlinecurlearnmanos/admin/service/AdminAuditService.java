package com.onlinecurlearnmanos.admin.service;

import com.onlinecurlearnmanos.admin.dto.AdminAuditLogResponse;
import com.onlinecurlearnmanos.admin.entity.AdminAuditLog;
import com.onlinecurlearnmanos.admin.mapper.AdminAuditLogMapper;
import com.onlinecurlearnmanos.common.api.PageResult;
import com.onlinecurlearnmanos.user.entity.SysUser;
import com.onlinecurlearnmanos.user.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理操作的审计流水。管理动作必须可追溯到具体操作人。
 */
@Service
@RequiredArgsConstructor
public class AdminAuditService {

    private static final int MAX_DETAIL_LENGTH = 1000;

    private final AdminAuditLogMapper auditLogMapper;
    private final SysUserMapper userMapper;

    public PageResult<AdminAuditLogResponse> list(long page, long size) {
        long safePage = Math.max(page, 1);
        long safeSize = Math.min(Math.max(size, 1), 50);
        Page<AdminAuditLog> result = auditLogMapper.selectPage(
                new Page<>(safePage, safeSize),
                new LambdaQueryWrapper<AdminAuditLog>()
                        .orderByDesc(AdminAuditLog::getCreatedAt)
                        .orderByDesc(AdminAuditLog::getId)
        );
        List<Long> operatorIds = result.getRecords().stream()
                .map(AdminAuditLog::getOperatorId)
                .distinct()
                .toList();
        Map<Long, String> operatorNames = operatorIds.isEmpty()
                ? Map.of()
                : userMapper.selectByIdsIncludingDeleted(operatorIds).stream()
                        .collect(Collectors.toMap(SysUser::getId, SysUser::getDisplayName));
        return new PageResult<>(
                result.getRecords().stream()
                        .map(log -> new AdminAuditLogResponse(
                                log.getId(),
                                log.getOperatorId(),
                                operatorNames.getOrDefault(log.getOperatorId(), "未知管理员"),
                                log.getAction(),
                                log.getTargetType(),
                                log.getTargetId(),
                                log.getDetail(),
                                log.getCreatedAt()
                        ))
                        .toList(),
                result.getTotal(),
                safePage,
                safeSize
        );
    }

    public void record(
            Long operatorId,
            String action,
            String targetType,
            String targetId,
            String detail
    ) {
        AdminAuditLog log = new AdminAuditLog();
        log.setOperatorId(operatorId);
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setDetail(truncate(detail));
        log.setCreatedAt(LocalDateTime.now());
        auditLogMapper.insert(log);
    }

    private String truncate(String detail) {
        if (detail == null || detail.length() <= MAX_DETAIL_LENGTH) {
            return detail;
        }
        return detail.substring(0, MAX_DETAIL_LENGTH);
    }
}
