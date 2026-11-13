package com.onlinecurlearnmanos.file.controller;

import com.onlinecurlearnmanos.common.api.ApiResponse;
import com.onlinecurlearnmanos.common.api.PageResult;
import com.onlinecurlearnmanos.file.dto.FileAssetResponse;
import com.onlinecurlearnmanos.file.service.FileAssetService;
import com.onlinecurlearnmanos.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员文件治理：查看全平台文件、清理孤儿文件、强制下架违规文件。
 * 强制删除会先解除业务关联，并写入审计日志。
 */
@RestController
@RequestMapping("/api/v1/admin/files")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminFileController {

    private final FileAssetService fileAssetService;

    @GetMapping
    public ApiResponse<PageResult<FileAssetResponse>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) Boolean orphan,
            @RequestParam(required = false) Long ownerId,
            @RequestParam(required = false) String purpose,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(
                fileAssetService.adminList(page, size, orphan, ownerId, purpose, principal)
        );
    }

    @PostMapping("/orphans/purge")
    public ApiResponse<Integer> purgeOrphans(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(fileAssetService.purgeOrphans(principal.getId()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        fileAssetService.delete(id, principal);
        return ApiResponse.success(null);
    }
}
