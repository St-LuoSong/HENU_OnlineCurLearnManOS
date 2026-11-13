package com.onlinecurlearnmanos.file.service;

import com.onlinecurlearnmanos.admin.service.AdminAuditService;
import com.onlinecurlearnmanos.cloudclass.dto.CloudClassSubmissionAccess;
import com.onlinecurlearnmanos.cloudclass.entity.CloudClass;
import com.onlinecurlearnmanos.cloudclass.entity.CloudClassResource;
import com.onlinecurlearnmanos.cloudclass.mapper.CloudClassMapper;
import com.onlinecurlearnmanos.cloudclass.mapper.CloudClassResourceMapper;
import com.onlinecurlearnmanos.cloudclass.service.CloudClassService;
import com.onlinecurlearnmanos.common.error.ErrorCode;
import com.onlinecurlearnmanos.common.exception.BusinessException;
import com.onlinecurlearnmanos.content.entity.LearningResource;
import com.onlinecurlearnmanos.content.mapper.LearningResourceMapper;
import com.onlinecurlearnmanos.file.dto.AttachmentCreateRequest;
import com.onlinecurlearnmanos.file.dto.AttachmentResponse;
import com.onlinecurlearnmanos.file.entity.CloudResourceAttachment;
import com.onlinecurlearnmanos.file.entity.CloudSubmissionAttachment;
import com.onlinecurlearnmanos.file.entity.FileAsset;
import com.onlinecurlearnmanos.file.entity.ResourceAttachment;
import com.onlinecurlearnmanos.file.mapper.CloudResourceAttachmentMapper;
import com.onlinecurlearnmanos.file.mapper.CloudSubmissionAttachmentMapper;
import com.onlinecurlearnmanos.file.mapper.FileAssetMapper;
import com.onlinecurlearnmanos.file.mapper.ResourceAttachmentMapper;
import com.onlinecurlearnmanos.security.UserPrincipal;
import com.onlinecurlearnmanos.user.entity.SysUser;
import com.onlinecurlearnmanos.user.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 文件与业务对象的关联管理。
 *
 * <p>平台教学资源：管理员可挂载任意文件，教师只能挂载自己发布的资源与自己的文件；
 * 云班级资源：仅班级教师（或管理员）可挂载；作业附件：学生只能挂载到自己尚未关闭的提交上，
 * 并完整继承班级状态、任务开放状态、截止时间与重交策略。</p>
 */
@Service
@RequiredArgsConstructor
public class FileAttachmentService {

    private final ResourceAttachmentMapper resourceAttachmentMapper;
    private final CloudResourceAttachmentMapper cloudResourceAttachmentMapper;
    private final CloudSubmissionAttachmentMapper cloudSubmissionAttachmentMapper;
    private final FileAssetMapper fileAssetMapper;
    private final LearningResourceMapper learningResourceMapper;
    private final CloudClassResourceMapper cloudClassResourceMapper;
    private final CloudClassMapper cloudClassMapper;
    private final SysUserMapper userMapper;
    private final CloudClassService cloudClassService;
    private final FileAccessGuard accessGuard;
    private final AdminAuditService auditService;

    // ---------------------------------------------------------------- 平台教学资源

    public List<AttachmentResponse> listResourceAttachments(Long resourceId, UserPrincipal principal) {
        requireResource(resourceId);
        List<ResourceAttachment> attachments = resourceAttachmentMapper.selectList(
                new LambdaQueryWrapper<ResourceAttachment>()
                        .eq(ResourceAttachment::getResourceId, resourceId)
                        .orderByAsc(ResourceAttachment::getSortOrder)
                        .orderByAsc(ResourceAttachment::getId));
        return toResponses(
                attachments.stream().map(ResourceAttachment::getFileId).toList(),
                attachments.stream().map(attachment -> new AttachmentView(
                        attachment.getId(),
                        attachment.getFileId(),
                        attachment.getSortOrder(),
                        null,
                        attachment.getCreatedAt()
                )).toList(),
                principal,
                null
        );
    }

