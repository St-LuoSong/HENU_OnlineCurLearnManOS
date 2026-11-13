package com.onlinecurlearnmanos.forum.service;

import com.onlinecurlearnmanos.common.api.PageResult;
import com.onlinecurlearnmanos.forum.dto.ForumPostSummaryResponse;
import com.onlinecurlearnmanos.forum.entity.ForumPost;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

class ForumPaginationTest extends ForumServiceTestSupport {

    @Test
    void keepsRequestedPageAndFixedSizeAndReturnsTotal() {
        stubPage(25);

        PageResult<ForumPostSummaryResponse> result = forumService.listPosts(
                2,
                10,
                null,
                null,
                principal(STUDENT_ID, "STUDENT")
        );

        assertThat(result.total()).isEqualTo(25);
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.size()).isEqualTo(10);
        assertThat(result.records()).isEmpty();
        ArgumentCaptor<Page<ForumPost>> captor = pageCaptor();
        assertThat(captor.getValue().getCurrent()).isEqualTo(2);
        assertThat(captor.getValue().getSize()).isEqualTo(10);
    }

    @Test
    void clampsInvalidPageAndOverlargeSize() {
        stubPage(25);

        PageResult<ForumPostSummaryResponse> result = forumService.listPosts(
                0,
                100,
                null,
                null,
                principal(STUDENT_ID, "STUDENT")
        );

        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(50);
        ArgumentCaptor<Page<ForumPost>> captor = pageCaptor();
        assertThat(captor.getValue().getCurrent()).isEqualTo(1);
        assertThat(captor.getValue().getSize()).isEqualTo(50);
    }

    private void stubPage(long total) {
        given(postMapper.selectPage(any(), any())).willAnswer(invocation -> {
            Page<ForumPost> requested = invocation.getArgument(0);
            requested.setRecords(List.of());
            requested.setTotal(total);
            return requested;
        });
    }

    private ArgumentCaptor<Page<ForumPost>> pageCaptor() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Page<ForumPost>> captor = ArgumentCaptor.forClass(Page.class);
        verify(postMapper).selectPage(captor.capture(), any());
        return captor;
    }
}
