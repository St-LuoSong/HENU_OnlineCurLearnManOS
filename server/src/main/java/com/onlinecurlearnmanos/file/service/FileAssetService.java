package com.onlinecurlearnmanos.file.service;

import com.onlinecurlearnmanos.admin.service.AdminAuditService;
import com.onlinecurlearnmanos.common.api.PageResult;
import com.onlinecurlearnmanos.common.error.ErrorCode;
import com.onlinecurlearnmanos.common.exception.BusinessException;
import com.onlinecurlearnmanos.file.dto.FileAssetResponse;
import com.onlinecurlearnmanos.file.entity.CloudResourceAttachment;
import com.onlinecurlearnmanos.file.entity.CloudSubmissionAttachment;
import com.onlinecurlearnmanos.file.entity.FileAsset;
import com.onlinecurlearnmanos.file.entity.ResourceAttachment;
import com.onlinecurlearnmanos.file.mapper.CloudResourceAttachmentMapper;
import com.onlinecurlearnmanos.file.mapper.CloudSubmissionAttachmentMapper;
import com.onlinecurlearnmanos.file.mapper.FileAssetMapper;
import com.onlinecurlearnmanos.file.mapper.ResourceAttachmentMapper;
import com.onlinecurlearnmanos.file.storage.FileStorage;
import com.onlinecurlearnmanos.file.storage.StoredKeys;
import com.onlinecurlearnmanos.forum.entity.ForumPostMedia;
import com.onlinecurlearnmanos.forum.mapper.ForumPostMediaMapper;
import com.onlinecurlearnmanos.security.UserPrincipal;
import com.onlinecurlearnmanos.user.entity.SysUser;
import com.onlinecurlearnmanos.user.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 文件资产的统一入口：上传校验、下载、删除、管理员治理。
 *
 * <p>写盘与写库的先后顺序为「先落盘、再写库，写库失败立即回收物理文件」，
 * 避免出现“有数据库记录但没有内容”的坏状态。</p>
 */
@Service
@RequiredArgsConstructor
public class FileAssetService {

    private static final Set<String> PREVIEWABLE_EXTENSIONS =
            Set.of("pdf", "txt", "md", "xls", "xlsx", "docx");
    private static final int ORPHAN_PURGE_BATCH = 200;

    private final FileAssetMapper fileAssetMapper;
    private final ResourceAttachmentMapper resourceAttachmentMapper;
    private final CloudResourceAttachmentMapper cloudResourceAttachmentMapper;
    private final CloudSubmissionAttachmentMapper cloudSubmissionAttachmentMapper;
    private final ForumPostMediaMapper forumPostMediaMapper;
    private final SysUserMapper userMapper;
    private final FileStorage fileStorage;
    private final FileValidator fileValidator;
    private final FileAccessGuard accessGuard;
    private final AdminAuditService auditService;

    @Transactional
    public FileAssetResponse upload(
            UserPrincipal principal,
            MultipartFile file,
            FilePurpose purpose
    ) {
        purpose.requireRole(principal.getRoles());
        // 图片用途走独立的扩展名 / 体积 / 像素校验，不复用文档白名单。
        ValidatedFile validated = purpose.isImageOnly()
                ? fileValidator.validateImage(file)
                : fileValidator.validate(file);
        FileAsset asset = new FileAsset();
        asset.setOwnerId(principal.getId());
        asset.setOriginalName(validated.originalName());
        asset.setStoredKey(StoredKeys.generate(validated.extension()));
        asset.setExtension(validated.extension());
        asset.setMimeType(validated.mimeType());
        asset.setSizeBytes(validated.sizeBytes());
        asset.setSha256(validated.sha256());
        asset.setPurpose(purpose.name());
        asset.setStorageProvider(fileStorage.provider());
        asset.setStatus(FileAsset.STATUS_ACTIVE);
        asset.setDownloadCount(0);
        asset.setDeleted(0);
        asset.setVersion(0);
        fileStorage.store(asset.getStoredKey(), validated.content());
        try {
            fileAssetMapper.insert(asset);
        } catch (RuntimeException exception) {
            fileStorage.delete(asset.getStoredKey());
            throw exception;
        }
        return toResponse(asset, principal, displayName(principal.getId()));
    }

