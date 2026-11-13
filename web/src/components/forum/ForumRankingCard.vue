<script setup lang="ts">
import { computed } from 'vue'

import type {
  ForumPostSummary,
  ForumRankingMetric,
  ForumRankings,
  ForumRankingWindow
} from '@/api/forum'

const props = defineProps<{
  rankings: ForumRankings | null
  loading: boolean
  rankingWindow: ForumRankingWindow
  rankingMetric: ForumRankingMetric
}>()

const emit = defineEmits<{
  (event: 'update:rankingWindow', value: ForumRankingWindow): void
  (event: 'update:rankingMetric', value: ForumRankingMetric): void
  (event: 'open', post: ForumPostSummary): void
}>()

const rankingWindows: Array<{ value: ForumRankingWindow; label: string }> = [
  { value: 'ALL', label: '全部' },
  { value: 'MONTH', label: '近 30 天' },
  { value: 'WEEK', label: '近 7 天' }
]

const rankingMetrics: Array<{ value: ForumRankingMetric; label: string }> = [
  { value: 'view', label: '浏览' },
  { value: 'like', label: '点赞' },
  { value: 'reply', label: '评论' }
]

const rankingRows = computed<ForumPostSummary[]>(() => {
  const source = props.rankings
  if (!source) {
    return []
  }
  if (props.rankingMetric === 'like') {
    return source.mostLiked
  }
  if (props.rankingMetric === 'reply') {
    return source.mostReplied
  }
  return source.mostViewed
})

function rankingValue(post: ForumPostSummary): string {
  if (props.rankingMetric === 'like') {
    return `${post.likeCount} 赞`
  }
  if (props.rankingMetric === 'reply') {
    return `${post.replyCount} 回复`
  }
  return `${post.viewCount} 浏览`
}
</script>

<template>
  <section class="forum-side-card forum-ranking-card" aria-labelledby="forum-ranking-title">
    <div class="forum-side-head">
      <div>
        <div class="forum-side-eyebrow">讨论热度</div>
        <h2 id="forum-ranking-title" class="forum-side-title">讨论排行</h2>
      </div>
      <span class="forum-side-note">前 5 名</span>
    </div>

    <div class="forum-rank-switch" role="tablist" aria-label="排行时间窗口">
      <button
        v-for="option in rankingWindows"
        :key="option.value"
        type="button"
        role="tab"
        :aria-selected="rankingWindow === option.value"
        :class="{ 'is-active': rankingWindow === option.value }"
        @click="emit('update:rankingWindow', option.value)"
      >
        {{ option.label }}
      </button>
    </div>

    <div class="forum-rank-switch forum-rank-switch--metric" role="tablist" aria-label="排行指标">
      <button
        v-for="option in rankingMetrics"
        :key="option.value"
        type="button"
        role="tab"
        :aria-selected="rankingMetric === option.value"
        :class="{ 'is-active': rankingMetric === option.value }"
        @click="emit('update:rankingMetric', option.value)"
      >
        {{ option.label }}
      </button>
    </div>

    <div v-loading="loading" class="forum-rank-body">
      <div v-if="!loading && rankingRows.length === 0" class="forum-side-empty">
        这个时间窗口还没有可排行的帖子。
      </div>
      <ol v-else class="forum-rank-list">
        <li
          v-for="(item, index) in rankingRows"
          :key="item.id"
          class="forum-rank-row"
          :class="{ 'is-top': index === 0 }"
        >
          <span class="forum-rank-no">{{ index + 1 }}</span>
          <button
            type="button"
            class="forum-rank-title"
            :title="item.title"
            @click="emit('open', item)"
          >
            {{ item.title }}
          </button>
          <span class="forum-rank-value">{{ rankingValue(item) }}</span>
        </li>
      </ol>
    </div>
  </section>
</template>

<style scoped>
.forum-ranking-card {
  min-width: 0;
  padding: 2px 0 18px;
  margin-bottom: 18px;
  border-bottom: 1px solid var(--rule);
}

.forum-side-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.forum-side-eyebrow {
  margin-bottom: 3px;
  color: var(--ink-faint);
  font-size: 11px;
  letter-spacing: 0.08em;
}

.forum-side-title {
  margin: 0;
  color: var(--ink);
  font-family: var(--font-serif);
  font-size: 18px;
  font-weight: 700;
  line-height: 1.35;
}

.forum-side-note {
  flex: 0 0 auto;
  padding-top: 16px;
  color: var(--ink-soft);
  font-size: 12px;
}

.forum-side-empty {
  margin: 10px 0 0;
  color: var(--ink-soft);
  font-size: 13px;
  line-height: 1.8;
}

.forum-rank-switch {
  display: flex;
  gap: 2px;
  padding: 3px;
  margin-top: 14px;
  background: var(--paper-deep);
  border-radius: var(--radius-md);
}

.forum-rank-switch--metric {
  padding: 0;
  margin-top: 8px;
  background: transparent;
}

.forum-rank-switch button {
  flex: 1 1 0;
  min-width: 0;
  padding: 6px 4px;
  color: var(--ink-soft);
  font-size: 12px;
  background: transparent;
  border: none;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition:
    color 160ms ease,
    background 160ms ease,
    box-shadow 160ms ease;
}

.forum-rank-switch button:hover {
  color: var(--primary-deep);
}

.forum-rank-switch button:focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: 2px;
}

.forum-rank-switch button.is-active {
  color: var(--ink);
  font-weight: 700;
  background: var(--white);
  box-shadow: 0 2px 8px rgba(35, 72, 128, 0.08);
}

.forum-rank-switch--metric button {
  padding: 4px 2px 8px;
  border-bottom: 2px solid transparent;
  border-radius: 0;
}

.forum-rank-switch--metric button.is-active {
  background: transparent;
  border-bottom-color: var(--primary);
  box-shadow: none;
}

.forum-rank-body {
  min-height: 72px;
  margin-top: 12px;
}

.forum-rank-list {
  padding: 0;
  margin: 0;
  list-style: none;
}

.forum-rank-row {
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr) 78px;
  gap: 8px;
  align-items: center;
  min-height: 38px;
  padding: 0 6px;
  border-radius: var(--radius-sm);
  transition: background 160ms ease;
}

.forum-rank-row:hover {
  background: var(--primary-wash);
}

.forum-rank-row.is-top .forum-rank-no {
  color: var(--ochre);
  font-weight: 700;
}

.forum-rank-no {
  color: var(--ink-faint);
  font-family: var(--font-numeric);
  font-size: 13px;
  font-variant-numeric: tabular-nums;
  text-align: center;
}

.forum-rank-title {
  min-width: 0;
  padding: 0;
  overflow: hidden;
  color: var(--ink);
  font-size: 13px;
  line-height: 1.6;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
  background: transparent;
  border: none;
  cursor: pointer;
}

.forum-rank-title:hover {
  color: var(--primary-deep);
}

.forum-rank-title:focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: 2px;
  border-radius: 2px;
}

.forum-rank-value {
  justify-self: end;
  color: var(--ink-soft);
  font-family: var(--font-numeric);
  font-size: 11px;
  font-variant-numeric: tabular-nums;
  text-align: right;
  white-space: nowrap;
}

@media (max-width: 760px) {
  .forum-rank-row {
    grid-template-columns: 24px minmax(0, 1fr) 76px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .forum-rank-row,
  .forum-rank-switch button {
    transition: none;
  }
}
</style>
