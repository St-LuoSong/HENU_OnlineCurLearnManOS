package com.onlinecurlearnmanos.file.service;

import com.onlinecurlearnmanos.admin.service.AdminAuditService;
import com.onlinecurlearnmanos.common.exception.BusinessException;
import com.onlinecurlearnmanos.file.entity.CloudResourceAttachment;
import com.onlinecurlearnmanos.file.entity.CloudSubmissionAttachment;
import com.onlinecurlearnmanos.file.entity.FileAsset;
import com.onlinecurlearnmanos.file.entity.ResourceAttachment;
import com.onlinecurlearnmanos.file.mapper.CloudResourceAttachmentMapper;
import com.onlinecurlearnmanos.file.mapper.CloudSubmissionAttachmentMapper;
import com.onlinecurlearnmanos.file.mapper.FileAssetMapper;
import com.onlinecurlearnmanos.file.mapper.ResourceAttachmentMapper;
import com.onlinecurlearnmanos.forum.entity.ForumPostMedia;
import com.onlinecurlearnmanos.forum.mapper.ForumPostMediaMapper;
import com.onlinecurlearnmanos.security.UserPrincipal;
import com.onlinecurlearnmanos.support.InMemoryFileStorage;
import com.onlinecurlearnmanos.support.MybatisLambdaCache;
import com.onlinecurlearnmanos.user.entity.SysUser;
import com.onlinecurlearnmanos.user.mapper.SysUserMapper;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 文件资产服务：落盘与写库的一致性、附件占用时的删除保护、管理员清理与审计。
 */
class FileAssetServiceTest {

    private final FileAssetMapper fileAssetMapper = mock(FileAssetMapper.class);
    private final ResourceAttachmentMapper resourceAttachmentMapper =
            mock(ResourceAttachmentMapper.class);
    private final CloudResourceAttachmentMapper cloudResourceAttachmentMapper =
            mock(CloudResourceAttachmentMapper.class);
    private final CloudSubmissionAttachmentMapper cloudSubmissionAttachmentMapper =
            mock(CloudSubmissionAttachmentMapper.class);
    private final ForumPostMediaMapper forumPostMediaMapper = mock(ForumPostMediaMapper.class);
    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final FileValidator validator = mock(FileValidator.class);
    private final FileAccessGuard accessGuard = mock(FileAccessGuard.class);
    private final AdminAuditService auditService = mock(AdminAuditService.class);
    private final InMemoryFileStorage storage = new InMemoryFileStorage();

    private FileAssetService service;
    private UserPrincipal student;

    @BeforeAll
    static void registerLambdaCache() {
        MybatisLambdaCache.register(
                FileAsset.class,
                ResourceAttachment.class,
                CloudResourceAttachment.class,
                CloudSubmissionAttachment.class,
                ForumPostMedia.class
        );
    }

    @BeforeEach
    void setUp() {
        service = new FileAssetService(
                fileAssetMapper,
                resourceAttachmentMapper,
                cloudResourceAttachmentMapper,
                cloudSubmissionAttachmentMapper,
                forumPostMediaMapper,
                userMapper,
                storage,
                validator,
                accessGuard,
                auditService
        );
        student = new UserPrincipal(31L, "2024001", "n/a", "张三", null, 1, List.of("STUDENT"));
        SysUser owner = new SysUser();
        owner.setId(31L);
        owner.setDisplayName("张三");
        given(userMapper.selectById(31L)).willReturn(owner);
        given(accessGuard.isOwner(any(FileAsset.class), eq(student))).willReturn(true);
    }

    private FileAsset assetEntity(Long id, Long ownerId, String status) {
        FileAsset asset = new FileAsset();
        asset.setId(id);
        asset.setOwnerId(ownerId);
        asset.setOriginalName("报告.pdf");
        asset.setExtension("pdf");
        asset.setMimeType("application/pdf");
        asset.setSizeBytes(12L);
        asset.setSha256("hash-" + id);
        asset.setStoredKey("0123456789abcdef0123456789abcd" + id + ".pdf");
        asset.setPurpose("SUBMISSION");
        asset.setStorageProvider("LOCAL");
        asset.setStatus(status);
        asset.setDownloadCount(0);
        return asset;
    }

    @Test
    void uploadStoresContentThenPersistsRecord() {
        byte[] content = "作业内容".getBytes(StandardCharsets.UTF_8);
        given(validator.validate(any())).willReturn(new ValidatedFile(
                "报告.pdf", "pdf", "application/pdf", content.length, "sha-1", content
        ));
        MockMultipartFile upload = new MockMultipartFile(
                "file", "报告.pdf", "application/pdf", content
        );

        var response = service.upload(student, upload, FilePurpose.SUBMISSION);

        assertThat(response.originalName()).isEqualTo("报告.pdf");
        assertThat(response.downloadCount()).isZero();
        assertThat(response.canDelete()).isTrue();
        ArgumentCaptor<FileAsset> captor = ArgumentCaptor.forClass(FileAsset.class);
        verify(fileAssetMapper).insert(captor.capture());
        assertThat(storage.size()).isEqualTo(1);
        assertThat(storage.content(captor.getValue().getStoredKey())).isEqualTo(content);
    }

