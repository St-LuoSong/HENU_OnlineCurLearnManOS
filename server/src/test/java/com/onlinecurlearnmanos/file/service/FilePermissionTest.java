package com.onlinecurlearnmanos.file.service;

import com.onlinecurlearnmanos.cloudclass.entity.CloudClass;
import com.onlinecurlearnmanos.cloudclass.entity.CloudClassMember;
import com.onlinecurlearnmanos.cloudclass.entity.CloudClassResource;
import com.onlinecurlearnmanos.cloudclass.entity.CloudClassSubmission;
import com.onlinecurlearnmanos.cloudclass.entity.CloudClassTask;
import com.onlinecurlearnmanos.cloudclass.mapper.CloudClassMapper;
import com.onlinecurlearnmanos.cloudclass.mapper.CloudClassMemberMapper;
import com.onlinecurlearnmanos.cloudclass.mapper.CloudClassResourceMapper;
import com.onlinecurlearnmanos.cloudclass.mapper.CloudClassSubmissionMapper;
import com.onlinecurlearnmanos.cloudclass.mapper.CloudClassTaskMapper;
import com.onlinecurlearnmanos.common.exception.BusinessException;
import com.onlinecurlearnmanos.content.entity.LearningResource;
import com.onlinecurlearnmanos.content.mapper.LearningResourceMapper;
import com.onlinecurlearnmanos.file.entity.CloudResourceAttachment;
import com.onlinecurlearnmanos.file.entity.CloudSubmissionAttachment;
import com.onlinecurlearnmanos.file.entity.FileAsset;
import com.onlinecurlearnmanos.file.entity.ResourceAttachment;
import com.onlinecurlearnmanos.file.mapper.CloudResourceAttachmentMapper;
import com.onlinecurlearnmanos.file.mapper.CloudSubmissionAttachmentMapper;
import com.onlinecurlearnmanos.file.mapper.ResourceAttachmentMapper;
import com.onlinecurlearnmanos.forum.entity.ForumPostMedia;
import com.onlinecurlearnmanos.forum.mapper.ForumPostMapper;
import com.onlinecurlearnmanos.forum.mapper.ForumPostMediaMapper;
import com.onlinecurlearnmanos.security.UserPrincipal;
import com.onlinecurlearnmanos.support.MybatisLambdaCache;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * 文件权限矩阵：管理员 / 上传者 / 平台资源读者 / 云班级成员 / 班级教师 / 提交者本人。
 */
class FilePermissionTest {

    private static final Long FILE_ID = 7L;
    private static final Long OWNER_ID = 31L;

    private final ResourceAttachmentMapper resourceAttachmentMapper =
            mock(ResourceAttachmentMapper.class);
    private final CloudResourceAttachmentMapper cloudResourceAttachmentMapper =
            mock(CloudResourceAttachmentMapper.class);
    private final CloudSubmissionAttachmentMapper cloudSubmissionAttachmentMapper =
            mock(CloudSubmissionAttachmentMapper.class);
    private final LearningResourceMapper learningResourceMapper =
            mock(LearningResourceMapper.class);
    private final CloudClassResourceMapper cloudClassResourceMapper =
            mock(CloudClassResourceMapper.class);
    private final CloudClassMapper cloudClassMapper = mock(CloudClassMapper.class);
    private final CloudClassMemberMapper cloudClassMemberMapper =
            mock(CloudClassMemberMapper.class);
    private final CloudClassSubmissionMapper cloudClassSubmissionMapper =
            mock(CloudClassSubmissionMapper.class);
    private final CloudClassTaskMapper cloudClassTaskMapper = mock(CloudClassTaskMapper.class);
    private final ForumPostMediaMapper forumPostMediaMapper = mock(ForumPostMediaMapper.class);
    private final ForumPostMapper forumPostMapper = mock(ForumPostMapper.class);

    private FileAccessGuard guard;
    private FileAsset asset;

    @BeforeAll
    static void registerLambdaCache() {
        MybatisLambdaCache.register(
                ResourceAttachment.class,
                CloudResourceAttachment.class,
                CloudSubmissionAttachment.class,
                CloudClassMember.class,
                ForumPostMedia.class
        );
    }

    @BeforeEach
    void setUp() {
        guard = new FileAccessGuard(
                resourceAttachmentMapper,
                cloudResourceAttachmentMapper,
                cloudSubmissionAttachmentMapper,
                forumPostMediaMapper,
                forumPostMapper,
                learningResourceMapper,
                cloudClassResourceMapper,
                cloudClassMapper,
                cloudClassMemberMapper,
                cloudClassSubmissionMapper,
                cloudClassTaskMapper
        );
        asset = new FileAsset();
        asset.setId(FILE_ID);
        asset.setOwnerId(OWNER_ID);
        asset.setExtension("pdf");
        asset.setStatus(FileAsset.STATUS_ACTIVE);
    }

