package com.onlinecurlearnmanos.forum.service;

import com.onlinecurlearnmanos.common.error.ErrorCode;
import com.onlinecurlearnmanos.common.exception.BusinessException;
import com.onlinecurlearnmanos.forum.dto.ForumAnnouncementResponse;
import com.onlinecurlearnmanos.forum.dto.ForumMessageAckRequest;
import com.onlinecurlearnmanos.forum.dto.ForumMessageListResponse;
import com.onlinecurlearnmanos.forum.dto.ForumMessageResponse;
import com.onlinecurlearnmanos.forum.entity.ForumAnnouncement;
import com.onlinecurlearnmanos.forum.entity.ForumMessageAck;
import com.onlinecurlearnmanos.forum.entity.ForumPost;
import com.onlinecurlearnmanos.forum.entity.ForumReply;
import com.onlinecurlearnmanos.forum.mapper.ForumAnnouncementMapper;
import com.onlinecurlearnmanos.forum.mapper.ForumMessageAckMapper;
import com.onlinecurlearnmanos.forum.mapper.ForumPostMapper;
import com.onlinecurlearnmanos.forum.mapper.ForumReplyMapper;
import com.onlinecurlearnmanos.security.UserPrincipal;
import com.onlinecurlearnmanos.user.entity.SysUser;
import com.onlinecurlearnmanos.user.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 论坛轻量消息流。
 *
 * <p>消息不单独落库：回复消息从“我发布的帖子 + 他人回复”实时派生，教师提醒从
 * 现有公示派生；数据库只保存用户是否已了解，避免复制业务内容造成状态漂移。</p>
 */
@Service
@RequiredArgsConstructor
public class ForumMessageService {

    private static final String TYPE_REPLY = "REPLY";
    private static final String TYPE_ANNOUNCEMENT = "ANNOUNCEMENT";
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final int MAX_LIMIT = 10;
    private static final int MAX_MY_POSTS = 100;
    private static final int MAX_REPLIES = 40;
    private static final int MAX_ANNOUNCEMENTS = 20;

    private final ForumPostMapper postMapper;
    private final ForumReplyMapper replyMapper;
    private final ForumAnnouncementService announcementService;
    private final ForumAnnouncementMapper announcementMapper;
    private final ForumMessageAckMapper ackMapper;
    private final SysUserMapper userMapper;

