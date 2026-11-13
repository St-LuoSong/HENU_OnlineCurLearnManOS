<script setup lang="ts">
import { Promotion, VideoPause } from '@element-plus/icons-vue'

defineProps<{
  modelValue: string
  placeholder?: string
  sending?: boolean
  disabled?: boolean
  mode: 'chat' | 'practice'
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: string): void
  (event: 'send'): void
  (event: 'stop'): void
}>()

function handleInput(value: string): void {
  emit('update:modelValue', value)
}
</script>

<template>
  <div class="chat-composer assistant-composer">
    <el-input
      :model-value="modelValue"
      type="textarea"
      :rows="3"
      resize="none"
      maxlength="2000"
      :disabled="disabled"
      :placeholder="placeholder"
      @update:model-value="handleInput"
      @keydown.enter.exact.prevent="emit('send')"
    />
    <div class="assistant-composer-actions">
      <el-button
        v-if="sending"
        type="warning"
        plain
        :icon="VideoPause"
        @click="emit('stop')"
      >
        停止生成
      </el-button>
      <el-button
        type="primary"
        :icon="Promotion"
        :disabled="disabled || sending || !modelValue.trim()"
        @click="emit('send')"
      >
        {{ mode === 'practice' ? '发送 / 提交' : '发送' }}
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.assistant-composer {
  flex: 0 0 auto;
}

.assistant-composer-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