    private UserPrincipal principal(Long id, String... roles) {
        return new UserPrincipal(id, "user" + id, "n/a", "用户" + id, null, 1, List.of(roles));
    }

    private ResourceAttachment resourceAttachment(Long resourceId) {
        ResourceAttachment attachment = new ResourceAttachment();
        attachment.setId(1L);
        attachment.setResourceId(resourceId);
        attachment.setFileId(FILE_ID);
        return attachment;
    }

    private LearningResource resource(String status) {
        LearningResource resource = new LearningResource();
        resource.setId(5L);
        resource.setStatus(status);
        resource.setPublisherId(88L);
        return resource;
    }

    @Test
    void adminAndOwnerAlwaysRead() {
        assertThat(guard.canRead(asset, principal(1L, "ADMIN"))).isTrue();
        assertThat(guard.canRead(asset, principal(OWNER_ID, "STUDENT"))).isTrue();
    }

    @Test
    void unattachedFileIsNotReadableByOthers() {
        assertThat(guard.canRead(asset, principal(55L, "STUDENT"))).isFalse();
    }

    @Test
    void publishedPlatformResourceAttachmentIsReadableByAnyUser() {
        given(resourceAttachmentMapper.selectList(any())).willReturn(List.of(resourceAttachment(5L)));
        given(learningResourceMapper.selectByIds(anyList())).willReturn(List.of(resource("PUBLISHED")));

        assertThat(guard.canRead(asset, principal(55L, "STUDENT"))).isTrue();
    }

    @Test
    void unpublishedPlatformResourceAttachmentIsNotReadable() {
        given(resourceAttachmentMapper.selectList(any())).willReturn(List.of(resourceAttachment(5L)));
        given(learningResourceMapper.selectByIds(anyList())).willReturn(List.of(resource("DRAFT")));

        assertThat(guard.canRead(asset, principal(55L, "STUDENT"))).isFalse();
    }

    @Test
    void classResourceAttachmentIsReadableByActiveMemberOnly() {
        CloudResourceAttachment attachment = new CloudResourceAttachment();
        attachment.setId(2L);
        attachment.setClassResourceId(9L);
        attachment.setFileId(FILE_ID);
        CloudClassResource classResource = new CloudClassResource();
        classResource.setId(9L);
        classResource.setClassId(3L);
        CloudClass cloudClass = new CloudClass();
        cloudClass.setId(3L);
        cloudClass.setTeacherId(99L);
        given(cloudResourceAttachmentMapper.selectList(any())).willReturn(List.of(attachment));
        given(cloudClassResourceMapper.selectById(9L)).willReturn(classResource);
        given(cloudClassMapper.selectById(3L)).willReturn(cloudClass);
        given(cloudClassMemberMapper.selectCount(any())).willReturn(1L);

        assertThat(guard.canRead(asset, principal(55L, "STUDENT"))).isTrue();
        assertThatCode(() -> guard.requireClassVisible(3L, principal(55L, "STUDENT")))
                .doesNotThrowAnyException();
    }

