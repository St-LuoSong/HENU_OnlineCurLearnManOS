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
import com.onlinecurlearnmanos.common.error.ErrorCode;
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
import com.onlinecurlearnmanos.forum.entity.ForumPost;
import com.onlinecurlearnmanos.forum.entity.ForumPostMedia;
import com.onlinecurlearnmanos.forum.mapper.ForumPostMapper;
import com.onlinecurlearnmanos.forum.mapper.ForumPostMediaMapper;
import com.onlinecurlearnmanos.security.UserPrincipal;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * 文件读取权限的唯一判定入口。
 *
 * <p>规则：管理员与文件所有者始终可读；其他人只有在文件已挂载到其可见的业务对象上时才可读——
 * 已发布平台资源的附件对全平台可读、云班级资源附件只对班级教师与在班学生可读、
 * 作业附件只对提交者本人与该班教师可读、论坛图片只对可见帖子的读者可读。
 * 刚上传但尚未挂载的文件只有上传者与管理员可读。</p>
 */
@Component
@RequiredArgsConstructor
public class FileAccessGuard {

    private final ResourceAttachmentMapper resourceAttachmentMapper;
    private final CloudResourceAttachmentMapper cloudResourceAttachmentMapper;
    private final CloudSubmissionAttachmentMapper cloudSubmissionAttachmentMapper;
    private final ForumPostMediaMapper forumPostMediaMapper;
    private final ForumPostMapper forumPostMapper;
    private final LearningResourceMapper learningResourceMapper;
    private final CloudClassResourceMapper cloudClassResourceMapper;
    private final CloudClassMapper cloudClassMapper;
    private final CloudClassMemberMapper cloudClassMemberMapper;
    private final CloudClassSubmissionMapper cloudClassSubmissionMapper;
    private final CloudClassTaskMapper cloudClassTaskMapper;

    public boolean isAdmin(UserPrincipal principal) {
        return principal != null && principal.getRoles().contains("ADMIN");
    }

    public boolean isOwner(FileAsset asset, UserPrincipal principal) {
        return principal != null && Objects.equals(asset.getOwnerId(), principal.getId());
    }

    public boolean canRead(FileAsset asset, UserPrincipal principal) {
        if (principal == null) {
            return false;
        }
        if (isAdmin(principal) || isOwner(asset, principal)) {
            return true;
        }
        return canReadViaResource(asset.getId(), principal)
                || canReadViaClassResource(asset.getId(), principal)
                || canReadViaSubmission(asset.getId(), principal)
                || canReadViaForumPost(asset.getId(), principal);
    }

    public void requireReadable(FileAsset asset, UserPrincipal principal) {
        if (!canRead(asset, principal)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该文件");
        }
    }

    public void requireOwnerOrAdmin(FileAsset asset, UserPrincipal principal) {
        if (!isAdmin(principal) && !isOwner(asset, principal)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有上传者或管理员可以删除该文件");
        }
    }

    /** 云班级资源附件等场景复用同一套班级可见性规则。 */
    public void requireClassVisible(Long classId, UserPrincipal principal) {
        if (isAdmin(principal)) {
            return;
        }
        if (classId == null || !isClassVisible(classId, principal)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该云班级的内容");
        }
    }

    /** 已发布平台资源的附件对所有登录用户可读。 */
    private boolean canReadViaResource(Long fileId, UserPrincipal principal) {
        List<Long> resourceIds = resourceAttachmentMapper.selectList(
                        new LambdaQueryWrapper<ResourceAttachment>()
                                .eq(ResourceAttachment::getFileId, fileId))
                .stream()
                .map(ResourceAttachment::getResourceId)
                .distinct()
                .toList();
        if (resourceIds.isEmpty()) {
            return false;
        }
        return learningResourceMapper.selectByIds(resourceIds).stream()
                .anyMatch(this::isPublished);
    }

    private boolean isPublished(LearningResource resource) {
        return resource != null && "PUBLISHED".equalsIgnoreCase(resource.getStatus());
    }

    /** 云班级资源附件只对班级教师与在班学生可读。 */
    private boolean canReadViaClassResource(Long fileId, UserPrincipal principal) {
        List<Long> classResourceIds = cloudResourceAttachmentMapper.selectList(
                        new LambdaQueryWrapper<CloudResourceAttachment>()
                                .eq(CloudResourceAttachment::getFileId, fileId))
                .stream()
                .map(CloudResourceAttachment::getClassResourceId)
                .distinct()
                .toList();
        for (Long classResourceId : classResourceIds) {
            CloudClassResource resource = cloudClassResourceMapper.selectById(classResourceId);
            if (resource == null || !isClassVisible(resource.getClassId(), principal)) {
                continue;
            }
            return true;
        }
        return false;
    }

    private boolean isClassVisible(Long classId, UserPrincipal principal) {
        CloudClass cloudClass = cloudClassMapper.selectById(classId);
        if (cloudClass == null) {
            return false;
        }
        if (Objects.equals(cloudClass.getTeacherId(), principal.getId())) {
            return true;
        }
        Long members = cloudClassMemberMapper.selectCount(new LambdaQueryWrapper<CloudClassMember>()
                .eq(CloudClassMember::getClassId, classId)
                .eq(CloudClassMember::getStudentId, principal.getId())
                .eq(CloudClassMember::getStatus, "ACTIVE"));
        return members != null && members > 0;
    }

    /** 作业附件只对提交者本人与该班教师可读。 */
    private boolean canReadViaSubmission(Long fileId, UserPrincipal principal) {
        List<Long> submissionIds = cloudSubmissionAttachmentMapper.selectList(
                        new LambdaQueryWrapper<CloudSubmissionAttachment>()
                                .eq(CloudSubmissionAttachment::getFileId, fileId))
                .stream()
                .map(CloudSubmissionAttachment::getSubmissionId)
                .distinct()
                .toList();
        for (Long submissionId : submissionIds) {
            CloudClassSubmission submission = cloudClassSubmissionMapper.selectById(submissionId);
            if (submission == null) {
                continue;
            }
            if (Objects.equals(submission.getStudentId(), principal.getId())) {
                return true;
            }
            CloudClassTask task = cloudClassTaskMapper.selectById(submission.getTaskId());
            if (task == null) {
                continue;
            }
            CloudClass cloudClass = cloudClassMapper.selectById(task.getClassId());
            if (cloudClass != null && Objects.equals(cloudClass.getTeacherId(), principal.getId())) {
                return true;
            }
        }
        return false;
    }
    /** 论坛图片：帖子处于已发布状态时对登录用户可读，帖子被隐藏后仅上传者与管理员可读。 */
    private boolean canReadViaForumPost(Long fileId, UserPrincipal principal) {
        List<Long> postIds = forumPostMediaMapper.selectList(
                        new LambdaQueryWrapper<ForumPostMedia>()
                                .eq(ForumPostMedia::getFileId, fileId))
                .stream()
                .map(ForumPostMedia::getPostId)
                .distinct()
                .toList();
        for (Long postId : postIds) {
            ForumPost post = forumPostMapper.selectById(postId);
            if (post != null && "PUBLISHED".equalsIgnoreCase(post.getStatus())) {
                return true;
            }
        }
        return false;
    }

}
