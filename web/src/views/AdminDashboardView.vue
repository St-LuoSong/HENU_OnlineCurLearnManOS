<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'

import {
  getAdminDashboardSummary,
  listAdminModels,
  listLlmProviders,
  type AdminDashboardSummary,
  type LlmModelConfig,
  type LlmProviderConfig
} from '@/api/admin'

const loading = ref(true)
const summary = ref<AdminDashboardSummary | null>(null)
const providers = ref<LlmProviderConfig[]>([])
const models = ref<LlmModelConfig[]>([])

onMounted(async () => {
  try {
    const [summaryData, providerList, modelList] = await Promise.all([
      getAdminDashboardSummary(),
      listLlmProviders(),
      listAdminModels()
    ])
    summary.value = summaryData
    providers.value = providerList
    models.value = modelList
  } catch {
    ElMessage.error('管理概览加载失败')
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div v-loading="loading" class="ledger-page">
    <header class="ledger-header">
      <div>
        <div class="ledger-kicker">管理后台</div>
        <h1 class="ledger-title">平台概览</h1>
        <p class="ledger-subtitle">
          账号规模、教学班级、AI 调用与积分异常全部由数据库实时聚合，不再抽样估算。
        </p>
      </div>
      <div class="ledger-date">管理控制台</div>
    </header>

    <div class="summary-strip">
      <div class="summary-item">
        <span class="summary-value">{{ summary?.totalUsers ?? 0 }}</span>
        <span class="summary-label">账号总数</span>
      </div>
      <div class="summary-item">
        <span class="summary-value">{{ summary?.studentUsers ?? 0 }}</span>
        <span class="summary-label">学生账号</span>
      </div>
      <div class="summary-item">
        <span class="summary-value">{{ summary?.teacherUsers ?? 0 }}</span>
        <span class="summary-label">教师账号</span>
      </div>
      <div class="summary-item">
        <span class="summary-value">{{ summary?.pendingTeachers ?? 0 }}</span>
        <span class="summary-label">待审核教师</span>
      </div>
      <div class="summary-item">
        <span class="summary-value">{{ summary?.activeCloudClasses ?? 0 }}</span>
        <span class="summary-label">进行中的云班级</span>
      </div>
    </div>

    <div class="summary-strip">
      <div class="summary-item">
        <span class="summary-value">{{ summary?.llmCallsToday ?? 0 }}</span>
        <span class="summary-label">今日 AI 调用</span>
      </div>
      <div class="summary-item">
        <span class="summary-value">{{ summary?.llmFailuresToday ?? 0 }}</span>
        <span class="summary-label">今日调用失败</span>
      </div>
      <div class="summary-item">
        <span class="summary-value">{{ summary?.reservedPointTransactions ?? 0 }}</span>
        <span class="summary-label">超时未确认积分</span>
      </div>
      <div class="summary-item">
        <span class="summary-value">{{ summary?.configuredProviders ?? 0 }}</span>
        <span class="summary-label">已配置供应商</span>
      </div>
      <div class="summary-item">
        <span class="summary-value">{{ summary?.enabledModels ?? 0 }}</span>
        <span class="summary-label">已启用模型</span>
      </div>
    </div>

    <div class="ledger-grid">
      <section class="ledger-section">
        <h2 class="ledger-section-title">
          AI 供应商状态
          <small>是否已录入 API Key</small>
        </h2>
        <div v-if="providers.length === 0" class="empty-ledger">还没有供应商配置。</div>
        <div v-for="provider in providers" :key="provider.id" class="week-row">
          <div class="week-day">{{ provider.code }}</div>
          <div>
            <div class="task-title">{{ provider.name }}</div>
            <div class="task-meta">
              {{ provider.enabled ? '已启用' : '已停用' }} ·
              {{ provider.apiKeyConfigured ? '已配置 Key' : '未配置 Key' }}
            </div>
          </div>
          <div class="week-value">{{ provider.apiKeyConfigured ? '可用' : '待配置' }}</div>
        </div>
      </section>

      <aside class="ledger-section">
        <h2 class="ledger-section-title">
          模型价格
          <small>积分消耗从低到高</small>
        </h2>
        <div v-for="model in models" :key="model.id" class="week-row">
          <div class="week-day">{{ model.providerName }}</div>
          <div>
            <div class="task-title">{{ model.displayName }}</div>
            <div class="task-meta">
              {{ model.modelCode }} · {{ model.enabled ? '已启用' : '已停用' }}
            </div>
          </div>
          <div class="week-value">{{ model.pointCost }} 分</div>
        </div>
      </aside>
    </div>
  </div>
</template>
