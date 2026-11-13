package com.onlinecurlearnmanos.content.service;

import com.onlinecurlearnmanos.admin.service.AdminAuditService;
import com.onlinecurlearnmanos.common.api.PageResult;
import com.onlinecurlearnmanos.common.error.ErrorCode;
import com.onlinecurlearnmanos.common.exception.BusinessException;
import com.onlinecurlearnmanos.content.dto.LearningResourceResponse;
import com.onlinecurlearnmanos.content.dto.LearningResourceSaveRequest;
import com.onlinecurlearnmanos.content.dto.ResourceAttachmentSummary;
import com.onlinecurlearnmanos.content.entity.KnowledgePoint;
import com.onlinecurlearnmanos.content.entity.LearningResource;
import com.onlinecurlearnmanos.content.entity.Subject;
import com.onlinecurlearnmanos.content.mapper.KnowledgePointMapper;
import com.onlinecurlearnmanos.content.mapper.LearningResourceMapper;
import com.onlinecurlearnmanos.content.mapper.ResourceKnowledgeMapper;
import com.onlinecurlearnmanos.content.mapper.ResourceViewLogMapper;
import com.onlinecurlearnmanos.content.mapper.SubjectMapper;
import com.onlinecurlearnmanos.file.entity.FileAsset;
import com.onlinecurlearnmanos.file.entity.ResourceAttachment;
import com.onlinecurlearnmanos.file.mapper.FileAssetMapper;
import com.onlinecurlearnmanos.file.mapper.ResourceAttachmentMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LearningResourceServiceImpl implements LearningResourceService {

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final int MAX_PAGE_SIZE = 50;

    private final LearningResourceMapper resourceMapper;
    private final ResourceKnowledgeMapper resourceKnowledgeMapper;
    private final SubjectMapper subjectMapper;
    private final KnowledgePointMapper knowledgePointMapper;
    private final ResourceAttachmentMapper resourceAttachmentMapper;
    private final ResourceViewLogMapper resourceViewLogMapper;
    private final FileAssetMapper fileAssetMapper;
    private final AdminAuditService auditService;

    @Override
    public PageResult<LearningResourceResponse> list(
            Long subjectId,
            Long knowledgePointId,
            String keyword,
            long page,
            long size,
            boolean includeUnpublished
    ) {
        long safePage = Math.max(page, 1);
        long safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        LambdaQueryWrapper<LearningResource> wrapper = new LambdaQueryWrapper<>();
        if (subjectId != null) {
            wrapper.eq(LearningResource::getSubjectId, subjectId);
        }
        if (knowledgePointId != null) {
            wrapper.exists(
                    "SELECT 1 FROM resource_knowledge rk "
                            + "WHERE rk.resource_id = learning_resource.id "
                            + "AND rk.knowledge_point_id = {0}",
                    knowledgePointId
            );
        }
        if (StringUtils.hasText(keyword)) {
            String normalized = keyword.trim();
            wrapper.and(condition -> condition
                    .like(LearningResource::getTitle, normalized)
                    .or()
                    .like(LearningResource::getDescription, normalized)
            );
        }
        if (!includeUnpublished) {
            wrapper.eq(LearningResource::getStatus, STATUS_PUBLISHED)
                    .exists(
                            "SELECT 1 FROM resource_attachment ra "
                                    + "JOIN file_asset fa ON fa.id = ra.file_id "
                                    + "WHERE ra.resource_id = learning_resource.id "
                                    + "AND fa.status = 'ACTIVE' AND fa.deleted = 0"
                    );
        }
        wrapper.orderByDesc(LearningResource::getUpdatedAt)
                .orderByDesc(LearningResource::getId);
        Page<LearningResource> result = resourceMapper.selectPage(
                new Page<>(safePage, safeSize),
                wrapper
        );
        return new PageResult<>(
                toResponses(result.getRecords()),
                result.getTotal(),
                safePage,
                safeSize
        );
    }

    @Override
    public LearningResourceResponse get(Long id, boolean includeUnpublished) {
        LearningResource resource = getRequired(id);
        if (!includeUnpublished) {
            boolean visible = STATUS_PUBLISHED.equals(resource.getStatus())
                    && activeAttachmentCount(id) > 0;
            if (!visible) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "学习资源不存在或尚未发布");
            }
        }
        return toResponse(resource);
    }

    @Override
    @Transactional
    public void recordView(Long userId, Long resourceId) {
        LearningResource resource = getRequired(resourceId);
        if (!STATUS_PUBLISHED.equals(resource.getStatus())) {
            return;
        }
        resourceViewLogMapper.recordView(
                userId,
                resourceId,
                LocalDate.now(ZoneId.of("Asia/Shanghai"))
        );
    }

    @Override
    @Transactional
    public LearningResourceResponse create(
            LearningResourceSaveRequest request,
            Long publisherId,
            Long operatorId
    ) {
        validateSubject(request.subjectId());
        LearningResource resource = new LearningResource();
        applyRequest(resource, request);
        resource.setPublisherId(publisherId);
        resource.setStatus(STATUS_DRAFT);
        resource.setDeleted(0);
        resource.setVersion(0);
        resourceMapper.insert(resource);
        saveKnowledgeRelations(resource.getId(), request.subjectId(), request.knowledgePointIds());
        auditService.record(
                operatorId,
                "RESOURCE_CREATE",
                "LEARNING_RESOURCE",
                String.valueOf(resource.getId()),
                "创建资源草稿：" + resource.getTitle()
        );
        return toResponse(resource);
    }

    @Override
    @Transactional
    public LearningResourceResponse update(
            Long id,
            LearningResourceSaveRequest request,
            Long operatorId
    ) {
        LearningResource resource = getRequired(id);
        validateSubject(request.subjectId());
        resourceMapper.update(null, new LambdaUpdateWrapper<LearningResource>()
                .eq(LearningResource::getId, id)
                .set(LearningResource::getSubjectId, request.subjectId())
                .set(LearningResource::getTitle, request.title().trim())
                .set(LearningResource::getDescription, normalizeNullable(request.description()))
                .set(LearningResource::getResourceType, request.resourceType().trim().toUpperCase())
                .set(LearningResource::getContent, normalizeNullable(request.content()))
                .set(LearningResource::getExternalUrl, normalizeNullable(request.externalUrl())));
        resourceKnowledgeMapper.deleteByResourceId(id);
        saveKnowledgeRelations(id, request.subjectId(), request.knowledgePointIds());
        auditService.record(
                operatorId,
                "RESOURCE_UPDATE",
                "LEARNING_RESOURCE",
                String.valueOf(id),
                "更新资源：" + request.title().trim()
        );
        return toResponse(getRequired(id));
    }

    @Override
    @Transactional
    public LearningResourceResponse updateStatus(Long id, String status, Long operatorId) {
        LearningResource resource = getRequired(id);
        String normalized = StringUtils.hasText(status)
                ? status.trim().toUpperCase()
                : STATUS_DRAFT;
        if (!STATUS_DRAFT.equals(normalized) && !STATUS_PUBLISHED.equals(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "资源状态不合法");
        }
        if (STATUS_PUBLISHED.equals(normalized) && activeAttachmentCount(id) == 0) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "至少上传一个有效附件后才能发布学习资源"
            );
        }
        resourceMapper.update(null, new LambdaUpdateWrapper<LearningResource>()
                .eq(LearningResource::getId, id)
                .set(LearningResource::getStatus, normalized));
        auditService.record(
                operatorId,
                STATUS_PUBLISHED.equals(normalized) ? "RESOURCE_PUBLISH" : "RESOURCE_UNPUBLISH",
                "LEARNING_RESOURCE",
                String.valueOf(id),
                (STATUS_PUBLISHED.equals(normalized) ? "发布资源：" : "下架资源：")
                        + resource.getTitle()
        );
        return toResponse(getRequired(id));
    }

    @Override
    @Transactional
    public void delete(Long id, Long operatorId) {
        LearningResource resource = getRequired(id);
        resourceAttachmentMapper.delete(new LambdaQueryWrapper<ResourceAttachment>()
                .eq(ResourceAttachment::getResourceId, id));
        resourceKnowledgeMapper.deleteByResourceId(id);
        resourceMapper.deleteById(id);
        auditService.record(
                operatorId,
                "RESOURCE_DELETE",
                "LEARNING_RESOURCE",
                String.valueOf(id),
                "删除资源：" + resource.getTitle()
        );
    }

    private List<LearningResourceResponse> toResponses(List<LearningResource> resources) {
        if (resources.isEmpty()) {
            return List.of();
        }
        List<Long> resourceIds = resources.stream().map(LearningResource::getId).toList();
        List<ResourceAttachment> attachments = resourceAttachmentMapper.selectList(
                new LambdaQueryWrapper<ResourceAttachment>()
                        .in(ResourceAttachment::getResourceId, resourceIds)
                        .orderByAsc(ResourceAttachment::getSortOrder)
                        .orderByAsc(ResourceAttachment::getId)
        );
        Map<Long, List<ResourceAttachment>> attachmentsByResource = attachments.stream()
                .collect(Collectors.groupingBy(
                        ResourceAttachment::getResourceId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        Map<Long, FileAsset> assets = loadActiveAssets(
                attachments.stream().map(ResourceAttachment::getFileId).toList()
        );
        Map<Long, Subject> subjects = loadSubjects(resources);
        Map<Long, String> knowledgePointNames = loadKnowledgePointNames(resourceIds);

        return resources.stream()
                .map(resource -> toResponse(
                        resource,
                        attachmentsByResource.getOrDefault(resource.getId(), List.of()),
                        assets,
                        subjects.get(resource.getSubjectId()),
                        knowledgePointNames
                ))
                .toList();
    }

    private LearningResourceResponse toResponse(LearningResource resource) {
        return toResponses(List.of(resource)).get(0);
    }

    private LearningResourceResponse toResponse(
            LearningResource resource,
            List<ResourceAttachment> attachments,
            Map<Long, FileAsset> assets,
            Subject subject,
            Map<Long, String> knowledgePointNames
    ) {
        List<Long> knowledgePointIds = new ArrayList<>(
                resourceKnowledgeMapper.selectKnowledgePointIds(resource.getId())
        );
        List<String> names = knowledgePointIds.stream()
                .map(knowledgePointNames::get)
                .filter(StringUtils::hasText)
                .toList();
        List<FileAsset> activeAssets = attachments.stream()
                .map(attachment -> assets.get(attachment.getFileId()))
                .filter(java.util.Objects::nonNull)
                .toList();
        FileAsset primary = activeAssets.isEmpty() ? null : activeAssets.get(0);
        ResourceAttachmentSummary primaryAttachment = primary == null
                ? null
                : new ResourceAttachmentSummary(
                        primary.getId(),
                        primary.getOriginalName(),
                        primary.getExtension(),
                        primary.getMimeType(),
                        primary.getSizeBytes() == null ? 0L : primary.getSizeBytes()
                );
        return new LearningResourceResponse(
                resource.getId(),
                resource.getSubjectId(),
                subject == null ? null : subject.getName(),
                resource.getTitle(),
                resource.getDescription(),
                resource.getResourceType(),
                resource.getContent(),
                resource.getExternalUrl(),
                resource.getPublisherId(),
                resource.getStatus(),
                knowledgePointIds,
                names,
                activeAssets.size(),
                primaryAttachment,
                resource.getCreatedAt(),
                resource.getUpdatedAt()
        );
    }

    private Map<Long, Subject> loadSubjects(Collection<LearningResource> resources) {
        List<Long> subjectIds = resources.stream()
                .map(LearningResource::getSubjectId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (subjectIds.isEmpty()) {
            return Map.of();
        }
        return subjectMapper.selectByIds(subjectIds).stream()
                .collect(Collectors.toMap(Subject::getId, Function.identity()));
    }

    private Map<Long, String> loadKnowledgePointNames(Collection<Long> resourceIds) {
        Set<Long> pointIds = new LinkedHashSet<>();
        for (Long resourceId : resourceIds) {
            pointIds.addAll(resourceKnowledgeMapper.selectKnowledgePointIds(resourceId));
        }
        if (pointIds.isEmpty()) {
            return Map.of();
        }
        return knowledgePointMapper.selectByIds(pointIds).stream()
                .collect(Collectors.toMap(KnowledgePoint::getId, KnowledgePoint::getName));
    }

    private Map<Long, FileAsset> loadActiveAssets(Collection<Long> fileIds) {
        List<Long> ids = fileIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return fileAssetMapper.selectByIds(ids).stream()
                .filter(asset -> FileAsset.STATUS_ACTIVE.equals(asset.getStatus()))
                .collect(Collectors.toMap(FileAsset::getId, Function.identity()));
    }

    private long activeAttachmentCount(Long resourceId) {
        List<ResourceAttachment> attachments = resourceAttachmentMapper.selectList(
                new LambdaQueryWrapper<ResourceAttachment>()
                        .eq(ResourceAttachment::getResourceId, resourceId)
        );
        return loadActiveAssets(
                attachments.stream().map(ResourceAttachment::getFileId).toList()
        ).size();
    }

    private void applyRequest(LearningResource resource, LearningResourceSaveRequest request) {
        resource.setSubjectId(request.subjectId());
        resource.setTitle(request.title().trim());
        resource.setDescription(normalizeNullable(request.description()));
        resource.setResourceType(request.resourceType().trim().toUpperCase());
        resource.setContent(normalizeNullable(request.content()));
        resource.setExternalUrl(normalizeNullable(request.externalUrl()));
    }

    private void saveKnowledgeRelations(
            Long resourceId,
            Long subjectId,
            List<Long> knowledgePointIds
    ) {
        if (knowledgePointIds == null || knowledgePointIds.isEmpty()) {
            return;
        }
        Set<Long> uniqueIds = new LinkedHashSet<>(knowledgePointIds);
        long existingCount = knowledgePointMapper.selectCount(
                new LambdaQueryWrapper<KnowledgePoint>()
                        .in(KnowledgePoint::getId, uniqueIds)
                        .eq(KnowledgePoint::getSubjectId, subjectId)
        );
        if (existingCount != uniqueIds.size()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "存在不属于该学科的知识点");
        }
        for (Long knowledgePointId : uniqueIds) {
            resourceKnowledgeMapper.insert(resourceId, knowledgePointId);
        }
    }

    private void validateSubject(Long subjectId) {
        Subject subject = subjectMapper.selectById(subjectId);
        if (subject == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "学科不存在");
        }
    }

    private LearningResource getRequired(Long id) {
        LearningResource resource = resourceMapper.selectById(id);
        if (resource == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "学习资源不存在");
        }
        return resource;
    }

    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
