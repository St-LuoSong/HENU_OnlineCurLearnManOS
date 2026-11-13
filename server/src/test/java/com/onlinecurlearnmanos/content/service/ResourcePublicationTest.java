package com.onlinecurlearnmanos.content.service;

import com.onlinecurlearnmanos.admin.service.AdminAuditService;
import com.onlinecurlearnmanos.common.api.PageResult;
import com.onlinecurlearnmanos.common.exception.BusinessException;
import com.onlinecurlearnmanos.content.dto.LearningResourceResponse;
import com.onlinecurlearnmanos.content.entity.KnowledgePoint;
import com.onlinecurlearnmanos.content.entity.LearningResource;
import com.onlinecurlearnmanos.content.entity.Subject;
import com.onlinecurlearnmanos.content.mapper.KnowledgePointMapper;
import com.onlinecurlearnmanos.content.mapper.LearningResourceMapper;
import com.onlinecurlearnmanos.content.mapper.ResourceKnowledgeMapper;
import com.onlinecurlearnmanos.content.mapper.SubjectMapper;
import com.onlinecurlearnmanos.file.entity.FileAsset;
import com.onlinecurlearnmanos.file.entity.ResourceAttachment;
import com.onlinecurlearnmanos.file.mapper.FileAssetMapper;
import com.onlinecurlearnmanos.file.mapper.ResourceAttachmentMapper;
import com.onlinecurlearnmanos.support.MybatisLambdaCache;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ResourcePublicationTest {

    private static final Long RESOURCE_ID = 5L;
    private static final Long FILE_ID = 7L;
    private static final Long OPERATOR_ID = 99L;

    @Mock
    private LearningResourceMapper resourceMapper;

    @Mock
    private ResourceKnowledgeMapper resourceKnowledgeMapper;

    @Mock
    private SubjectMapper subjectMapper;

    @Mock
    private KnowledgePointMapper knowledgePointMapper;

    @Mock
    private ResourceAttachmentMapper resourceAttachmentMapper;

    @Mock
    private FileAssetMapper fileAssetMapper;

    @Mock
    private AdminAuditService auditService;

    @InjectMocks
    private LearningResourceServiceImpl service;

    @BeforeAll
    static void registerLambdaCache() {
        MybatisLambdaCache.register(
                LearningResource.class,
                ResourceAttachment.class,
                FileAsset.class,
                Subject.class,
                KnowledgePoint.class
        );
    }

    @Test
    void publishRequiresAtLeastOneActiveAttachment() {
        given(resourceMapper.selectById(RESOURCE_ID)).willReturn(resource("DRAFT"));
        given(resourceAttachmentMapper.selectList(any())).willReturn(List.of());

        assertThatThrownBy(() ->
                service.updateStatus(RESOURCE_ID, "PUBLISHED", OPERATOR_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("至少上传一个有效附件");
    }

    @Test
    void publishSucceedsWithActiveAttachmentAndWritesAudit() {
        LearningResource draft = resource("DRAFT");
        LearningResource published = resource("PUBLISHED");
        given(resourceMapper.selectById(RESOURCE_ID)).willReturn(draft, published);
        given(resourceAttachmentMapper.selectList(any())).willReturn(List.of(attachment()));
        given(fileAssetMapper.selectByIds(any())).willReturn(List.of(activeAsset()));
        given(resourceKnowledgeMapper.selectKnowledgePointIds(RESOURCE_ID)).willReturn(List.of());

        LearningResourceResponse response = service.updateStatus(
                RESOURCE_ID,
                "PUBLISHED",
                OPERATOR_ID
        );

        assertThat(response.status()).isEqualTo("PUBLISHED");
        verify(auditService).record(
                eq(OPERATOR_ID),
                eq("RESOURCE_PUBLISH"),
                eq("LEARNING_RESOURCE"),
                eq(String.valueOf(RESOURCE_ID)),
                any()
        );
    }

    @Test
    void unpublishedResourceIsHiddenFromNonAdmin() {
        given(resourceMapper.selectById(RESOURCE_ID)).willReturn(resource("DRAFT"));

        assertThatThrownBy(() -> service.get(RESOURCE_ID, false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("尚未发布");
    }

    @Test
    void listClampsPageSizeToFifty() {
        given(resourceMapper.selectPage(any(), any())).willAnswer(invocation -> {
            Page<LearningResource> requested = invocation.getArgument(0);
            requested.setRecords(List.of());
            requested.setTotal(25);
            return requested;
        });

        PageResult<LearningResourceResponse> result = service.list(
                null,
                null,
                null,
                0,
                100,
                false
        );

        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(50);
        assertThat(result.total()).isEqualTo(25);
    }

    @Test
    void deleteCleansRelationsAndWritesAudit() {
        given(resourceMapper.selectById(RESOURCE_ID)).willReturn(resource("DRAFT"));

        service.delete(RESOURCE_ID, OPERATOR_ID);

        verify(resourceAttachmentMapper).delete(any());
        verify(resourceKnowledgeMapper).deleteByResourceId(RESOURCE_ID);
        verify(resourceMapper).deleteById(RESOURCE_ID);
        verify(auditService).record(
                eq(OPERATOR_ID),
                eq("RESOURCE_DELETE"),
                eq("LEARNING_RESOURCE"),
                eq(String.valueOf(RESOURCE_ID)),
                any()
        );
    }

    private LearningResource resource(String status) {
        LearningResource resource = new LearningResource();
        resource.setId(RESOURCE_ID);
        resource.setSubjectId(10L);
        resource.setTitle("导数知识库");
        resource.setStatus(status);
        resource.setDeleted(0);
        resource.setVersion(0);
        return resource;
    }

    private ResourceAttachment attachment() {
        ResourceAttachment attachment = new ResourceAttachment();
        attachment.setId(1L);
        attachment.setResourceId(RESOURCE_ID);
        attachment.setFileId(FILE_ID);
        return attachment;
    }

    private FileAsset activeAsset() {
        FileAsset asset = new FileAsset();
        asset.setId(FILE_ID);
        asset.setOriginalName("导数讲义.pdf");
        asset.setExtension("pdf");
        asset.setMimeType("application/pdf");
        asset.setSizeBytes(1024L);
        asset.setStatus(FileAsset.STATUS_ACTIVE);
        return asset;
    }
}