    @Test
    void classResourceAttachmentIsHiddenFromOutsider() {
        CloudResourceAttachment attachment = new CloudResourceAttachment();
        attachment.setId(2L);
        attachment.setClassResourceId(9L);
        attachment.setFileId(FILE_ID);
        CloudClassResource classResource = new CloudClassResource();
        classResource.setId(9L);
        classResource.setClassId(3L);
        CloudClass cloudClass = new CloudClass();
        cloudClass.setId(3L);
        cloudClass.setTeacherId(99L);
        given(cloudResourceAttachmentMapper.selectList(any())).willReturn(List.of(attachment));
        given(cloudClassResourceMapper.selectById(9L)).willReturn(classResource);
        given(cloudClassMapper.selectById(3L)).willReturn(cloudClass);
        given(cloudClassMemberMapper.selectCount(any())).willReturn(0L);

        assertThat(guard.canRead(asset, principal(55L, "STUDENT"))).isFalse();
        assertThatThrownBy(() -> guard.requireClassVisible(3L, principal(55L, "STUDENT")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权访问该云班级");
    }

    @Test
    void submissionAttachmentIsReadableByOwnerAndClassTeacher() {
        CloudSubmissionAttachment attachment = new CloudSubmissionAttachment();
        attachment.setId(3L);
        attachment.setSubmissionId(11L);
        attachment.setFileId(FILE_ID);
        CloudClassSubmission submission = new CloudClassSubmission();
        submission.setId(11L);
        submission.setStudentId(OWNER_ID);
        submission.setTaskId(21L);
        CloudClassTask task = new CloudClassTask();
        task.setId(21L);
        task.setClassId(3L);
        CloudClass cloudClass = new CloudClass();
        cloudClass.setId(3L);
        cloudClass.setTeacherId(99L);
        given(cloudSubmissionAttachmentMapper.selectList(any())).willReturn(List.of(attachment));
        given(cloudClassSubmissionMapper.selectById(11L)).willReturn(submission);
        given(cloudClassTaskMapper.selectById(21L)).willReturn(task);
        given(cloudClassMapper.selectById(3L)).willReturn(cloudClass);

        assertThat(guard.canRead(asset, principal(99L, "TEACHER"))).isTrue();
        assertThat(guard.canRead(asset, principal(55L, "STUDENT"))).isFalse();
    }

    @Test
    void requirementHelpersRejectUnauthorizedUsers() {
        UserPrincipal stranger = principal(55L, "STUDENT");

        assertThatThrownBy(() -> guard.requireReadable(asset, stranger))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权访问该文件");
        assertThatThrownBy(() -> guard.requireOwnerOrAdmin(asset, stranger))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只有上传者或管理员");
        assertThatCode(() -> guard.requireOwnerOrAdmin(asset, principal(OWNER_ID, "STUDENT")))
                .doesNotThrowAnyException();
    }

    /** 16-D：班级教学图片沿用班级可见性，班级外用户必须 403。 */
    @Test
    void classResourceImageIsReadableByClassMemberOnly() {
        asset.setPurpose(FilePurpose.CLASS_RESOURCE_IMAGE.name());
        asset.setExtension("png");
        given(cloudResourceAttachmentMapper.selectList(any())).willReturn(List.of(classResourceImage()));
        given(cloudClassResourceMapper.selectById(9L)).willReturn(classResource());
        given(cloudClassMapper.selectById(3L)).willReturn(cloudClass());
        given(cloudClassMemberMapper.selectCount(any())).willReturn(1L);

        assertThatCode(() -> guard.requireReadable(asset, principal(55L, "STUDENT")))
                .doesNotThrowAnyException();
    }

    /** 16-D：作业图片只对提交者本人与该班教师可读，其他学生拿到 id 也必须 403。 */
    @Test
    void submissionImageIsHiddenFromOtherStudents() {
        asset.setPurpose(FilePurpose.SUBMISSION_IMAGE.name());
        asset.setExtension("png");
        CloudSubmissionAttachment attachment = new CloudSubmissionAttachment();
        attachment.setId(3L);
        attachment.setSubmissionId(11L);
        attachment.setFileId(FILE_ID);
        CloudClassSubmission submission = new CloudClassSubmission();
        submission.setId(11L);
        submission.setStudentId(OWNER_ID);
        submission.setTaskId(21L);
        CloudClassTask task = new CloudClassTask();
        task.setId(21L);
        task.setClassId(3L);
        given(cloudSubmissionAttachmentMapper.selectList(any())).willReturn(List.of(attachment));
        given(cloudClassSubmissionMapper.selectById(11L)).willReturn(submission);
        given(cloudClassTaskMapper.selectById(21L)).willReturn(task);
        given(cloudClassMapper.selectById(3L)).willReturn(cloudClass());

        assertThat(guard.canRead(asset, principal(99L, "TEACHER"))).isTrue();
        assertThat(guard.canRead(asset, principal(OWNER_ID, "STUDENT"))).isTrue();
        assertThat(guard.canRead(asset, principal(55L, "STUDENT"))).isFalse();
        assertThatThrownBy(() -> guard.requireReadable(asset, principal(55L, "STUDENT")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权访问该文件");
    }

    private CloudResourceAttachment classResourceImage() {
        CloudResourceAttachment attachment = new CloudResourceAttachment();
        attachment.setId(2L);
        attachment.setClassResourceId(9L);
        attachment.setFileId(FILE_ID);
        return attachment;
    }

    private CloudClassResource classResource() {
        CloudClassResource classResource = new CloudClassResource();
        classResource.setId(9L);
        classResource.setClassId(3L);
        return classResource;
    }

    private CloudClass cloudClass() {
        CloudClass cloudClass = new CloudClass();
        cloudClass.setId(3L);
        cloudClass.setTeacherId(99L);
        return cloudClass;
    }
}