    public FileAsset requireActive(Long fileId) {
        FileAsset asset = fileId == null ? null : fileAssetMapper.selectById(fileId);
        if (asset == null || !FileAsset.STATUS_ACTIVE.equals(asset.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文件不存在或已被删除");
        }
        return asset;
    }

    public FileAssetResponse metadata(Long fileId, UserPrincipal principal) {
        FileAsset asset = requireActive(fileId);
        accessGuard.requireReadable(asset, principal);
        return toResponse(asset, principal, displayName(asset.getOwnerId()));
    }

    /** 校验读取权限后返回文件记录，供预览等只读场景复用。 */
    public FileAsset requireReadable(Long fileId, UserPrincipal principal) {
        FileAsset asset = requireActive(fileId);
        accessGuard.requireReadable(asset, principal);
        return asset;
    }

    public InputStream openStream(Long fileId, UserPrincipal principal) {
        return openStream(requireReadable(fileId, principal));
    }

    /** 已经完成权限判定的场景可直接按记录打开内容流。 */
    public InputStream openStream(FileAsset asset) {
        return fileStorage.openStream(asset.getStoredKey());
    }

    @Transactional
    public void markDownloaded(Long fileId) {
        fileAssetMapper.update(null, new LambdaUpdateWrapper<FileAsset>()
                .eq(FileAsset::getId, fileId)
                .setSql("download_count = download_count + 1")
                .set(FileAsset::getLastDownloadedAt, LocalDateTime.now()));
    }

    @Transactional
    public void delete(Long fileId, UserPrincipal principal) {
        FileAsset asset = requireActive(fileId);
        accessGuard.requireOwnerOrAdmin(asset, principal);
        long attached = countAttachments(fileId);
        boolean adminOverride = accessGuard.isAdmin(principal)
                && !accessGuard.isOwner(asset, principal);
        if (attached > 0 && !adminOverride) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "该文件已被教学资源或作业引用，请先解除关联后再删除"
            );
        }
        if (attached > 0) {
            detachAll(fileId);
        }
        fileAssetMapper.deleteById(fileId);
        fileStorage.delete(asset.getStoredKey());
        if (adminOverride) {
            auditService.record(
                    principal.getId(),
                    "FILE_DELETE",
                    "FILE_ASSET",
                    String.valueOf(fileId),
                    "管理员清理文件：" + asset.getOriginalName()
            );
        }
    }

    public PageResult<FileAssetResponse> adminList(
            long page,
            long size,
            Boolean orphanOnly,
            Long ownerId,
            String purpose,
            UserPrincipal principal
    ) {
        long safePage = Math.max(page, 1);
        long safeSize = Math.min(Math.max(size, 1), 50);
        LambdaQueryWrapper<FileAsset> wrapper = new LambdaQueryWrapper<>();
        if (ownerId != null) {
            wrapper.eq(FileAsset::getOwnerId, ownerId);
        }
        if (StringUtils.hasText(purpose)) {
            wrapper.eq(FileAsset::getPurpose, purpose.trim().toUpperCase(Locale.ROOT));
        }
        if (Boolean.TRUE.equals(orphanOnly)) {
            Set<Long> attached = attachedFileIds();
            if (!attached.isEmpty()) {
                wrapper.notIn(FileAsset::getId, attached);
            }
        }
        wrapper.orderByDesc(FileAsset::getCreatedAt).orderByDesc(FileAsset::getId);
        Page<FileAsset> result = fileAssetMapper.selectPage(new Page<>(safePage, safeSize), wrapper);
        Map<Long, String> names = ownerNames(result.getRecords());
        return new PageResult<>(
                result.getRecords().stream()
                        .map(asset -> toResponse(
                                asset,
                                principal,
                                names.getOrDefault(asset.getOwnerId(), "未知用户")
                        ))
                        .toList(),
                result.getTotal(),
                safePage,
                safeSize
        );
    }

    /**
     * 清理没有任何业务引用的孤儿文件（上传后未提交、资源删除后遗留等）。
     * 单次最多处理 {@value #ORPHAN_PURGE_BATCH} 个，避免长时间占用事务。
     */
    @Transactional
    public int purgeOrphans(Long adminId) {
        Set<Long> attached = attachedFileIds();
        LambdaQueryWrapper<FileAsset> wrapper = new LambdaQueryWrapper<FileAsset>()
                .orderByAsc(FileAsset::getId)
                .last("LIMIT " + ORPHAN_PURGE_BATCH);
        if (!attached.isEmpty()) {
            wrapper.notIn(FileAsset::getId, attached);
        }
        List<FileAsset> orphans = fileAssetMapper.selectList(wrapper);
        for (FileAsset asset : orphans) {
            fileAssetMapper.deleteById(asset.getId());
            fileStorage.delete(asset.getStoredKey());
        }
        if (!orphans.isEmpty()) {
            auditService.record(
                    adminId,
                    "FILE_ORPHAN_PURGE",
                    "FILE_ASSET",
                    "-",
                    "清理孤儿文件 " + orphans.size() + " 个"
            );
        }
        return orphans.size();
    }

    private long countAttachments(Long fileId) {
        return countOf(resourceAttachmentMapper.selectCount(
                new LambdaQueryWrapper<ResourceAttachment>()
                        .eq(ResourceAttachment::getFileId, fileId)))
                + countOf(cloudResourceAttachmentMapper.selectCount(
                        new LambdaQueryWrapper<CloudResourceAttachment>()
                                .eq(CloudResourceAttachment::getFileId, fileId)))
                + countOf(cloudSubmissionAttachmentMapper.selectCount(
                        new LambdaQueryWrapper<CloudSubmissionAttachment>()
                                .eq(CloudSubmissionAttachment::getFileId, fileId)))
                + countOf(forumPostMediaMapper.selectCount(
                        new LambdaQueryWrapper<ForumPostMedia>()
                                .eq(ForumPostMedia::getFileId, fileId)));
    }

    private long countOf(Long value) {
        return value == null ? 0L : value;
    }

    private void detachAll(Long fileId) {
        resourceAttachmentMapper.delete(new LambdaQueryWrapper<ResourceAttachment>()
                .eq(ResourceAttachment::getFileId, fileId));
        cloudResourceAttachmentMapper.delete(new LambdaQueryWrapper<CloudResourceAttachment>()
                .eq(CloudResourceAttachment::getFileId, fileId));
        cloudSubmissionAttachmentMapper.delete(new LambdaQueryWrapper<CloudSubmissionAttachment>()
                .eq(CloudSubmissionAttachment::getFileId, fileId));
        forumPostMediaMapper.delete(new LambdaQueryWrapper<ForumPostMedia>()
                .eq(ForumPostMedia::getFileId, fileId));
    }

    private Set<Long> attachedFileIds() {
        Set<Long> ids = new LinkedHashSet<>();
        resourceAttachmentMapper.selectList(
                        new LambdaQueryWrapper<ResourceAttachment>().select(ResourceAttachment::getFileId))
                .forEach(attachment -> ids.add(attachment.getFileId()));
        cloudResourceAttachmentMapper.selectList(
                        new LambdaQueryWrapper<CloudResourceAttachment>()
                                .select(CloudResourceAttachment::getFileId))
                .forEach(attachment -> ids.add(attachment.getFileId()));
        cloudSubmissionAttachmentMapper.selectList(
                        new LambdaQueryWrapper<CloudSubmissionAttachment>()
                                .select(CloudSubmissionAttachment::getFileId))
                .forEach(attachment -> ids.add(attachment.getFileId()));
        forumPostMediaMapper.selectList(
                        new LambdaQueryWrapper<ForumPostMedia>()
                                .select(ForumPostMedia::getFileId))
                .forEach(media -> ids.add(media.getFileId()));
        return ids;
    }

    private Map<Long, String> ownerNames(List<FileAsset> assets) {
        List<Long> ownerIds = assets.stream()
                .map(FileAsset::getOwnerId)
                .distinct()
                .toList();
        if (ownerIds.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectByIdsIncludingDeleted(ownerIds).stream()
                .collect(Collectors.toMap(SysUser::getId, SysUser::getDisplayName));
    }

    private String displayName(Long userId) {
        SysUser user = userMapper.selectById(userId);
        return user == null || !StringUtils.hasText(user.getDisplayName())
                ? "未知用户"
                : user.getDisplayName();
    }

    public FileAssetResponse toResponse(FileAsset asset, UserPrincipal principal, String ownerName) {
        boolean canDelete = accessGuard.isAdmin(principal) || accessGuard.isOwner(asset, principal);
        return new FileAssetResponse(
                asset.getId(),
                asset.getOriginalName(),
                asset.getExtension(),
                asset.getMimeType(),
                asset.getSizeBytes(),
                asset.getSha256(),
                asset.getPurpose(),
                asset.getStatus(),
                asset.getOwnerId(),
                ownerName,
                asset.getDownloadCount(),
                asset.getLastDownloadedAt(),
                asset.getCreatedAt(),
                PREVIEWABLE_EXTENSIONS.contains(asset.getExtension()),
                canDelete
        );
    }
}
