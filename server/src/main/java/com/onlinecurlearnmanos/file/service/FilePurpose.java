package com.onlinecurlearnmanos.file.service;

import com.onlinecurlearnmanos.common.error.ErrorCode;
import com.onlinecurlearnmanos.common.exception.BusinessException;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 文件用途决定“谁能上传”。读取权限由 {@code FileAccessGuard} 按附件归属计算，
 * 与上传用途无关，避免上传后就自动对全平台可见。
 */
public enum FilePurpose {

    PLATFORM_RESOURCE(Set.of("TEACHER", "ADMIN")),
    CLASS_RESOURCE(Set.of("TEACHER", "ADMIN")),
    SUBMISSION(Set.of("STUDENT")),
    /** 论坛帖子图片：学生与教师都可以发图文帖，白名单与体积上限独立于文档。 */
    FORUM_IMAGE(Set.of("STUDENT", "TEACHER", "ADMIN")),
    /** 班级教学图片：只有班级教师（或管理员代为治理）可以上传。 */
    CLASS_RESOURCE_IMAGE(Set.of("TEACHER", "ADMIN")),
    /** 作业图片：只有学生可以上传，且仍受截止时间与重交策略约束。 */
    SUBMISSION_IMAGE(Set.of("STUDENT"));

    private final Set<String> allowedRoles;

    FilePurpose(Set<String> allowedRoles) {
        this.allowedRoles = allowedRoles;
    }

    public static FilePurpose parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return PLATFORM_RESOURCE;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        for (FilePurpose purpose : values()) {
            if (purpose.name().equals(normalized)) {
                return purpose;
            }
        }
        throw new BusinessException(
                ErrorCode.BAD_REQUEST,
                "文件用途只能是 PLATFORM_RESOURCE、CLASS_RESOURCE、SUBMISSION、FORUM_IMAGE、"
                        + "CLASS_RESOURCE_IMAGE 或 SUBMISSION_IMAGE"
        );
    }

    /** 图片用途走独立的扩展名 / 体积 / 像素校验，不复用文档白名单。 */
    public boolean isImageOnly() {
        return this == FORUM_IMAGE
                || this == CLASS_RESOURCE_IMAGE
                || this == SUBMISSION_IMAGE;
    }

    public void requireRole(List<String> roles) {
        boolean allowed = roles != null
                && roles.stream().anyMatch(role -> allowedRoles.contains(role));
        if (!allowed) {
            throw new BusinessException(
                    ErrorCode.FORBIDDEN,
                    rejectMessage()
            );
        }
    }

    private String rejectMessage() {
        return switch (this) {
            case SUBMISSION, SUBMISSION_IMAGE -> "只有学生可以上传作业附件";
            case FORUM_IMAGE -> "只有学生或教师可以上传论坛图片";
            case CLASS_RESOURCE_IMAGE -> "只有教师或管理员可以上传班级教学图片";
            default -> "只有教师或管理员可以上传该类文件";
        };
    }
}