    @Test
    void uploadRollsBackStoredFileWhenDatabaseInsertFails() {
        byte[] content = "作业内容".getBytes(StandardCharsets.UTF_8);
        given(validator.validate(any())).willReturn(new ValidatedFile(
                "报告.pdf", "pdf", "application/pdf", content.length, "sha-1", content
        ));
        given(fileAssetMapper.insert(any(FileAsset.class)))
                .willThrow(new IllegalStateException("数据库不可用"));
        MockMultipartFile upload = new MockMultipartFile(
                "file", "报告.pdf", "application/pdf", content
        );

        assertThatThrownBy(() -> service.upload(student, upload, FilePurpose.SUBMISSION))
                .isInstanceOf(IllegalStateException.class);
        assertThat(storage.size()).isZero();
    }

    @Test
    void deleteIsRejectedWhileFileIsStillReferenced() {
        FileAsset asset = assetEntity(7L, 31L, FileAsset.STATUS_ACTIVE);
        given(fileAssetMapper.selectById(7L)).willReturn(asset);
        given(resourceAttachmentMapper.selectCount(any())).willReturn(1L);

        assertThatThrownBy(() -> service.delete(7L, student))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请先解除关联");
        verify(fileAssetMapper, never()).deleteById(anyLong());
    }

    @Test
    void deleteRemovesRecordAndStoredContent() {
        FileAsset asset = assetEntity(7L, 31L, FileAsset.STATUS_ACTIVE);
        storage.store(asset.getStoredKey(), "content".getBytes(StandardCharsets.UTF_8));
        given(fileAssetMapper.selectById(7L)).willReturn(asset);
        given(resourceAttachmentMapper.selectCount(any())).willReturn(0L);
        given(cloudResourceAttachmentMapper.selectCount(any())).willReturn(0L);
        given(cloudSubmissionAttachmentMapper.selectCount(any())).willReturn(0L);

        service.delete(7L, student);

        verify(fileAssetMapper).deleteById(7L);
        assertThat(storage.size()).isZero();
        verify(auditService, never()).record(anyLong(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void adminDeleteDetachesAttachmentsAndWritesAuditLog() {
        UserPrincipal admin =
                new UserPrincipal(1L, "admin", "n/a", "管理员", null, 1, List.of("ADMIN"));
        FileAsset asset = assetEntity(7L, 31L, FileAsset.STATUS_ACTIVE);
        storage.store(asset.getStoredKey(), "content".getBytes(StandardCharsets.UTF_8));
        given(fileAssetMapper.selectById(7L)).willReturn(asset);
        given(accessGuard.isAdmin(admin)).willReturn(true);
        given(accessGuard.isOwner(asset, admin)).willReturn(false);
        given(resourceAttachmentMapper.selectCount(any())).willReturn(1L);

        service.delete(7L, admin);

        verify(resourceAttachmentMapper).delete(any());
        verify(cloudResourceAttachmentMapper).delete(any());
        verify(cloudSubmissionAttachmentMapper).delete(any());
        verify(fileAssetMapper).deleteById(7L);
        verify(auditService).record(eq(1L), eq("FILE_DELETE"), eq("FILE_ASSET"), eq("7"), anyString());
        assertThat(storage.size()).isZero();
    }

    @Test
    void purgeOrphansRemovesOnlyUnreferencedFiles() {
        UserPrincipal admin =
                new UserPrincipal(1L, "admin", "n/a", "管理员", null, 1, List.of("ADMIN"));
        ResourceAttachment attachment = new ResourceAttachment();
        attachment.setId(1L);
        attachment.setResourceId(5L);
        attachment.setFileId(2L);
        given(resourceAttachmentMapper.selectList(any())).willReturn(List.of(attachment));
        FileAsset orphanOne = assetEntity(1L, 31L, FileAsset.STATUS_ACTIVE);
        FileAsset orphanTwo = assetEntity(3L, 31L, FileAsset.STATUS_ACTIVE);
        storage.store(orphanOne.getStoredKey(), "a".getBytes(StandardCharsets.UTF_8));
        storage.store(orphanTwo.getStoredKey(), "b".getBytes(StandardCharsets.UTF_8));
        given(fileAssetMapper.selectList(any())).willReturn(List.of(orphanOne, orphanTwo));

        int removed = service.purgeOrphans(1L);

        assertThat(removed).isEqualTo(2);
        verify(fileAssetMapper).deleteById(1L);
        verify(fileAssetMapper).deleteById(3L);
        assertThat(storage.size()).isZero();
        verify(auditService).record(eq(1L), eq("FILE_ORPHAN_PURGE"), eq("FILE_ASSET"), eq("-"), anyString());
    }
}