    public ForumMessageListResponse list(UserPrincipal principal, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), MAX_LIMIT);
        List<Candidate> candidates = new ArrayList<>();
        candidates.addAll(loadReplyCandidates(principal));
        candidates.addAll(loadAnnouncementCandidates(principal));
        candidates.sort(Comparator.comparing(
                Candidate::createdAt,
                Comparator.nullsLast(Comparator.<LocalDateTime>reverseOrder())
        ));

        Set<MessageKey> acknowledged = loadAcknowledgedKeys(principal.getId(), candidates);
        // 右侧栏展示固定数量的最新消息，未读数也只统计当前可见的消息，避免出现无法逐条处理的隐藏未读。
        int unreadCount = (int) candidates.stream()
                .limit(safeLimit)
                .filter(candidate -> !acknowledged.contains(candidate.key()))
                .count();
        List<ForumMessageResponse> records = candidates.stream()
                .limit(safeLimit)
                .map(candidate -> candidate.toResponse(acknowledged.contains(candidate.key())))
                .toList();
        return new ForumMessageListResponse(records, unreadCount);
    }

    @Transactional
    public void acknowledge(UserPrincipal principal, ForumMessageAckRequest request) {
        String type = normalizeType(request.type());
        if (TYPE_REPLY.equals(type)) {
            assertCanAcknowledgeReply(principal, request.sourceId());
        } else {
            assertCanAcknowledgeAnnouncement(principal, request.sourceId());
        }
        ackMapper.upsert(principal.getId(), type, request.sourceId());
    }

    private List<Candidate> loadReplyCandidates(UserPrincipal principal) {
        List<ForumPost> myPosts = postMapper.selectList(new LambdaQueryWrapper<ForumPost>()
                .eq(ForumPost::getAuthorId, principal.getId())
                .eq(ForumPost::getStatus, STATUS_PUBLISHED)
                .orderByDesc(ForumPost::getCreatedAt)
                .orderByDesc(ForumPost::getId)
                .last("LIMIT " + MAX_MY_POSTS));
        if (myPosts.isEmpty()) {
            return List.of();
        }

        Map<Long, ForumPost> postsById = myPosts.stream()
                .collect(Collectors.toMap(ForumPost::getId, Function.identity()));
        List<ForumReply> replies = replyMapper.selectList(new LambdaQueryWrapper<ForumReply>()
                .in(ForumReply::getPostId, postsById.keySet())
                .ne(ForumReply::getAuthorId, principal.getId())
                .eq(ForumReply::getStatus, STATUS_PUBLISHED)
                .orderByDesc(ForumReply::getCreatedAt)
                .orderByDesc(ForumReply::getId)
                .last("LIMIT " + MAX_REPLIES));
        if (replies.isEmpty()) {
            return List.of();
        }

        Map<Long, SysUser> authors = loadAuthors(
                replies.stream().map(ForumReply::getAuthorId).toList()
        );
        return replies.stream()
                .map(reply -> {
                    ForumPost post = postsById.get(reply.getPostId());
                    if (post == null) {
                        return null;
                    }
                    SysUser author = authors.get(reply.getAuthorId());
                    return new Candidate(
                            TYPE_REPLY,
                            reply.getId(),
                            post.getId(),
                            reply.getId(),
                            post.getTitle(),
                            preview(reply.getContent()),
                            reply.getAuthorId(),
                            author == null ? "未知用户" : author.getDisplayName(),
                            reply.getCreatedAt()
                    );
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private List<Candidate> loadAnnouncementCandidates(UserPrincipal principal) {
        List<ForumAnnouncementResponse> announcements =
                announcementService.listPublished(MAX_ANNOUNCEMENTS, principal);
        return announcements.stream()
                .filter(item -> !Objects.equals(item.authorId(), principal.getId()))
                .map(item -> new Candidate(
                        TYPE_ANNOUNCEMENT,
                        item.id(),
                        null,
                        null,
                        item.title(),
                        preview(item.content()),
                        item.authorId(),
                        item.authorName(),
                        item.createdAt()
                ))
                .toList();
    }

    private Set<MessageKey> loadAcknowledgedKeys(Long userId, List<Candidate> candidates) {
        if (candidates.isEmpty()) {
            return Set.of();
        }
        List<Long> sourceIds = candidates.stream()
                .map(Candidate::sourceId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (sourceIds.isEmpty()) {
            return Set.of();
        }
        List<ForumMessageAck> rows = ackMapper.selectList(
                new LambdaQueryWrapper<ForumMessageAck>()
                        .eq(ForumMessageAck::getUserId, userId)
                        .in(ForumMessageAck::getMessageType, List.of(TYPE_REPLY, TYPE_ANNOUNCEMENT))
                        .in(ForumMessageAck::getSourceId, sourceIds)
        );
        return rows.stream()
                .map(row -> new MessageKey(row.getMessageType(), row.getSourceId()))
                .collect(Collectors.toCollection(HashSet::new));
    }

    private Map<Long, SysUser> loadAuthors(List<Long> authorIds) {
        List<Long> ids = authorIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectByIdsIncludingDeleted(ids).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
    }

    private void assertCanAcknowledgeReply(UserPrincipal principal, Long replyId) {
        ForumReply reply = replyMapper.selectById(replyId);
        if (reply == null || !STATUS_PUBLISHED.equals(reply.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "回复消息不存在或已失效");
        }
        ForumPost post = postMapper.selectById(reply.getPostId());
        if (post == null || !STATUS_PUBLISHED.equals(post.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "关联帖子不存在或已隐藏");
        }
        if (!Objects.equals(post.getAuthorId(), principal.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能了解与自己帖子相关的回复");
        }
        if (Objects.equals(reply.getAuthorId(), principal.getId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "自己的回复不会生成消息");
        }
    }

    private void assertCanAcknowledgeAnnouncement(UserPrincipal principal, Long announcementId) {
        ForumAnnouncement announcement = announcementMapper.selectById(announcementId);
        if (announcement == null
                || !ForumAnnouncement.STATUS_PUBLISHED.equals(announcement.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "教师提醒不存在或已失效");
        }
        if (Objects.equals(announcement.getAuthorId(), principal.getId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "自己发布的公示不会生成提醒");
        }
    }

    private String normalizeType(String type) {
        String normalized = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        if (!TYPE_REPLY.equals(normalized) && !TYPE_ANNOUNCEMENT.equals(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的消息类型");
        }
        return normalized;
    }

    private String preview(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String normalized = content.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 120 ? normalized : normalized.substring(0, 120) + "...";
    }

    private record Candidate(
            String type,
            Long sourceId,
            Long postId,
            Long replyId,
            String title,
            String content,
            Long authorId,
            String authorName,
            LocalDateTime createdAt
    ) {

        MessageKey key() {
            return new MessageKey(type, sourceId);
        }

        ForumMessageResponse toResponse(boolean acknowledged) {
            return new ForumMessageResponse(
                    type,
                    sourceId,
                    postId,
                    replyId,
                    title,
                    content,
                    authorId,
                    authorName,
                    createdAt,
                    acknowledged
            );
        }
    }

    private record MessageKey(String type, Long sourceId) {
    }
}
