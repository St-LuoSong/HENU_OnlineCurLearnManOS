<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { computed, onMounted, ref } from 'vue'

import {
  getUserAiPreference,
  updateUserAiPreference,
  type UserAiPreference
} from '@/api/settings'
import type { LlmModelConfig } from '@/api/admin'

const loading = ref(true)
const saving = ref(false)
const preference = ref<UserAiPreference>({ availableModels: [] })
const selectedModelId = ref<number | undefined>()

const selectedModel = computed(() =>
  preference.value.availableModels.find((model) => model.id === selectedModelId.value)
)

onMounted(load)

async function load(): Promise<void> {
  loading.value = true
  try {
    preference.value = await getUserAiPreference()
    selectedModelId.value = preference.value.selectedModelId
  } catch {
    ElMessage.error('AI 模型设置加载失败')
  } finally {
    loading.value = false
  }
}

async function save(): Promise<void> {
  if (!selectedModelId.value) {
    ElMessage.warning('请选择一个模型')
    return
  }
  saving.value = true
  try {
    preference.value = await updateUserAiPreference(selectedModelId.value)
    selectedModelId.value = preference.value.selectedModelId
    ElMessage.success('模型偏好已保存')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '模型偏好保存失败')
  } finally {
    saving.value = false
  }
}

function providerLabel(model: LlmModelConfig): string {
  return model.providerName ?? model.providerCode
}
</script>

<template>
  <section v-loading="loading" class="center-panel">
    <div class="center-panel-head">
      <div>
        <h2>AI 与模型</h2>
        <p>普通与专家模式分别选择模型，偏好只在对应分级内生效。</p>
      </div>
      <span class="center-panel-meta">按模型分级管理</span>
    </div>

    <div class="model-option-list">
      <button
        v-for="model in preference.availableModels"
        :key="model.id"
        type="button"
        class="model-option"
        :class="{ active: model.id === selectedModelId }"
        @click="selectedModelId = model.id"
      >
        <span>
          <strong>{{ model.displayName }}</strong>
          <small>
            {{ providerLabel(model) }} · {{ model.modelCode }}
            · {{ model.tier === 'EXPERT' ? '专家' : '普通' }}
          </small>
        </span>
        <span class="model-cost">
          {{ model.tier === 'EXPERT' ? `${model.pointCost} 积分 / 次` : '普通模式免费' }}
        </span>
      </button>
      <div v-if="preference.availableModels.length === 0" class="empty-ledger">
        管理员尚未配置可用模型。
      </div>
    </div>

    <div class="model-summary-line">
      <span v-if="selectedModel">
        当前选择：<strong>{{ selectedModel.displayName }}</strong>
      </span>
      <span v-else>尚未选择模型</span>
      <el-button type="primary" :loading="saving" @click="save">保存模型偏好</el-button>
    </div>
  </section>
</template>
