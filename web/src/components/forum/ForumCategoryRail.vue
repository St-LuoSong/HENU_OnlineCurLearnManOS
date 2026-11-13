<script setup lang="ts">
import type { ForumCategory } from '@/api/forum'

defineProps<{
  categories: ForumCategory[]
  selectedCategoryId?: number
  total: number
}>()

const emit = defineEmits<{
  (event: 'select', categoryId?: number): void
}>()
</script>

<template>
  <aside class="forum-category-rail">
    <div class="forum-category-head">
      <div class="forum-category-kicker">浏览入口</div>
      <h2 class="forum-category-title">讨论分类</h2>
    </div>

    <nav class="forum-category-list" aria-label="论坛讨论分类">
      <button
        class="forum-category"
        :class="{ active: !selectedCategoryId }"
        :aria-pressed="!selectedCategoryId"
        @click="emit('select', undefined)"
      >
        <span class="forum-category-mark" aria-hidden="true"></span>
        <span class="forum-category-copy">
          <span class="forum-category-name">全部讨论</span>
          <small>{{ total }} 篇讨论</small>
        </span>
      </button>
      <button
        v-for="category in categories"
        :key="category.id"
        class="forum-category"
        :class="{ active: selectedCategoryId === category.id }"
        :aria-pressed="selectedCategoryId === category.id"
        @click="emit('select', category.id)"
      >
        <span class="forum-category-mark" aria-hidden="true"></span>
        <span class="forum-category-copy">
          <span class="forum-category-name">{{ category.name }}</span>
          <small>{{ category.description || '课程讨论入口' }}</small>
        </span>
      </button>
    </nav>

    <section class="forum-rail-section">
      <div class="forum-rail-title">讨论公约</div>
      <p>说清楚你在学什么、已经尝试过什么、卡在哪一步。不要只发一句“有人吗”。</p>
    </section>
  </aside>
</template>

<style scoped>
.forum-category-rail {
  min-width: 0;
  padding: 18px 14px;
  background: var(--white);
  border: 1px solid var(--rule);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
}

.forum-category-head {
  padding: 0 6px 12px;
}

.forum-category-kicker {
  margin-bottom: 3px;
  color: var(--ink-faint);
  font-size: 11px;
  letter-spacing: 0.08em;
}

.forum-category-title {
  margin: 0;
  color: var(--ink);
  font-family: var(--font-serif);
  font-size: 18px;
  line-height: 1.35;
}

.forum-category-list {
  display: grid;
  gap: 3px;
}

.forum-category {
  display: grid;
  width: 100%;
  grid-template-columns: 4px minmax(0, 1fr);
  gap: 10px;
  align-items: stretch;
  padding: 10px 10px;
  color: var(--ink);
  text-align: left;
  background: transparent;
  border: none;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition:
    color 160ms ease,
    background 160ms ease;
}

.forum-category:hover {
  color: var(--primary-deep);
  background: var(--primary-wash);
}

.forum-category:focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: 1px;
}

.forum-category.active {
  color: var(--primary-deep);
  background: var(--primary-soft);
}

.forum-category-mark {
  width: 3px;
  min-height: 30px;
  background: transparent;
  border-radius: var(--radius-pill);
  transition: background 160ms ease;
}

.forum-category.active .forum-category-mark {
  background: var(--primary);
}

.forum-category-copy {
  display: grid;
  min-width: 0;
  gap: 3px;
}

.forum-category-name {
  overflow: hidden;
  font-size: 13px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.forum-category.active .forum-category-name {
  font-weight: 700;
}

.forum-category small {
  display: -webkit-box;
  overflow: hidden;
  color: var(--ink-soft);
  font-size: 12px;
  font-weight: 400;
  line-height: 1.55;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.forum-rail-section {
  margin-top: 18px;
  padding: 16px 6px 0;
  border-top: 1px solid var(--rule);
}

.forum-rail-title {
  color: var(--ink);
  font-family: var(--font-serif);
  font-size: 14px;
  font-weight: 700;
}

.forum-rail-section p {
  margin: 8px 0 0;
  color: var(--ink-soft);
  font-size: 12px;
  line-height: 1.75;
}

@media (max-width: 1180px) {
  .forum-category-rail {
    padding: 16px 12px;
  }
}
</style>
