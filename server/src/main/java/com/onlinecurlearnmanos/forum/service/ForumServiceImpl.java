package com.onlinecurlearnmanos.forum.service;

import com.onlinecurlearnmanos.common.api.PageResult;
import com.onlinecurlearnmanos.common.error.ErrorCode;
import com.onlinecurlearnmanos.common.exception.BusinessException;
import com.onlinecurlearnmanos.forum.dto.ForumCategoryResponse;
import com.onlinecurlearnmanos.forum.dto.ForumMediaResponse;
import com.onlinecurlearnmanos.forum.dto.ForumPostDetailResponse;
import com.onlinecurlearnmanos.forum.dto.ForumPostSaveRequest;
import com.onlinecurlearnmanos.forum.dto.ForumPostSummaryResponse;
import com.onlinecurlearnmanos.forum.dto.ForumRankingsResponse;
import com.onlinecurlearnmanos.forum.dto.ForumReplyResponse;
import com.onlinecurlearnmanos.forum.dto.ForumReplySaveRequest;
import com.onlinecurlearnmanos.forum.entity.ForumCategory;
import com.onlinecurlearnmanos.forum.entity.ForumPost;
import com.onlinecurlearnmanos.forum.entity.ForumReply;
import com.onlinecurlearnmanos.forum.mapper.ForumCategoryMapper;
import com.onlinecurlearnmanos.forum.mapper.ForumLikeMapper;
import com.onlinecurlearnmanos.forum.mapper.ForumPostMapper;
import com.onlinecurlearnmanos.forum.mapper.ForumReplyMapper;
import com.onlinecurlearnmanos.security.UserPrincipal;
import com.onlinecurlearnmanos.user.entity.SysUser;
import com.onlinecurlearnmanos.user.mapper.SysRoleMapper;
import com.onlinecurlearnmanos.user.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class ForumServiceImpl implements ForumService {

    private static final String ROLE_STUDENT = "STUDENT";
    private static final String ROLE_TEACHER = "TEACHER";
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String STATUS_PUBLISHED = "PUBLISHED";

    private final ForumCategoryMapper categoryMapper;
    private final ForumPostMapper postMapper;
    private final ForumReplyMapper replyMapper;
    private final ForumLikeMapper likeMapper;
    private final ForumMediaService mediaService;
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;

    @Override
    public List<ForumCategoryResponse> categories() {
        return categoryMapper.selectList(new LambdaQueryWrapper<ForumCategory>()
                        .orderByAsc(ForumCategory::getSortOrder)
                        .orderByAsc(ForumCategory::getId))
                .stream()
                .map(category -> new ForumCategoryResponse(
                        category.getId(),
                        category.getCode(),
                        category.getName(),
                        category.getDescription()
                ))
                .toList();
    }

    @Override
    public ForumRankingsResponse rankings(String window, int limit, UserPrincipal principal) {
        int safeLimit = Math.min(Math.max(limit, 1), 10);
        String normalized = normalizeWindow(window);
        LocalDateTime since = switch (normalized) {
            case "WEEK" -> LocalDateTime.now().minusDays(7);
            case "MONTH" -> LocalDateTime.now().minusDays(30);
            default -> null;
        };
        return new ForumRankingsResponse(
                normalized,
                toSummaries(topPosts(safeLimit, since, "VIEW"), principal),
                toSummaries(topPosts(safeLimit, since, "LIKE"), principal),
                toSummaries(topPosts(safeLimit, since, "REPLY"), principal)
        );
    }

    /** 排行榜口径：同一时间窗口内按指定指标降序取前 N 条，只统计已发布内容。 */
    private List<ForumPost> topPosts(int limit, LocalDateTime since, String metric) {
        LambdaQueryWrapper<ForumPost> wrapper = new LambdaQueryWrapper<ForumPost>()
                .eq(ForumPost::getStatus, STATUS_PUBLISHED);
        if (since != null) {
            wrapper.ge(ForumPost::getCreatedAt, since);
        }
        switch (metric) {
            case "LIKE" -> wrapper.orderByDesc(ForumPost::getLikeCount);
            case "REPLY" -> wrapper.orderByDesc(ForumPost::getReplyCount);
            default -> wrapper.orderByDesc(ForumPost::getViewCount);
        }
        wrapper.orderByDesc(ForumPost::getId).last("LIMIT " + limit);
        return postMapper.selectList(wrapper);
    }

    private String normalizeWindow(String window) {
        if (!StringUtils.hasText(window)) {
            return "ALL";
        }
        String normalized = window.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "WEEK", "MONTH" -> normalized;
            default -> "ALL";
        };
    }

    @Override
    public PageResult<ForumPostSummaryResponse> listPosts(
            long page,
            long size,
            Long categoryId,
            String keyword,
            UserPrincipal principal
    ) {
        long safePage = Math.max(page, 1);
        long safeSize = Math.min(Math.max(size, 1), 50);
        LambdaQueryWrapper<ForumPost> wrapper = new LambdaQueryWrapper<>();
        if (categoryId != null) {
            wrapper.eq(ForumPost::getCategoryId, categoryId);
        }
        if (StringUtils.hasText(keyword)) {
            String normalized = keyword.trim();
            wrapper.and(condition -> condition
                    .like(ForumPost::getTitle, normalized)
                    .or()
                    .like(ForumPost::getContent, normalized)
            );
        }
        // 被管理员隐藏的内容不出现在公开列表里
        wrapper.eq(ForumPost::getStatus, STATUS_PUBLISHED)
                .orderByDesc(ForumPost::getCreatedAt);

        Page<ForumPost> result = postMapper.selectPage(new Page<>(safePage, safeSize), wrapper);
        List<ForumPostSummaryResponse> records = toSummaries(result.getRecords(), principal);
        return new PageResult<>(records, result.getTotal(), safePage, safeSize);
    }

    @Override
    @Transactional
    public ForumPostDetailResponse getPost(Long id, UserPrincipal principal) {
        ForumPost post = getRequiredPost(id);
        if (!STATUS_PUBLISHED.equals(post.getStatus()) && !isAdmin(principal)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "帖子已被隐藏");
        }
        postMapper.update(null, new LambdaUpdateWrapper<ForumPost>()
                .eq(ForumPost::getId, id)
                .setSql("view_count = view_count + 1"));
        post.setViewCount(intValue(post.getViewCount()) + 1);
        return toDetail(post, principal);
    }

    @Override
    @Transactional
    public ForumPostDetailResponse createPost(UserPrincipal principal, ForumPostSaveRequest request) {
        getRequiredCategory(request.categoryId());
        ForumPost post = new ForumPost();
        post.setCategoryId(request.categoryId());
        post.setAuthorId(principal.getId());
        post.setTitle(request.title().trim());
        post.setContent(request.content().trim());
        post.setViewCount(0);
        post.setLikeCount(0);
        post.setReplyCount(0);
        post.setStatus(STATUS_PUBLISHED);
        post.setDeleted(0);
        post.setVersion(0);
        postMapper.insert(post);
        mediaService.replaceMedia(post.getId(), request.mediaFileIds(), principal);
        // 重新读库，确保 createdAt 等数据库默认生成字段可用（审计 D1）。
        return toDetail(getRequiredPost(post.getId()), principal);
    }

    @Override
    @Transactional
    public ForumPostDetailResponse updatePost(
            Long id,
            UserPrincipal principal,
            ForumPostSaveRequest request
    ) {
        ForumPost post = getRequiredPost(id);
        assertCanEdit(post.getAuthorId(), principal);
        getRequiredCategory(request.categoryId());
        postMapper.update(null, new LambdaUpdateWrapper<ForumPost>()
                .eq(ForumPost::getId, id)
                .set(ForumPost::getCategoryId, request.categoryId())
                .set(ForumPost::getTitle, request.title().trim())
                .set(ForumPost::getContent, request.content().trim()));
        mediaService.replaceMedia(id, request.mediaFileIds(), principal);
        return toDetail(getRequiredPost(id), principal);
    }

    @Override
    @Transactional
    public void deletePost(Long id, UserPrincipal principal) {
        ForumPost post = getRequiredPost(id);
        assertCanDelete(post.getAuthorId(), principal);
        mediaService.detachAll(id);
        replyMapper.delete(new LambdaQueryWrapper<ForumReply>().eq(ForumReply::getPostId, id));
        postMapper.deleteById(id);
    }

    @Override
    @Transactional
    public ForumPostSummaryResponse like(Long id, UserPrincipal principal) {
        ForumPost post = getRequiredPost(id);
        if (Objects.equals(post.getAuthorId(), principal.getId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不能点赞自己的帖子");
        }
        if (likeMapper.count(id, principal.getId()) == 0) {
            likeMapper.insert(id, principal.getId());
            postMapper.update(null, new LambdaUpdateWrapper<ForumPost>()
                    .eq(ForumPost::getId, id)
                    .setSql("like_count = like_count + 1"));
        }
        return toSummaries(List.of(getRequiredPost(id)), principal).get(0);
    }

    @Override
    @Transactional
    public ForumPostSummaryResponse unlike(Long id, UserPrincipal principal) {
        getRequiredPost(id);
        if (likeMapper.delete(id, principal.getId()) > 0) {
            postMapper.update(null, new LambdaUpdateWrapper<ForumPost>()
                    .eq(ForumPost::getId, id)
                    .setSql("like_count = GREATEST(like_count - 1, 0)"));
        }
        return toSummaries(List.of(getRequiredPost(id)), principal).get(0);
    }

    @Override
    @Transactional
    public ForumReplyResponse createReply(
            Long postId,
            UserPrincipal principal,
            ForumReplySaveRequest request
    ) {
        ForumPost post = getRequiredPost(postId);
        if (!STATUS_PUBLISHED.equals(post.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "该帖子已被隐藏，无法回复");
        }
        ForumReply reply = new ForumReply();
        reply.setPostId(postId);
        reply.setAuthorId(principal.getId());
        reply.setContent(request.content().trim());
        reply.setStatus(STATUS_PUBLISHED);
        reply.setDeleted(0);
        reply.setVersion(0);
        replyMapper.insert(reply);
        postMapper.update(null, new LambdaUpdateWrapper<ForumPost>()
                .eq(ForumPost::getId, postId)
                .setSql("reply_count = reply_count + 1"));
        // 关键修复（审计 D1）：必须重新读库再转 DTO。此前直接返回插入用的内存对象，
        // createdAt 仍为 null，前端渲染时 value.replace(...) 抛错，表现为回复后页面卡死。
        return toReplyResponse(getRequiredReply(reply.getId()), principal);
    }

    @Override
    @Transactional
    public ForumReplyResponse updateReply(
            Long replyId,
            UserPrincipal principal,
            ForumReplySaveRequest request
    ) {
        ForumReply reply = getRequiredReply(replyId);
        assertCanEdit(reply.getAuthorId(), principal);
        replyMapper.update(null, new LambdaUpdateWrapper<ForumReply>()
                .eq(ForumReply::getId, replyId)
                .set(ForumReply::getContent, request.content().trim()));
        return toReplyResponse(getRequiredReply(replyId), principal);
    }

    @Override
    @Transactional
    public void deleteReply(Long replyId, UserPrincipal principal) {
        ForumReply reply = getRequiredReply(replyId);
        assertCanDelete(reply.getAuthorId(), principal);
        replyMapper.deleteById(replyId);
        postMapper.update(null, new LambdaUpdateWrapper<ForumPost>()
                .eq(ForumPost::getId, reply.getPostId())
                .setSql("reply_count = GREATEST(reply_count - 1, 0)"));
    }

    private ForumPost getRequiredPost(Long id) {
        ForumPost post = postMapper.selectById(id);
        if (post == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "帖子不存在");
        }
        return post;
    }

    private ForumReply getRequiredReply(Long id) {
        ForumReply reply = replyMapper.selectById(id);
        if (reply == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "回复不存在");
        }
        return reply;
    }

    private ForumCategory getRequiredCategory(Long id) {
        ForumCategory category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "论坛分类不存在");
        }
        return category;
    }

    /**
     * 编辑权限：只有作者本人可以编辑自己的内容。管理员修改他人内容必须走
     * 举报 / 治理流程（带理由与审计），避免无审计的超级编辑权限。
     */
    private void assertCanEdit(Long authorId, UserPrincipal principal) {
        if (!Objects.equals(authorId, principal.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能编辑自己发布的内容");
        }
    }

    /**
     * 删除权限：本人可以删除自己的内容；管理员可以删除任意内容；
     * 教师只能额外删除学生内容，不能删除其他教师或管理员的内容（审计 D2）。
     */
    private void assertCanDelete(Long authorId, UserPrincipal principal) {
        if (Objects.equals(authorId, principal.getId()) || isAdmin(principal)) {
            return;
        }
        if (isTeacher(principal) && loadAuthors(List.of(authorId)).isStudent(authorId)) {
            return;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN, "无权删除该内容");
    }

    private boolean isTeacher(UserPrincipal principal) {
        return principal.getRoles().contains(ROLE_TEACHER);
    }

    private boolean isAdmin(UserPrincipal principal) {
        return principal.getRoles().contains(ROLE_ADMIN);
    }

    private List<ForumPostSummaryResponse> toSummaries(
            List<ForumPost> posts,
            UserPrincipal principal
    ) {
        if (posts.isEmpty()) {
            return List.of();
        }
        Map<Long, ForumCategory> categories = loadCategories(
                posts.stream().map(ForumPost::getCategoryId).toList()
        );
        AuthorContext authors = loadAuthors(
                posts.stream().map(ForumPost::getAuthorId).toList()
        );
        Set<Long> likedPostIds = loadLikedPostIds(
                principal.getId(),
                posts.stream().map(ForumPost::getId).toList()
        );
        Map<Long, List<ForumMediaResponse>> mediaByPost = mediaService.listMediaForPosts(
                posts.stream().map(ForumPost::getId).toList()
        );
        return posts.stream()
                .map(post -> toSummary(
                        post,
                        categories.get(post.getCategoryId()),
                        authors,
                        likedPostIds.contains(post.getId()),
                        mediaByPost.getOrDefault(post.getId(), List.of()),
                        principal
                ))
                .toList();
    }

    private ForumPostSummaryResponse toSummary(
            ForumPost post,
            ForumCategory category,
            AuthorContext authors,
            boolean likedByMe,
            List<ForumMediaResponse> media,
            UserPrincipal principal
    ) {
        return new ForumPostSummaryResponse(
                post.getId(),
                post.getCategoryId(),
                category == null ? null : category.getName(),
                post.getAuthorId(),
                authors.authorName(post.getAuthorId()),
                authors.roleOf(post.getAuthorId()),
                authors.badgesOf(post.getAuthorId()),
                post.getTitle(),
                preview(post.getContent()),
                intValue(post.getViewCount()),
                intValue(post.getLikeCount()),
                intValue(post.getReplyCount()),
                likedByMe,
                canEditContent(post.getAuthorId(), principal),
                canDeleteContent(post.getAuthorId(), principal, authors),
                canReportContent(post.getAuthorId(), principal),
                media,
                post.getCreatedAt()
        );
    }

    private ForumPostDetailResponse toDetail(ForumPost post, UserPrincipal principal) {
        ForumCategory category = categoryMapper.selectById(post.getCategoryId());
        LambdaQueryWrapper<ForumReply> replyWrapper = new LambdaQueryWrapper<ForumReply>()
                .eq(ForumReply::getPostId, post.getId())
                .orderByAsc(ForumReply::getCreatedAt);
        if (!isAdmin(principal)) {
            replyWrapper.eq(ForumReply::getStatus, STATUS_PUBLISHED);
        }
        List<ForumReply> replies = replyMapper.selectList(replyWrapper);
        AuthorContext authors = loadAuthors(
                Stream.concat(
                        Stream.of(post.getAuthorId()),
                        replies.stream().map(ForumReply::getAuthorId)
                ).toList()
        );
        boolean likedByMe = !loadLikedPostIds(principal.getId(), List.of(post.getId())).isEmpty();
        List<ForumReplyResponse> replyResponses = replies.stream()
                .map(reply -> toReplyResponse(reply, authors, principal))
                .toList();
        return new ForumPostDetailResponse(
                post.getId(),
                post.getCategoryId(),
                category == null ? null : category.getName(),
                post.getAuthorId(),
                authors.authorName(post.getAuthorId()),
                authors.roleOf(post.getAuthorId()),
                authors.badgesOf(post.getAuthorId()),
                post.getTitle(),
                post.getContent(),
                intValue(post.getViewCount()),
                intValue(post.getLikeCount()),
                intValue(post.getReplyCount()),
                likedByMe,
                canEditContent(post.getAuthorId(), principal),
                canDeleteContent(post.getAuthorId(), principal, authors),
                canReportContent(post.getAuthorId(), principal),
                mediaService.listPostMedia(post.getId()),
                post.getCreatedAt(),
                post.getUpdatedAt(),
                replyResponses
        );
    }

    private ForumReplyResponse toReplyResponse(ForumReply reply, UserPrincipal principal) {
        return toReplyResponse(reply, loadAuthors(List.of(reply.getAuthorId())), principal);
    }

    private ForumReplyResponse toReplyResponse(
            ForumReply reply,
            AuthorContext authors,
            UserPrincipal principal
    ) {
        return new ForumReplyResponse(
                reply.getId(),
                reply.getPostId(),
                reply.getAuthorId(),
                authors.authorName(reply.getAuthorId()),
                authors.roleOf(reply.getAuthorId()),
                authors.badgesOf(reply.getAuthorId()),
                reply.getContent(),
                canEditContent(reply.getAuthorId(), principal),
                canDeleteContent(reply.getAuthorId(), principal, authors),
                canReportContent(reply.getAuthorId(), principal),
                reply.getCreatedAt()
        );
    }

    private boolean canEditContent(Long authorId, UserPrincipal principal) {
        return Objects.equals(authorId, principal.getId());
    }

    private boolean canDeleteContent(
            Long authorId,
            UserPrincipal principal,
            AuthorContext authors
    ) {
        if (Objects.equals(authorId, principal.getId()) || isAdmin(principal)) {
            return true;
        }
        return isTeacher(principal) && authors.isStudent(authorId);
    }

    private boolean canReportContent(Long authorId, UserPrincipal principal) {
        return !Objects.equals(authorId, principal.getId());
    }

    private Map<Long, ForumCategory> loadCategories(Collection<Long> categoryIds) {
        List<Long> ids = distinctIds(categoryIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        return categoryMapper.selectByIds(ids).stream()
                .collect(Collectors.toMap(ForumCategory::getId, Function.identity()));
    }

    private Set<Long> loadLikedPostIds(Long userId, Collection<Long> postIds) {
        List<Long> ids = distinctIds(postIds);
        if (ids.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(likeMapper.selectLikedPostIds(userId, ids));
    }

    private AuthorContext loadAuthors(Collection<Long> authorIds) {
        List<Long> ids = distinctIds(authorIds);
        if (ids.isEmpty()) {
            return new AuthorContext(Map.of(), Set.of(), Set.of());
        }
        Map<Long, SysUser> users = userMapper.selectByIdsIncludingDeleted(ids).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        Set<Long> teacherIds = new HashSet<>(
                roleMapper.selectUserIdsByRoleCode(ids, ROLE_TEACHER)
        );
        Set<Long> adminIds = new HashSet<>(
                roleMapper.selectUserIdsByRoleCode(ids, ROLE_ADMIN)
        );
        return new AuthorContext(users, teacherIds, adminIds);
    }

    private List<Long> distinctIds(Collection<Long> ids) {
        return ids.stream().filter(Objects::nonNull).distinct().toList();
    }

    private String preview(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String normalized = content.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 120 ? normalized : normalized.substring(0, 120) + "...";
    }

    private int intValue(Integer value) {
        return value == null ? 0 : value;
    }

    /**
     * 一次批量加载作者昵称与角色，避免逐帖子 / 逐回复查询造成 N+1（审计 D14）。
     */
    private record AuthorContext(
            Map<Long, SysUser> users,
            Set<Long> teacherIds,
            Set<Long> adminIds
    ) {

        String authorName(Long userId) {
            SysUser user = users.get(userId);
            return user == null ? "未知用户" : user.getDisplayName();
        }

        String roleOf(Long userId) {
            if (adminIds.contains(userId)) {
                return ROLE_ADMIN;
            }
            if (teacherIds.contains(userId)) {
                return ROLE_TEACHER;
            }
            return ROLE_STUDENT;
        }

        List<String> badgesOf(Long userId) {
            if (adminIds.contains(userId)) {
                return List.of("管理员");
            }
            if (teacherIds.contains(userId)) {
                return List.of("教师");
            }
            return List.of();
        }

        boolean isStudent(Long userId) {
            return !teacherIds.contains(userId) && !adminIds.contains(userId);
        }
    }
}
