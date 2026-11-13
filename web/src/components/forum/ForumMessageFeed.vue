<script setup lang="ts">
import type { ForumMessage } from '@/api/forum'
import { formatDateTime as formatDateTimeText } from '@/utils/date'

defineProps<{
  messages: ForumMessage[]
  loading: boolean
  unreadCount: number
}>()

const emit = defineEmits<{
  (event: 'open', message: ForumMessage): void
  (event: 'acknowledge', message: ForumMessage): void
}>()

function messageTypeLabel(type: ForumMessage['type']): string {
  return type === 'ANNOUNCEMENT' ? '教师提醒' : '帖子回复'
}

function formatDateTime(value?: string | null): string {
  return formatDateTimeText(value, '刚刚')
}

function openMessage(message: ForumMessage): void {
  if (message.postId) {
    emit('open', message)
  }
}
</script>

<template>
  <section class="forum-side-card forum-message-card" aria-labelledby="forum-message-title">
    <div class="forum-side-head">
      <div>
        <div class="forum-side-eyebrow">与你有关</div>
        <h2 id="forum-message-title" class="forum-side-title">消息动态</h2>
      </div>
      <span
        v-if="unreadCount > 0"
        class="forum-message-unread"
        role="status"
        aria-live="polite"
        :aria-label="`${unreadCount} 条未了解消息`"
      >
        {{ unreadCount > 99 ? '99+' : unreadCount }}
      </span>
    </div>

    <div v-loading="loading" class="forum-message-body">
      <div v-if="!loading && messages.length === 0" class="forum-message-empty">
        有同学回复你的帖子，或老师发布提醒后，会出现在这里。
      </div>

      <TransitionGroup v-else name="forum-message" tag="div" class="forum-message-list">
        <article
          v-for="message in messages"
          :key="`${message.type}-${message.sourceId}`"
          class="forum-message-item"
          :class="{
            'is-unread': !message.acknowledged,
            'is-clickable': Boolean(message.postId)
          }"
          :role="message.postId ? 'button' : undefined"
          :tabindex="message.postId ? 0 : undefined"
          @click="openMessage(message)"
          @keydown.enter="openMessage(message)"
          @keydown.space.prevent="openMessage(message)"
        >
          <div class="forum-message-item-head">
            <span class="forum-message-type" :class="`is-${message.type.toLowerCase()}`">
              {{ messageTypeLabel(message.type) }}
            </span>
            <span class="forum-message-time">{{ formatDateTime(message.createdAt) }}</span>
          </div>
          <h3 class="forum-message-title" :title="message.title">{{ message.title }}</h3>
          <p class="forum-message-content">{{ message.content }}</p>
          <div class="forum-message-foot">
            <span class="forum-message-author">{{ message.authorName }}</span>
            <el-button
              v-if="message.type === 'ANNOUNCEMENT' && !message.acknowledged"
              link
              type="primary"
              size="small"
              @click.stop="emit('acknowledge', message)"
            >
              已了解
            </el-button>
            <span v-else-if="message.acknowledged" class="forum-message-done">
              {{ message.type === 'ANNOUNCEMENT' ? '已了解' : '已读' }}
            </span>
            <span v-else class="forum-message-action">查看帖子</span>
          </div>
        </article>
      </TransitionGroup>
    </div>
  </section>
</template>

<style scoped>
.forum-message-card {
  min-width: 0;
  padding: 0 0 18px;
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

.forum-message-unread {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 22px;
  height: 22px;
  padding: 0 6px;
  color: var(--white);
  font-family: var(--font-numeric);
  font-size: 11px;
  background: var(--cinnabar);
  border-radius: var(--radius-pill);
}

.forum-message-body {
  min-height: 56px;
  margin-top: 12px;
}

.forum-message-empty {
  padding: 10px 0 2px;
  color: var(--ink-soft);
  font-size: 13px;
  line-height: 1.8;
}

.forum-message-list {
  display: grid;
  gap: 8px;
}

.forum-message-item {
  position: relative;
  padding: 11px 10px 10px 12px;
  background: var(--primary-wash);
  border: 1px solid transparent;
  border-radius: var(--radius-md);
  transition:
    background 160ms ease,
    border-color 160ms ease,
    transform 160ms ease;
}

.forum-message-item.is-unread {
  background: var(--white);
  border-color: var(--rule);
}

.forum-message-item.is-unread::before {
  position: absolute;
  top: 14px;
  left: 0;
  width: 3px;
  height: 24px;
  content: '';
  background: var(--primary);
  border-radius: 0 3px 3px 0;
}

.forum-message-item.is-clickable {
  cursor: pointer;
}

.forum-message-item.is-clickable:hover {
  border-color: var(--rule-strong);
  transform: translateY(-1px);
}

.forum-message-item:focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: 2px;
}

.forum-message-item-head,
.forum-message-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.forum-message-type {
  color: var(--primary-deep);
  font-size: 11px;
  font-weight: 700;
}

.forum-message-type.is-announcement {
  color: var(--ochre);
}

.forum-message-time {
  color: var(--ink-faint);
  font-size: 11px;
}

.forum-message-title {
  display: -webkit-box;
  margin: 7px 0 4px;
  overflow: hidden;
  color: var(--ink);
  font-family: var(--font-display);
  font-size: 13px;
  line-height: 1.55;
  overflow-wrap: anywhere;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.forum-message-content {
  display: -webkit-box;
  margin: 0;
  overflow: hidden;
  color: var(--ink-soft);
  font-size: 12px;
  line-height: 1.7;
  overflow-wrap: anywhere;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.forum-message-foot {
  justify-content: flex-start;
  margin-top: 7px;
}

.forum-message-author {
  overflow: hidden;
  color: var(--ink-faint);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.forum-message-foot :deep(.el-button) {
  margin-left: auto;
  padding: 0;
}

.forum-message-done,
.forum-message-action {
  margin-left: auto;
  color: var(--jade);
  font-size: 11px;
}

.forum-message-action {
  color: var(--primary-deep);
}

.forum-message-enter-active,
.forum-message-leave-active {
  transition: opacity 180ms ease, transform 180ms ease;
}

.forum-message-enter-from,
.forum-message-leave-to {
  opacity: 0;
  transform: translateY(4px);
}

@media (prefers-reduced-motion: reduce) {
  .forum-message-item,
  .forum-message-enter-active,
  .forum-message-leave-active {
    transition: none;
  }
}
</style>
