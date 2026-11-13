<script setup lang="ts">
import { Delete, DocumentCopy, Star, StarFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { nextTick, ref, watch } from 'vue'

import type { AssistantMessage, AssistantSession } from '@/api/assistant'
import MarkdownContent from '@/components/markdown/MarkdownContent.vue'
import { formatMonthDayTime } from '@/utils/date'

const STATE_LABELS: Record<string, string> = {
  LEARNING: '学习中',
  ASSESSING: '答题中',
  TRANSFER_TEST: '迁移测试',
  MASTERED: '已掌握',
  REMEDIATION: '补救教学'
}

const MESSAGE_TYPE_LABELS: Record<string, string> = {
  WELCOME: '开始',
  TEACHING: '讲解',
  QUESTION: '出题',
  TRANSFER: '迁移测试',
  ANSWER: '作答',
  HINT: '分层提示',
  RESULT: '评估结果',
  REMEDIATION: '补救教学'
}

const props = defineProps<{
  session: AssistantSession | null
  messages: AssistantMessage[]
  loading?: boolean
  favoritedIds?: number[]
}>()

const emit = defineEmits<{
  (event: 'remove', session: AssistantSession): void
  (event: 'retry', message: AssistantMessage): void
  (event: 'favorite', message: AssistantMessage): void
}>()

const messagePane = ref<HTMLElement | null>(null)
let stickToBottom = true

watch(
  () => [
    props.session?.id,
    props.messages.length,
    props.messages[props.messages.length - 1]?.content,
    props.messages[props.messages.length - 1]?.stopped,
    props.messages[props.messages.length - 1]?.streamPhase
  ],
  () => {
    if (stickToBottom) {
      void scrollToBottom()
    }
  }
)

function handleScroll(): void {
  const pane = messagePane.value
  if (!pane) {
    return
  }
  stickToBottom = pane.scrollHeight - pane.scrollTop - pane.clientHeight < 80
}

async function scrollToBottom(force = false): Promise<void> {
  await nextTick()
  if (messagePane.value) {
    if (force) {
      stickToBottom = true
    }
    if (!stickToBottom) {
      return
    }
    messagePane.value.scrollTop = messagePane.value.scrollHeight
  }
}

function modeLabel(session: AssistantSession): string {
  return session.sessionMode === 'practice' ? '练习引擎' : '问答引擎'
}

function stateLabel(session: AssistantSession): string {
  if (session.sessionMode === 'practice') {
    const state = STATE_LABELS[session.state ?? ''] ?? session.state ?? '未开始'
    return session.currentGrade ? `${state} · 等级 ${session.currentGrade}` : state
  }
  return session.state === 'ACTIVE' ? '进行中' : session.state ?? ''
}

function roleLabel(message: AssistantMessage): string {
  return message.role === 'user' ? '我' : 'AI 导师'
}

function messageTypeLabel(type?: string): string {
  return type ? MESSAGE_TYPE_LABELS[type] ?? type : ''
}

function formatDateTime(value?: string): string {
  return formatMonthDayTime(value, '')
}

async function copy(message: AssistantMessage): Promise<void> {
  if (!message.content) {
    return
  }
  try {
    await navigator.clipboard.writeText(message.content)
    ElMessage.success('已复制')
  } catch {
    ElMessage.error('复制失败，请手动选择内容')
  }
}

defineExpose({ scrollToBottom })
</script>

<template>
  <section v-if="session" v-loading="loading" class="assistant-conversation">
    <div class="assistant-conversation-header">
      <div>
        <div class="assistant-conversation-title-row">
          <h2>{{ session.title }}</h2>
          <el-tag size="small" :type="session.sessionMode === 'practice' ? 'warning' : 'success'">
            {{ modeLabel(session) }}
          </el-tag>
        </div>
        <div class="task-meta">
          {{ session.subjectName ?? '通用' }}
          <template v-if="session.topic"> · 主题 {{ session.topic }}</template>
          <template v-if="session.knowledgePointName"> · {{ session.knowledgePointName }}</template>
          · {{ stateLabel(session) }}
        </div>
      </div>
      <el-button link type="danger" :icon="Delete" @click="emit('remove', session)">
        删除会话
      </el-button>
    </div>

    <div ref="messagePane" class="chat-messages assistant-messages" @scroll="handleScroll">
      <div v-if="messages.length === 0" class="chat-empty">
        {{ session.sessionMode === 'practice'
          ? '点击下方“出题练习”开始一轮评估。'
          : '从一个具体问题开始，或使用下方快捷动作。' }}
      </div>
      <div
        v-for="message in messages"
        :key="message.id"
        class="chat-message"
        :class="message.role"
      >
        <div class="chat-message-meta">
          {{ roleLabel(message) }}
          <template v-if="message.messageType">
            · {{ messageTypeLabel(message.messageType) }}
          </template>
          <template v-if="message.model"> · 模型 {{ message.model }}</template>
          · {{ formatDateTime(message.createdAt) }}
        </div>
        <details v-if="message.reasoning" class="chat-reasoning">
          <summary>推理过程</summary>
          <div class="chat-reasoning-body">
            <MarkdownContent :content="message.reasoning" />
          </div>
        </details>
        <div class="chat-bubble">
          <MarkdownContent
            v-if="message.content"
            :content="message.content"
            :streaming="message.streamPhase === 'streaming'"
          />
          <span v-else-if="message.stopped" class="chat-pending">已停止生成</span>
          <span v-else-if="message.failed">生成失败，可重试</span>
          <span v-else-if="message.streamPhase === 'connecting'" class="chat-pending">
            正在建立实时连接…
          </span>
          <span v-else-if="message.streamPhase === 'streaming'" class="chat-pending">
            实时生成中…
            <i class="chat-caret" />
          </span>
          <span v-else class="chat-pending">正在整理回答…</span>
        </div>
        <div class="assistant-message-actions">
          <span v-if="message.stopped && message.content" class="chat-stopped-note">
            已停止生成
          </span>
          <el-button
            v-if="message.role === 'assistant' && message.id > 0 && message.content"
            link
            size="small"
            :icon="favoritedIds?.includes(message.id) ? StarFilled : Star"
            :type="favoritedIds?.includes(message.id) ? 'warning' : undefined"
            @click="emit('favorite', message)"
          >
            {{ favoritedIds?.includes(message.id) ? '已收藏' : '收藏' }}
          </el-button>
          <el-button
            v-if="message.content"
            link
            size="small"
            :icon="DocumentCopy"
            @click="copy(message)"
          >
            复制
          </el-button>
          <el-button
            v-if="message.failed || message.stopped"
            link
            type="primary"
            size="small"
            @click="emit('retry', message)"
          >
            {{ message.stopped ? '重新生成' : '重试' }}
          </el-button>
        </div>
      </div>
    </div>
  </section>

  <div v-else class="chat-empty chat-empty-large">
    选择左侧会话，或新建问答 / 练习开始。
  </div>
</template>

<style scoped>
.assistant-conversation {
  display: flex;
  flex: 1 1 auto;
  min-width: 0;
  min-height: 0;
  flex-direction: column;
  padding-left: 22px;
}

.assistant-conversation-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 0;
  border-bottom: 1px solid var(--rule);
}

