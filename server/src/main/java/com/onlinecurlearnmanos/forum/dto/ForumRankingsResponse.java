package com.onlinecurlearnmanos.forum.dto;

import java.util.List;

/**
 * 论坛右侧排行榜：同一时间窗口下按浏览量 / 点赞量 / 评论量各取前若干条，
 * 口径由 {@code window}（ALL / WEEK / MONTH）明确，避免与分类栏重复。
 */
public record ForumRankingsResponse(
        String window,
        List<ForumPostSummaryResponse> mostViewed,
        List<ForumPostSummaryResponse> mostLiked,
        List<ForumPostSummaryResponse> mostReplied
) {
}

