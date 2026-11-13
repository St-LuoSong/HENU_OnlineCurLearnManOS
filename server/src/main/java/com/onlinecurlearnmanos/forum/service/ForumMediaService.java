package com.onlinecurlearnmanos.forum.service;

import com.onlinecurlearnmanos.common.error.ErrorCode;
import com.onlinecurlearnmanos.common.exception.BusinessException;
import com.onlinecurlearnmanos.file.entity.FileAsset;
import com.onlinecurlearnmanos.file.mapper.FileAssetMapper;
import com.onlinecurlearnmanos.file.service.FileAccessGuard;
import com.onlinecurlearnmanos.file.service.FilePurpose;
import com.onlinecurlearnmanos.forum.dto.ForumMediaResponse;
import com.onlinecurlearnmanos.forum.entity.ForumPostMedia;
import com.onlinecurlearnmanos.forum.mapper.ForumPostMediaMapper;
import com.onlinecurlearnmanos.security.UserPrincipal;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 帖子媒体关系服务。
 *
 * <p>数量上限 9 张、只允许论坛图片用途的图片文件、只能关联自己上传的文件，
 * 三项校验都在服务端完成，前端隐藏按钮不构成权限。</p>
 */
@Service
@RequiredArgsConstructor
public class ForumMediaService {

    public static final int MAX_MEDIA_PER_POST = 9;

    private static final Set<String> IMAGE_EXTENSIONS =
            Set.of("jpg", "jpeg", "png", "webp", "gif");

    private final ForumPostMediaMapper mediaMapper;
    private final FileAssetMapper fileAssetMapper;
    private final FileAccessGuard accessGuard;

    public List<ForumMediaResponse> listPostMedia(Long postId) {
        if (postId == null) {
            return List.of();
        }
        return listMediaForPosts(List.of(postId)).getOrDefault(postId, List.of());
    }

    /** 列表页一次批量取回所有帖子的媒体，避免逐帖查询造成 N+1。 */
    public Map<Long, List<ForumMediaResponse>> listMediaForPosts(Collection<Long> postIds) {
        List<Long> ids = postIds == null
                ? List.of()
                : postIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<ForumPostMedia> rows = mediaMapper.selectList(new LambdaQueryWrapper<ForumPostMedia>()
                .in(ForumPostMedia::getPostId, ids)
                .orderByAsc(ForumPostMedia::getSortOrder)
                .orderByAsc(ForumPostMedia::getId));
        if (rows.isEmpty()) {
            return Map.of();
        }
        Map<Long, FileAsset> assets = fileAssetMapper
                .selectByIds(rows.stream().map(ForumPostMedia::getFileId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(FileAsset::getId, Function.identity(), (left, right) -> left));
        Map<Long, List<ForumMediaResponse>> grouped = new LinkedHashMap<>();
        for (ForumPostMedia row : rows) {
            FileAsset asset = assets.get(row.getFileId());
            if (asset == null || !FileAsset.STATUS_ACTIVE.equals(asset.getStatus())) {
                continue;
            }
            grouped.computeIfAbsent(row.getPostId(), key -> new ArrayList<>())
                    .add(toResponse(row, asset));
        }
        return grouped;
    }

    /**
     * 全量替换帖子的图片列表。
     *
     * <p>先校验再写库，顺序即九宫格顺序；同一次请求出现重复文件直接拒绝，
     * 避免唯一键冲突抛出数据库异常。</p>
     */
    @Transactional
    public void replaceMedia(Long postId, List<Long> fileIds, UserPrincipal principal) {
        List<Long> ordered = normalize(fileIds);
        if (ordered.size() > MAX_MEDIA_PER_POST) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "一条帖子最多上传 " + MAX_MEDIA_PER_POST + " 张图片"
            );
        }
        List<FileAsset> assets = new ArrayList<>();
        for (Long fileId : ordered) {
            assets.add(requireOwnedImage(fileId, principal));
        }
        mediaMapper.delete(new LambdaQueryWrapper<ForumPostMedia>()
                .eq(ForumPostMedia::getPostId, postId));
        int sortOrder = 0;
        for (FileAsset asset : assets) {
            ForumPostMedia media = new ForumPostMedia();
            media.setPostId(postId);
            media.setFileId(asset.getId());
            media.setMediaType(ForumPostMedia.TYPE_IMAGE);
            media.setSortOrder(sortOrder++);
            media.setCreatedAt(LocalDateTime.now());
            mediaMapper.insert(media);
        }
    }

    /** 删除帖子时同步解除媒体关系；文件记录保留，由管理端孤儿清理统一处理。 */
    @Transactional
    public void detachAll(Long postId) {
        mediaMapper.delete(new LambdaQueryWrapper<ForumPostMedia>()
                .eq(ForumPostMedia::getPostId, postId));
    }

    public ForumMediaResponse toResponse(ForumPostMedia media, FileAsset asset) {
        return new ForumMediaResponse(
                media.getId(),
                asset.getId(),
                asset.getOriginalName(),
                asset.getMimeType(),
                asset.getSizeBytes(),
                media.getSortOrder() == null ? 0 : media.getSortOrder(),
                true
        );
    }

    private List<Long> normalize(List<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return List.of();
        }
        List<Long> ordered = new ArrayList<>(fileIds.stream().filter(Objects::nonNull).toList());
        if (ordered.size() != new HashSet<>(ordered).size()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "同一张图片不能重复添加");
        }
        return ordered;
    }

    private FileAsset requireOwnedImage(Long fileId, UserPrincipal principal) {
        FileAsset asset = fileId == null ? null : fileAssetMapper.selectById(fileId);
        if (asset == null || !FileAsset.STATUS_ACTIVE.equals(asset.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "图片不存在或已被删除");
        }
        if (!FilePurpose.FORUM_IMAGE.name().equals(asset.getPurpose())) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "只有论坛图片用途的文件可以关联到帖子"
            );
        }
        String extension = asset.getExtension() == null
                ? ""
                : asset.getExtension().toLowerCase(Locale.ROOT);
        if (!IMAGE_EXTENSIONS.contains(extension)) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "帖子只支持 jpg / jpeg / png / webp / gif 格式的图片"
            );
        }
        if (!accessGuard.isAdmin(principal) && !accessGuard.isOwner(asset, principal)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能关联自己上传的图片");
        }
        return asset;
    }
}

