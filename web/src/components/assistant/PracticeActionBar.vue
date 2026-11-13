<script setup lang="ts">
import { MagicStick, Notebook } from '@element-plus/icons-vue'
import { computed } from 'vue'

import type { AssistantSession } from '@/api/assistant'
import type { PracticeAnswer } from '@/api/practice'

const props = defineProps<{
  session: AssistantSession | null
  busy: boolean
  canAnswer: boolean
  lastAnswer?: PracticeAnswer | null
}>()

const emit = defineEmits<{
  (event: 'question'): void
  (event: 'hint'): void
  (event: 'quick', prompt: string): void
}>()

const questionLabel = computed(() =>
  props.session?.state === 'TRANSFER_TEST' ? '迁移测试' : '出题练习'
)

const chatActions = [
  { label: '解释概念', prompt: '请用循序渐进的方式解释这个概念，并给出一个直观类比。' },
  { label: '举例说明', prompt: '请结合一个具体例子说明，并指出容易出错的地方。' },
  { label: '总结要点', prompt: '请总结这一部分的核心要点，并列出复习顺序。' },
  { label: '生成练习', prompt: '请根据当前内容生成 3 道由浅入深的练习题，不要直接给答案。' }
]
</script>

<template>
  <div v-if="session?.sessionMode === 'practice'" class="practice-action-bar">
    <el-button :disabled="busy" :icon="Notebook" @click="emit('question')">
      {{ questionLabel }}
    </el-button>
    <el-button :disabled="busy" :icon="MagicStick" @click="emit('hint')">
      分层提示
    </el-button>
    <span v-if="canAnswer" class="practice-action-hint">当前已进入作答状态</span>
    <div v-if="lastAnswer" class="practice-answer-strip">
      <span class="career-category">{{ lastAnswer.transfer ? '迁移测试' : '练习作答' }}</span>
      <span>本题评价：{{ lastAnswer.grade }} 级</span>
      <span>{{ lastAnswer.correct ? '作答正确' : '仍需订正' }}</span>
      <span v-if="lastAnswer.errorLabel">错误类型：{{ lastAnswer.errorLabel }}</span>
    </div>
  </div>

  <div v-else-if="session" class="assistant-quick-actions">
    <el-button
      v-for="action in chatActions"
      :key="action.label"
      size="small"
      :disabled="busy"
      @click="emit('quick', action.prompt)"
    >
      {{ action.label }}
    </el-button>
  </div>
</template>

<style scoped>
.practice-action-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  padding: 12px 0;
  border-top: 1px solid var(--rule);
}

.practice-action-hint {
  color: var(--ink-soft);
  font-size: 12px;
}

.practice-answer-strip {
  display: flex;
  flex-basis: 100%;
  flex-wrap: wrap;
  gap: 14px;
  align-items: center;
  padding-top: 4px;
  color: var(--ink-soft);
  font-size: 13px;
}

.assistant-quick-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 12px 0;
  border-top: 1px solid var(--rule);
}
</style>
