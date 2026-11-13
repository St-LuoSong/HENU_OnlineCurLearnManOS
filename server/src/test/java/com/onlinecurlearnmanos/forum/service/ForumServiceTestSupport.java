package com.onlinecurlearnmanos.forum.service;

import com.onlinecurlearnmanos.forum.dto.ForumReplySaveRequest;
import com.onlinecurlearnmanos.forum.entity.ForumPost;
import com.onlinecurlearnmanos.forum.entity.ForumReply;
import com.onlinecurlearnmanos.forum.mapper.ForumCategoryMapper;
import com.onlinecurlearnmanos.forum.mapper.ForumLikeMapper;
import com.onlinecurlearnmanos.forum.mapper.ForumPostMapper;
import com.onlinecurlearnmanos.forum.mapper.ForumReplyMapper;
import com.onlinecurlearnmanos.security.UserPrincipal;
import com.onlinecurlearnmanos.support.MybatisLambdaCache;
import com.onlinecurlearnmanos.user.entity.SysUser;
import com.onlinecurlearnmanos.user.mapper.SysRoleMapper;
import com.onlinecurlearnmanos.user.mapper.SysUserMapper;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.BeforeAll;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 论坛服务的单元测试装配：只装配 Mock 依赖，不启动 Spring、不连数据库。
 */
@ExtendWith(MockitoExtension.class)
abstract class ForumServiceTestSupport {

    protected static final Long CATEGORY_ID = 41L;
    protected static final Long POST_ID = 11L;
    protected static final Long REPLY_ID = 21L;
    protected static final Long STUDENT_ID = 31L;
    protected static final Long TEACHER_ID = 32L;
    protected static final Long OTHER_TEACHER_ID = 33L;
    protected static final Long ADMIN_ID = 34L;

    @Mock
    protected ForumCategoryMapper categoryMapper;

    @Mock
    protected ForumPostMapper postMapper;

    @Mock
    protected ForumReplyMapper replyMapper;

    @Mock
    protected ForumLikeMapper likeMapper;

    @Mock
    protected ForumMediaService mediaService;

    @Mock
    protected SysUserMapper userMapper;

    @Mock
    protected SysRoleMapper roleMapper;

    @InjectMocks
    protected ForumServiceImpl forumService;

    @BeforeAll
    static void registerLambdaCache() {
        MybatisLambdaCache.register(ForumPost.class, ForumReply.class);
    }

    protected UserPrincipal principal(Long id, String... roles) {
        return new UserPrincipal(id, "user-" + id, "n/a", "用户" + id, null, 1, List.of(roles));
    }

    protected ForumPost post(Long authorId) {
        ForumPost post = new ForumPost();
        post.setId(POST_ID);
        post.setCategoryId(CATEGORY_ID);
        post.setAuthorId(authorId);
        post.setTitle("极限怎么理解");
        post.setContent("我对极限的定义有点模糊，想听听大家的理解。");
        post.setViewCount(3);
        post.setLikeCount(1);
        post.setReplyCount(1);
        post.setStatus("PUBLISHED");
        post.setCreatedAt(LocalDateTime.of(2026, 9, 20, 10, 0));
        post.setUpdatedAt(LocalDateTime.of(2026, 9, 20, 10, 0));
        return post;
    }

    protected ForumReply reply(Long authorId) {
        ForumReply reply = new ForumReply();
        reply.setId(REPLY_ID);
        reply.setPostId(POST_ID);
        reply.setAuthorId(authorId);
        reply.setContent("建议先看数列极限的直观定义。");
        reply.setStatus("PUBLISHED");
        reply.setCreatedAt(LocalDateTime.of(2026, 9, 20, 10, 30));
        return reply;
    }

    protected ForumReplySaveRequest replyRequest(String content) {
        return new ForumReplySaveRequest(content);
    }

    protected SysUser user(Long id, String displayName) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername("user-" + id);
        user.setDisplayName(displayName);
        return user;
    }

    protected List<String> rolesOf(String... roles) {
        return Arrays.asList(roles);
    }
}