    @Transactional
    public AttachmentResponse attachToResource(
            Long resourceId,
            AttachmentCreateRequest request,
            UserPrincipal principal
    ) {
        LearningResource resource = requireResource(resourceId);
        boolean admin = accessGuard.isAdmin(principal);
        boolean publisher = Objects.equals(resource.getPublisherId(), principal.getId());
        if (!admin && !(publisher && principal.getRoles().contains("TEACHER"))) {
            throw new BusinessException(
                    ErrorCode.FORBIDDEN,
                    "只有管理员或该资源的发布教师可以上传教学资源文件"
            );
        }
        FileAsset asset = requireOwnedUpload(request.fileId(), principal);
        if (resourceAttachmentMapper.selectCount(new LambdaQueryWrapper<ResourceAttachment>()
                .eq(ResourceAttachment::getResourceId, resourceId)
                .eq(ResourceAttachment::getFileId, asset.getId())) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "该文件已关联到此资源");
        }
        ResourceAttachment attachment = new ResourceAttachment();
        attachment.setResourceId(resourceId);
        attachment.setFileId(asset.getId());
        attachment.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        attachment.setCreatedAt(LocalDateTime.now());
        resourceAttachmentMapper.insert(attachment);
        auditService.record(
                principal.getId(),
                "RESOURCE_ATTACHMENT_ADD",
                "LEARNING_RESOURCE",
                String.valueOf(resourceId),
                "关联资源附件：" + asset.getOriginalName()
        );
        return toResponses(
                List.of(asset.getId()),
                List.of(new AttachmentView(
                        attachment.getId(),
                        asset.getId(),
                        attachment.getSortOrder(),
                        null,
                        attachment.getCreatedAt()
                )),
                principal,
                resource.getPublisherId()
        ).get(0);
    }

    @Transactional
    public void detachResourceAttachment(
            Long resourceId,
            Long attachmentId,
            UserPrincipal principal
    ) {
        LearningResource resource = requireResource(resourceId);
        ResourceAttachment attachment = requireAttachment(
                resourceAttachmentMapper.selectById(attachmentId),
                resourceId,
                ResourceAttachment::getResourceId
        );
        FileAsset asset = requireAsset(attachment.getFileId());
        if (!accessGuard.isAdmin(principal)
                && !accessGuard.isOwner(asset, principal)
                && !Objects.equals(resource.getPublisherId(), principal.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权解除该文件关联");
        }
        resourceAttachmentMapper.deleteById(attachmentId);
        auditService.record(
                principal.getId(),
                "RESOURCE_ATTACHMENT_REMOVE",
                "LEARNING_RESOURCE",
                String.valueOf(resourceId),
                "解除资源附件：" + asset.getOriginalName()
        );
        long remaining = activeResourceAttachmentCount(resourceId);
        if (remaining == 0 && "PUBLISHED".equals(resource.getStatus())) {
            learningResourceMapper.update(null, new LambdaUpdateWrapper<LearningResource>()
                    .eq(LearningResource::getId, resourceId)
                    .set(LearningResource::getStatus, "DRAFT"));
            auditService.record(
                    principal.getId(),
                    "RESOURCE_UNPUBLISH",
                    "LEARNING_RESOURCE",
                    String.valueOf(resourceId),
                    "最后一个有效附件被移除，资源自动下架：" + resource.getTitle()
            );
        }
    }

    // ---------------------------------------------------------------- 云班级教学资源

    public List<AttachmentResponse> listClassResourceAttachments(
            Long classResourceId,
            UserPrincipal principal
    ) {
        CloudClassResource resource = requireClassResource(classResourceId);
        accessGuard.requireClassVisible(resource.getClassId(), principal);
        List<CloudResourceAttachment> attachments = cloudResourceAttachmentMapper.selectList(
                new LambdaQueryWrapper<CloudResourceAttachment>()
                        .eq(CloudResourceAttachment::getClassResourceId, classResourceId)
                        .orderByAsc(CloudResourceAttachment::getSortOrder)
                        .orderByAsc(CloudResourceAttachment::getId));
        return toResponses(
                attachments.stream().map(CloudResourceAttachment::getFileId).toList(),
                attachments.stream().map(attachment -> new AttachmentView(
                        attachment.getId(),
                        attachment.getFileId(),
                        attachment.getSortOrder(),
                        null,
                        attachment.getCreatedAt()
                )).toList(),
                principal,
                teacherOf(resource.getClassId())
        );
    }

    @Transactional
    public AttachmentResponse attachToClassResource(
            Long classResourceId,
            AttachmentCreateRequest request,
            UserPrincipal principal
    ) {
        CloudClassResource resource = requireClassResource(classResourceId);
        requireClassTeacher(resource.getClassId(), principal);
        FileAsset asset = requireOwnedUpload(request.fileId(), principal);
        if (cloudResourceAttachmentMapper.selectCount(
                new LambdaQueryWrapper<CloudResourceAttachment>()
                        .eq(CloudResourceAttachment::getClassResourceId, classResourceId)
                        .eq(CloudResourceAttachment::getFileId, asset.getId())) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "该文件已关联到此班级资源");
        }
        CloudResourceAttachment attachment = new CloudResourceAttachment();
        attachment.setClassResourceId(classResourceId);
        attachment.setFileId(asset.getId());
        attachment.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        attachment.setCreatedAt(LocalDateTime.now());
        cloudResourceAttachmentMapper.insert(attachment);
        return toResponses(
                List.of(asset.getId()),
                List.of(new AttachmentView(
                        attachment.getId(),
                        asset.getId(),
                        attachment.getSortOrder(),
                        null,
                        attachment.getCreatedAt()
                )),
                principal,
                teacherOf(resource.getClassId())
        ).get(0);
    }

    @Transactional
    public void detachClassResourceAttachment(
            Long classResourceId,
            Long attachmentId,
            UserPrincipal principal
    ) {
        CloudClassResource resource = requireClassResource(classResourceId);
        CloudResourceAttachment attachment = requireAttachment(
                cloudResourceAttachmentMapper.selectById(attachmentId),
                classResourceId,
                CloudResourceAttachment::getClassResourceId
        );
        FileAsset asset = requireAsset(attachment.getFileId());
        if (!accessGuard.isAdmin(principal)
                && !accessGuard.isOwner(asset, principal)
                && !Objects.equals(teacherOf(resource.getClassId()), principal.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权解除该文件关联");
        }
        cloudResourceAttachmentMapper.deleteById(attachmentId);
    }

    // ---------------------------------------------------------------- 学生作业附件

    public List<AttachmentResponse> listSubmissionAttachments(
            Long submissionId,
            UserPrincipal principal
    ) {
        CloudClassSubmissionAccess access = cloudClassService.submissionAttachmentContext(
                principal.getId(),
                principal.getRoles(),
                submissionId
        );
        List<CloudSubmissionAttachment> attachments = cloudSubmissionAttachmentMapper.selectList(
                new LambdaQueryWrapper<CloudSubmissionAttachment>()
                        .eq(CloudSubmissionAttachment::getSubmissionId, submissionId)
                        .orderByAsc(CloudSubmissionAttachment::getVersionNo)
                        .orderByAsc(CloudSubmissionAttachment::getId));
        return toResponses(
                attachments.stream().map(CloudSubmissionAttachment::getFileId).toList(),
                attachments.stream().map(attachment -> new AttachmentView(
                        attachment.getId(),
                        attachment.getFileId(),
                        null,
                        attachment.getVersionNo(),
                        attachment.getCreatedAt()
                )).toList(),
                principal,
                access.teacherId()
        );
    }

    @Transactional
    public AttachmentResponse attachToSubmission(
            Long submissionId,
            AttachmentCreateRequest request,
            UserPrincipal principal
    ) {
        CloudClassSubmissionAccess access = cloudClassService.assertSubmissionAttachmentAllowed(
                principal.getId(),
                submissionId
        );
        FileAsset asset = requireOwnedUpload(request.fileId(), principal);
        if (cloudSubmissionAttachmentMapper.selectCount(
                new LambdaQueryWrapper<CloudSubmissionAttachment>()
                        .eq(CloudSubmissionAttachment::getSubmissionId, submissionId)
                        .eq(CloudSubmissionAttachment::getFileId, asset.getId())) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "该文件已作为本次作业的附件提交");
        }
        CloudSubmissionAttachment attachment = new CloudSubmissionAttachment();
        attachment.setSubmissionId(submissionId);
        attachment.setFileId(asset.getId());
        attachment.setVersionNo(access.versionNo() == null ? 1 : access.versionNo());
        attachment.setCreatedAt(LocalDateTime.now());
        cloudSubmissionAttachmentMapper.insert(attachment);
        return toResponses(
                List.of(asset.getId()),
                List.of(new AttachmentView(
                        attachment.getId(),
                        asset.getId(),
                        null,
                        attachment.getVersionNo(),
                        attachment.getCreatedAt()
                )),
                principal,
                access.teacherId()
        ).get(0);
    }

    @Transactional
    public void detachSubmissionAttachment(
            Long submissionId,
            Long attachmentId,
            UserPrincipal principal
    ) {
        CloudClassSubmissionAccess access = cloudClassService.submissionAttachmentContext(
                principal.getId(),
                principal.getRoles(),
                submissionId
        );
        CloudSubmissionAttachment attachment = requireAttachment(
                cloudSubmissionAttachmentMapper.selectById(attachmentId),
                submissionId,
                CloudSubmissionAttachment::getSubmissionId
        );
        FileAsset asset = requireAsset(attachment.getFileId());
        boolean studentOwner = Objects.equals(access.studentId(), principal.getId())
                && accessGuard.isOwner(asset, principal);
        boolean classTeacher = Objects.equals(access.teacherId(), principal.getId());
        if (!accessGuard.isAdmin(principal) && !studentOwner && !classTeacher) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权删除该作业附件");
        }
        cloudSubmissionAttachmentMapper.deleteById(attachmentId);
    }

    // ---------------------------------------------------------------- 内部工具

    private LearningResource requireResource(Long resourceId) {
        LearningResource resource = resourceId == null ? null : learningResourceMapper.selectById(resourceId);
        if (resource == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "教学资源不存在");
        }
        return resource;
    }

    private CloudClassResource requireClassResource(Long classResourceId) {
        CloudClassResource resource = classResourceId == null
                ? null
                : cloudClassResourceMapper.selectById(classResourceId);
        if (resource == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "云班级资源不存在");
        }
        return resource;
    }

    private Long teacherOf(Long classId) {
        CloudClass cloudClass = classId == null ? null : cloudClassMapper.selectById(classId);
        return cloudClass == null ? null : cloudClass.getTeacherId();
    }

    private void requireClassTeacher(Long classId, UserPrincipal principal) {
        if (accessGuard.isAdmin(principal)) {
            return;
        }
        if (!Objects.equals(teacherOf(classId), principal.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有班级教师可以上传班级教学资源文件");
        }
    }

    /**
     * 关联文件必须是本次上传者自己的文件（管理员代为治理时例外），
     * 防止把别人的私有文件挂到自己的资源上造成越权读取。
     */
    private FileAsset requireOwnedUpload(Long fileId, UserPrincipal principal) {
        FileAsset asset = requireAsset(fileId);
        if (!accessGuard.isAdmin(principal) && !accessGuard.isOwner(asset, principal)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能关联自己上传的文件");
        }
        return asset;
    }

    private FileAsset requireAsset(Long fileId) {
        FileAsset asset = fileId == null ? null : fileAssetMapper.selectById(fileId);
        if (asset == null || !FileAsset.STATUS_ACTIVE.equals(asset.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文件不存在或已被删除");
        }
        return asset;
    }

    private <T> T requireAttachment(T attachment, Long parentId, java.util.function.Function<T, Long> parentGetter) {
        if (attachment == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "附件不存在");
        }
        if (!Objects.equals(parentGetter.apply(attachment), parentId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "附件不存在");
        }
        return attachment;
    }

    private List<AttachmentResponse> toResponses(
            List<Long> fileIds,
            List<AttachmentView> views,
            UserPrincipal principal,
            Long classTeacherId
    ) {
        if (views.isEmpty()) {
            return List.of();
        }
        Map<Long, FileAsset> assets = loadAssets(fileIds);
        Map<Long, String> names = ownerNames(assets.values());
        return views.stream()
                .map(view -> {
                    FileAsset asset = assets.get(view.fileId());
                    if (asset == null) {
                        return null;
                    }
                    boolean canDelete = accessGuard.isAdmin(principal)
                            || accessGuard.isOwner(asset, principal)
                            || Objects.equals(classTeacherId, principal.getId());
                    return new AttachmentResponse(
                            view.id(),
                            asset.getId(),
                            asset.getOriginalName(),
                            asset.getExtension(),
                            asset.getMimeType(),
                            asset.getSizeBytes(),
                            view.sortOrder(),
                            view.versionNo(),
                            asset.getOwnerId(),
                            names.getOrDefault(asset.getOwnerId(), "未知用户"),
                            view.createdAt(),
                            canDelete
                    );
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private Map<Long, FileAsset> loadAssets(Collection<Long> fileIds) {
        if (fileIds.isEmpty()) {
            return Map.of();
        }
        return fileAssetMapper.selectByIds(fileIds).stream()
                .collect(Collectors.toMap(FileAsset::getId, asset -> asset, (left, right) -> left));
    }

    private Map<Long, String> ownerNames(Collection<FileAsset> assets) {
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

    private long activeResourceAttachmentCount(Long resourceId) {
        List<ResourceAttachment> attachments = resourceAttachmentMapper.selectList(
                new LambdaQueryWrapper<ResourceAttachment>()
                        .eq(ResourceAttachment::getResourceId, resourceId)
        );
        if (attachments.isEmpty()) {
            return 0;
        }
        List<Long> fileIds = attachments.stream()
                .map(ResourceAttachment::getFileId)
                .distinct()
                .toList();
        return fileAssetMapper.selectByIds(fileIds).stream()
                .filter(asset -> FileAsset.STATUS_ACTIVE.equals(asset.getStatus()))
                .count();
    }

    private record AttachmentView(
            Long id,
            Long fileId,
            Integer sortOrder,
            Integer versionNo,
            LocalDateTime createdAt
    ) {
    }
}
