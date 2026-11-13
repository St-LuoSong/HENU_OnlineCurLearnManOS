<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'

import { getPointAnomalies, type PointAnomalySummary } from '@/api/admin'

const loading = ref(false)
const summary = ref<PointAnomalySummary>({
  staleReservations: [],
  staleRunningCalls: [],
  succeededButUnconfirmed: [],
  failedButNotRefunded: [],
  scannedCallCount: 0
})

onMounted(() => {
  void load()
})

async function load(): Promise<void> {
  loading.value = true
  try {
    summary.value = await getPointAnomalies()
  } catch {
    ElMessage.error('积分异常清单加载失败')
  } finally {
    loading.value = false
  }
}

function formatDateTime(value?: string): string {
  return value ? value.replace('T', ' ').slice(0, 19) : '未记录'
}
</script>

<template>
  <div v-loading="loading" class="ledger-page">
    <header class="ledger-header">
      <div>
        <div class="ledger-kicker">管理后台</div>
        <h1 class="ledger-title">积分与 AI 异常</h1>
        <p class="ledger-subtitle">
          预占积分与调用记录必须成对收敛。这里列出未自动收敛的异常，用于人工核对与追账。
        </p>
      </div>
      <div class="ledger-date">
        <el-button @click="load">刷新</el-button>
      </div>
    </header>

    <div class="summary-strip">
      <div class="summary-item">
        <span class="summary-value">{{ summary.staleReservations.length }}</span>
        <span class="summary-label">超时未确认预占</span>
      </div>
      <div class="summary-item">
        <span class="summary-value">{{ summary.staleRunningCalls.length }}</span>
        <span class="summary-label">超时未完成调用</span>
      </div>
      <div class="summary-item">
        <span class="summary-value">{{ summary.succeededButUnconfirmed.length }}</span>
        <span class="summary-label">成功但未确认扣费</span>
      </div>
      <div class="summary-item">
        <span class="summary-value">{{ summary.failedButNotRefunded.length }}</span>
        <span class="summary-label">失败但未退款</span>
      </div>
      <div class="summary-item">
        <span class="summary-value">{{ summary.scannedCallCount }}</span>
        <span class="summary-label">本次核对调用数</span>
      </div>
    </div>

    <el-tabs>
      <el-tab-pane :label="`超时未确认预占 (${summary.staleReservations.length})`">
        <div v-if="summary.staleReservations.length === 0" class="empty-ledger">
          没有超过补偿阈值的预占积分。
        </div>
        <el-table v-else :data="summary.staleReservations" border>
          <el-table-column prop="transactionId" label="流水号" width="100" />
          <el-table-column prop="userId" label="用户" width="90" />
          <el-table-column prop="type" label="类型" width="140" />
          <el-table-column prop="amount" label="积分" width="80" />
          <el-table-column prop="idempotencyKey" label="幂等键" min-width="240" />
          <el-table-column label="创建时间" width="170">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane :label="`超时未完成调用 (${summary.staleRunningCalls.length})`">
        <div v-if="summary.staleRunningCalls.length === 0" class="empty-ledger">
          没有长时间停留在 RUNNING 的调用记录。
        </div>
        <el-table v-else :data="summary.staleRunningCalls" border>
          <el-table-column prop="callRecordId" label="记录号" width="100" />
          <el-table-column prop="userId" label="用户" width="90" />
          <el-table-column prop="providerCode" label="供应商" width="120" />
          <el-table-column prop="model" label="模型" width="160" />
          <el-table-column prop="idempotencyKey" label="幂等键" min-width="240" />
          <el-table-column label="创建时间" width="170">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane :label="`成功但未确认扣费 (${summary.succeededButUnconfirmed.length})`">
        <div v-if="summary.succeededButUnconfirmed.length === 0" class="empty-ledger">
          最近 {{ summary.scannedCallCount }} 条调用中没有发现"已成功但积分仍预占"的情况。
        </div>
        <el-table v-else :data="summary.succeededButUnconfirmed" border>
          <el-table-column prop="callRecordId" label="记录号" width="100" />
          <el-table-column prop="userId" label="用户" width="90" />
          <el-table-column prop="providerCode" label="供应商" width="120" />
          <el-table-column prop="model" label="模型" width="160" />
          <el-table-column prop="idempotencyKey" label="幂等键" min-width="240" />
          <el-table-column label="创建时间" width="170">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane :label="`失败但未退款 (${summary.failedButNotRefunded.length})`">
        <div v-if="summary.failedButNotRefunded.length === 0" class="empty-ledger">
          最近 {{ summary.scannedCallCount }} 条调用中没有发现"已失败但积分未退回"的情况。
        </div>
        <el-table v-else :data="summary.failedButNotRefunded" border>
          <el-table-column prop="callRecordId" label="记录号" width="100" />
          <el-table-column prop="userId" label="用户" width="90" />
          <el-table-column prop="providerCode" label="供应商" width="120" />
          <el-table-column prop="errorMessage" label="失败原因" min-width="240" />
          <el-table-column prop="idempotencyKey" label="幂等键" min-width="240" />
          <el-table-column label="创建时间" width="170">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>
