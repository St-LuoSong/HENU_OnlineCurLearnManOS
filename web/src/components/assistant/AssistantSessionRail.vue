<script setup lang="ts">
import { ChatDotRound, Notebook, Plus } from '@element-plus/icons-vue'
import { computed } from 'vue'

import type {
  AssistantFilter,
  AssistantSession
} from '@/api/assistant'
import { formatMonthDayTime } from '@/utils/date'

const STATE_LABELS: Record<string, string> = {
  LEARNING: '学习中',
  ASSESSING: '答题中',
  TRANSFER_TEST: '迁移测试',
  MASTERED: '已掌握',
  REMEDIATION: '补救教学'
}

const props = defineProps<{
  sessions: AssistantSession[]
  selectedKey: string | null
  filter: AssistantFilter
  modeOnly?: 'chat' | 'practice'
  loading?: boolean
}>()

const emit = defineEmits<{
  (event: 'select', session: AssistantSession): void
  (event: 'update:filter', filter: AssistantFilter): void
  (event: 'create', mode: 'chat' | 'practice'): void
}>()

const filteredSessions = computed(() => {
  const mode = props.modeOnly ?? (props.filter === 'all' ? null : props.filter)
  return mode ? props.sessions.filter((session) => session.sessionMode === mode) : props.sessions
})

function sessionKey(session: AssistantSession): string {
  return `${session.sourceType}:${session.id}`
}

function modeLabel(mode: string): string {
  return mode === 'practice' ? '练习' : '问答'
}

function stateLabel(session: AssistantSession): string {
  if (session.sessionMode === 'practice') {
    const state = STATE_LABELS[session.state ?? ''] ?? session.state ?? ''
    return session.currentGrade ? `${state} · ${session.currentGrade}` : state
  }
  return session.state === 'ACTIVE' ? '进行中' : session.state ?? ''
}

function formatDateTime(value?: string): string {
  return formatMonthDayTime(value, '')
}

function handleFilterChange(value: string | number | boolean | undefined): void {
  emit('update:filter', value === 'chat' || value === 'practice' ? value : 'all')
}
</script>

<template>
  <aside class="assistant-session-rail">
    <div class="assistant-rail-header">
      <div>
        <div class="chat-rail-title">AI 学习助手</div>
        <p>问答与练习统一会话</p>
      </div>
      <el-button
        v-if="modeOnly"
        type="primary"
        size="small"
        :icon="modeOnly === 'practice' ? Notebook : ChatDotRound"
        @click="emit('create', modeOnly)"
      >
        新建
      </el-button>
      <el-dropdown v-else trigger="click">
        <el-button type="primary" size="small" :icon="Plus">新建</el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item :icon="ChatDotRound" @click="emit('create', 'chat')">
              新建问答
            </el-dropdown-item>
            <el-dropdown-item :icon="Notebook" @click="emit('create', 'practice')">
              新建练习
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>

    <el-radio-group
      v-if="!modeOnly"
      class="assistant-filter"
      :model-value="filter"
      size="small"
      @update:model-value="handleFilterChange"
    >
      <el-radio-button label="all">全部</el-radio-button>
      <el-radio-button label="chat">问答</el-radio-button>
      <el-radio-button label="practice">练习</el-radio-button>
    </el-radio-group>

    <div v-if="loading" class="assistant-rail-loading">正在加载会话…</div>
    <div v-else-if="filteredSessions.length === 0" class="empty-ledger">
      当前筛选下还没有会话，点击右上角新建。
    </div>
    <div v-else class="assistant-session-list">
      <button
        v-for="item in filteredSessions"
        :key="sessionKey(item)"
        class="chat-session-item assistant-session-item"
        :class="{ active: sessionKey(item) === selectedKey }"
        @click="emit('select', item)"
      >
        <span class="assistant-session-title-row">
          <span class="chat-session-title">{{ item.title }}</span>
          <el-tag size="small" :type="item.sessionMode === 'practice' ? 'warning' : 'success'">
            {{ modeLabel(item.sessionMode) }}
          </el-tag>
        </span>
        <span class="chat-session-preview">
          {{ item.topic ?? item.subjectName ?? '通用' }}
          <template v-if="item.knowledgePointName"> · {{ item.knowledgePointName }}</template>
          · {{ item.preview || '暂无消息' }}
        </span>
        <span class="chat-session-time">
          {{ stateLabel(item) }} · {{ formatDateTime(item.updatedAt) }}
        </span>
      </button>
    </div>
  </aside>
</template>

<style scoped>
.assistant-session-rail {
  display: flex;
  min-height: 0;
  flex-direction: column;
  padding: 18px 16px 18px 0;
  border-right: 1px solid var(--rule);
}

.assistant-rail-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.assistant-rail-header p {
  margin: 6px 0 0;
  color: var(--ink-soft);
  font-size: 12px;
}

.assistant-filter {
  margin: 14px 0 10px;
}

.assistant-session-list {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
}

.assistant-session-item {
  border-radius: 0;
}

.assistant-session-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.assistant-rail-loading {
  padding: 24px 0;
  color: var(--ink-soft);
  font-size: 13px;
}
</style>
