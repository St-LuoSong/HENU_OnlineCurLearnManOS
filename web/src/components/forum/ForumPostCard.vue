<script setup lang="ts">
import type { ForumPostSummary } from '@/api/forum'
import { formatDateTime as formatDateTimeText } from '@/utils/date'
import MediaGrid from '@/components/media/MediaGrid.vue'

defineProps<{
  post: ForumPostSummary
}>()

const emit = defineEmits<{
  (event: 'open', post: ForumPostSummary): void
  (event: 'edit', post: ForumPostSummary): void
  (event: 'delete', post: ForumPostSummary): void
  (event: 'report', post: ForumPostSummary): void
  (event: 'toggle-like', post: ForumPostSummary): void
  (event: 'preview', payload: { post: ForumPostSummary; index: number }): void
}>()

function formatDateTime(value?: string): string {
  return formatDateTimeText(value, '刚刚')
}
</script>

<template>
  <article class="forum-post-card">
    <div class="forum-post-card-main">
      <div class="forum-post-card-eyebrow">
        <span class="forum-post-category">{{ post.categoryName || '学习讨论' }}</span>
        <span>{{ formatDateTime(post.createdAt) }}</span>
      </div>
      <button class="forum-post-title" @click="emit('open', post)">{{ post.title }}</button>
      <p class="forum-post-preview">{{ post.contentPreview }}</p>
      <MediaGrid
        v-if="post.media && post.media.length > 0"
        :items="post.media"
        @preview="(index) => emit('preview', { post, index })"
      />
      <div class="forum-post-meta">
        <span class="forum-post-author">{{ post.authorName }}</span>
        <el-tag
          v-for="badge in post.authorBadges"
          :key="badge"
          size="small"
          type="warning"
          effect="plain"
        >
          {{ badge }}
        </el-tag>
      </div>
    </div>

    <div class="forum-post-card-side">
      <div class="forum-post-stats">
        <span><strong>{{ post.viewCount }}</strong> 浏览</span>
        <span><strong>{{ post.replyCount }}</strong> 回复</span>
        <span><strong>{{ post.likeCount }}</strong> 点赞</span>
      </div>
      <div class="forum-post-actions">
        <el-button
          link
          :type="post.likedByMe ? 'success' : 'primary'"
          @click="emit('toggle-like', post)"
        >
          {{ post.likedByMe ? '已点赞' : '点赞' }}
        </el-button>
        <el-button link type="primary" @click="emit('open', post)">进入讨论</el-button>
        <el-button v-if="post.canEdit" link @click="emit('edit', post)">编辑</el-button>
        <el-button v-if="post.canDelete" link type="danger" @click="emit('delete', post)">
          删除
        </el-button>
        <el-button v-if="post.canReport" link type="warning" @click="emit('report', post)">
          举报
        </el-button>
      </div>
    </div>
  </article>
</template>

<style scoped>
.forum-post-card {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 142px;
  gap: 20px;
  padding: 20px 6px;
  border-bottom: 1px solid var(--rule);
  transition: background 160ms ease;
}

.forum-post-card::before {
  position: absolute;
  top: 20px;
  bottom: 20px;
  left: -1px;
  width: 2px;
  content: '';
  background: transparent;
  border-radius: var(--radius-pill);
  transition: background 160ms ease;
}

.forum-post-card:hover {
  background: linear-gradient(90deg, var(--primary-wash), transparent 72%);
}

.forum-post-card:hover::before {
  background: var(--primary);
}

.forum-post-card-main {
  min-width: 0;
}

.forum-post-card-eyebrow {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
  color: var(--ink-faint);
  font-size: 11px;
}

.forum-post-category {
  padding: 2px 7px;
  color: var(--primary-deep);
  font-weight: 600;
  background: var(--primary-soft);
  border-radius: var(--radius-pill);
}

.forum-post-title {
  padding: 0;
  color: var(--ink);
  font-family: var(--font-serif);
  font-size: 19px;
  font-weight: 700;
  line-height: 1.45;
  text-align: left;
  background: transparent;
  border: none;
  cursor: pointer;
  overflow-wrap: anywhere;
}

.forum-post-title:hover {
  color: var(--primary-deep);
}

.forum-post-title:focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: 3px;
  border-radius: 2px;
}

.forum-post-preview {
  margin: 8px 0 10px;
  color: var(--ink-soft);
  font-size: 13px;
  line-height: 1.8;
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.forum-post-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 10px;
  align-items: center;
  color: var(--ink-soft);
  font-size: 12px;
}

.forum-post-author {
  color: var(--ink);
  font-weight: 600;
}

.forum-post-card-side {
  display: flex;
  min-width: 0;
  flex-direction: column;
  align-items: flex-end;
  gap: 12px;
}

.forum-post-stats {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 4px 10px;
  color: var(--ink-soft);
  font-size: 11px;
  text-align: right;
}

.forum-post-stats strong {
  color: var(--ink);
  font-family: var(--font-numeric);
  font-size: 13px;
  font-variant-numeric: tabular-nums;
}

.forum-post-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 4px 8px;
}

@media (max-width: 760px) {
  .forum-post-card {
    grid-template-columns: 1fr;
    gap: 12px;
    padding: 18px 2px;
  }

  .forum-post-card-side {
    align-items: flex-start;
  }

  .forum-post-stats,
  .forum-post-actions {
    text-align: left;
    justify-content: flex-start;
  }

  .forum-post-title {
    font-size: 18px;
  }
}
</style>