.assistant-conversation-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.assistant-conversation-title-row h2 {
  margin: 0;
  font-family: var(--font-display);
  font-size: 22px;
}

.assistant-messages {
  min-height: 0;
}

.assistant-message-actions {
  display: flex;
  min-height: 22px;
  align-items: center;
  gap: 6px;
}

.chat-message {
  animation: chat-message-in 0.18s ease-out;
}

@keyframes chat-message-in {
  from {
    opacity: 0;
    transform: translateY(4px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (prefers-reduced-motion: reduce) {
  .chat-message {
    animation: none;
  }
}

.chat-reasoning {
  margin-bottom: 8px;
  color: var(--ink-soft);
  font-size: 13px;
}

.chat-reasoning summary {
  cursor: pointer;
}

.chat-reasoning-body {
  margin-top: 8px;
  padding: 10px 12px;
  line-height: 1.75;
  white-space: pre-wrap;
  background: var(--paper-deep);
  border-left: 3px solid var(--ochre);
}

.chat-pending {
  color: var(--ink-soft);
}

.chat-stopped-note {
  color: var(--ink-soft);
  font-size: 12px;
}

.chat-caret {
  display: inline-block;
  width: 7px;
  height: 14px;
  margin-left: 5px;
  vertical-align: -2px;
  background: var(--ink-soft);
  animation: chat-caret-blink 1s steps(2, start) infinite;
}

@keyframes chat-caret-blink {
  to {
    visibility: hidden;
  }
}

@media (prefers-reduced-motion: reduce) {
  .chat-caret {
    animation: none;
  }
}
</style>
