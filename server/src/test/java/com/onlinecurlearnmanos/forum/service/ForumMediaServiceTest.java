package com.onlinecurlearnmanos.forum.service;

import com.onlinecurlearnmanos.common.exception.BusinessException;
import com.onlinecurlearnmanos.file.entity.FileAsset;
import com.onlinecurlearnmanos.file.mapper.FileAssetMapper;
import com.onlinecurlearnmanos.file.service.FileAccessGuard;
import com.onlinecurlearnmanos.forum.dto.ForumMediaResponse;
import com.onlinecurlearnmanos.forum.entity.ForumPostMedia;
import com.onlinecurlearnmanos.forum.mapper.ForumPostMediaMapper;
import com.onlinecurlearnmanos.security.UserPrincipal;
import com.onlinecurlearnmanos.support.MybatisLambdaCache;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 帖子图片的强校验：数量上限、用途白名单、归属校验、顺序与批量读取。
 * 这些规则必须在服务端成立，前端隐藏按钮不算权限。
 */
class ForumMediaServiceTest {

    private static final Long POST_ID = 11L;
    private static final Long OTHER_POST_ID = 12L;
    private static final Long OWNER_ID = 31L;
    private static final Long OTHER_ID = 32L;

    private final ForumPostMediaMapper mediaMapper = mock(ForumPostMediaMapper.class);
    private final FileAssetMapper fileAssetMapper = mock(FileAssetMapper.class);
    private final FileAccessGuard accessGuard = mock(FileAccessGuard.class);
    private final ForumMediaService service =
            new ForumMediaService(mediaMapper, fileAssetMapper, accessGuard);

    private final UserPrincipal owner =
            new UserPrincipal(OWNER_ID, "2024001", "n/a", "张三", null, 1, List.of("STUDENT"));

    @BeforeAll
    static void registerLambdaCache() {
        MybatisLambdaCache.register(ForumPostMedia.class);
    }

    @Test
    void rejectsMoreThanNineImages() {
        List<Long> fileIds = new ArrayList<>();
        for (long id = 1; id <= 10; id++) {
            fileIds.add(id);
        }

        assertThatThrownBy(() -> service.replaceMedia(POST_ID, fileIds, owner))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("最多上传 9 张图片");
        verify(mediaMapper, never()).insert(any(ForumPostMedia.class));
    }

    @Test
    void rejectsDuplicatedImages() {
        assertThatThrownBy(() -> service.replaceMedia(POST_ID, List.of(3L, 3L), owner))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能重复添加");
    }

    @Test
    void rejectsFilesThatAreNotForumImagePurpose() {
        given(fileAssetMapper.selectById(3L))
                .willReturn(asset(3L, OWNER_ID, "CLASS_RESOURCE", "png"));

        assertThatThrownBy(() -> service.replaceMedia(POST_ID, List.of(3L), owner))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("论坛图片用途");
    }

    @Test
    void rejectsImageOwnedByAnotherUser() {
        given(fileAssetMapper.selectById(3L)).willReturn(asset(3L, OTHER_ID, "FORUM_IMAGE", "png"));

        assertThatThrownBy(() -> service.replaceMedia(POST_ID, List.of(3L), owner))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能关联自己上传的图片");
    }

    @Test
    void rejectsDeletedImage() {
        FileAsset deleted = asset(3L, OWNER_ID, "FORUM_IMAGE", "png");
        deleted.setStatus(FileAsset.STATUS_DELETED);
        given(fileAssetMapper.selectById(3L)).willReturn(deleted);

        assertThatThrownBy(() -> service.replaceMedia(POST_ID, List.of(3L), owner))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("图片不存在");
    }

    @Test
    void keepsSubmittedOrderAsSortOrder() {
        given(fileAssetMapper.selectById(anyLong())).willAnswer(invocation ->
                asset(invocation.getArgument(0), OWNER_ID, "FORUM_IMAGE", "png"));
        given(accessGuard.isOwner(any(), any())).willReturn(true);

        service.replaceMedia(POST_ID, List.of(7L, 5L), owner);

        ArgumentCaptor<ForumPostMedia> captor = ArgumentCaptor.forClass(ForumPostMedia.class);
        verify(mediaMapper, times(2)).insert(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(ForumPostMedia::getFileId)
                .containsExactly(7L, 5L);
        assertThat(captor.getAllValues())
                .extracting(ForumPostMedia::getSortOrder)
                .containsExactly(0, 1);
        assertThat(captor.getAllValues())
                .extracting(ForumPostMedia::getMediaType)
                .containsOnly(ForumPostMedia.TYPE_IMAGE);
    }

    @Test
    void emptyListClearsAllMedia() {
        service.replaceMedia(POST_ID, List.of(), owner);

        verify(mediaMapper).delete(any());
        verify(mediaMapper, never()).insert(any(ForumPostMedia.class));
    }

    @Test
    void detachAllRemovesEveryRelationOfThePost() {
        service.detachAll(POST_ID);

        verify(mediaMapper).delete(any());
    }

    @Test
    void groupsMediaByPostAndSkipsMissingFiles() {
        given(mediaMapper.selectList(any())).willReturn(List.of(
                media(1L, POST_ID, 7L, 0),
                media(2L, POST_ID, 8L, 1),
                media(3L, OTHER_POST_ID, 9L, 0)
        ));
        given(fileAssetMapper.selectByIds(any())).willReturn(List.of(
                asset(7L, OWNER_ID, "FORUM_IMAGE", "png"),
                asset(8L, OWNER_ID, "FORUM_IMAGE", "jpg")
        ));

        Map<Long, List<ForumMediaResponse>> grouped =
                service.listMediaForPosts(List.of(POST_ID, OTHER_POST_ID));

        assertThat(grouped.get(POST_ID)).hasSize(2);
        // 9 号文件记录已丢失：关系行存在但文件读不到，必须整条跳过，而不是返回空壳媒体。
        assertThat(grouped).doesNotContainKey(OTHER_POST_ID);
    }

    @Test
    void singlePostLookupReturnsEmptyListWhenNothingAttached() {
        assertThat(service.listPostMedia(POST_ID)).isEmpty();
    }

    private ForumPostMedia media(Long id, Long postId, Long fileId, int sortOrder) {
        ForumPostMedia media = new ForumPostMedia();
        media.setId(id);
        media.setPostId(postId);
        media.setFileId(fileId);
        media.setMediaType(ForumPostMedia.TYPE_IMAGE);
        media.setSortOrder(sortOrder);
        return media;
    }

    private FileAsset asset(Long id, Long ownerId, String purpose, String extension) {
        FileAsset asset = new FileAsset();
        asset.setId(id);
        asset.setOwnerId(ownerId);
        asset.setPurpose(purpose);
        asset.setExtension(extension);
        asset.setMimeType("image/" + extension);
        asset.setOriginalName("photo." + extension);
        asset.setSizeBytes(2048L);
        asset.setStatus(FileAsset.STATUS_ACTIVE);
        return asset;
    }
}

