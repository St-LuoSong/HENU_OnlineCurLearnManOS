<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'

import { listAuditLogs, type AdminAuditLog } from '@/api/admin'

const loading = ref(false)
const logs = ref<AdminAuditLog[]>([])
const total = ref(0)
const page = ref(1)
const size = 20

const ACTION_LABELS: Record<string, string> = {
  USER_ENABLE: '启用账号',
  USER_DISABLE: '停用账号'
}

onMounted(() => {
  void load()
})

async function load(): Promise<void> {
  loading.value = true
  try {
    const result = await listAuditLogs({ page: page.value, size })
    logs.value = result.records
    total.value = result.total
  } catch {
    ElMessage.error('审计日志加载失败')
  } finally {
    loading.value = false
  }
}

function handlePageChange(value: number): void {
  page.value = value
  void load()
}

function actionLabel(action: string): string {
  return ACTION_LABELS[action] ?? action
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
        <h1 class="ledger-title">审计日志</h1>
        <p class="ledger-subtitle">
          记录管理员的账号启停等管理动作，用于事后追溯责任与异常排查。
        </p>
      </div>
      <div class="ledger-date">共 {{ total }} 条记录</div>
    </header>

    <div v-if="logs.length === 0" class="empty-ledger">还没有管理操作记录。</div>

    <el-table v-else :data="logs" border>
      <el-table-column prop="operatorName" label="操作人" width="140" />
      <el-table-column label="动作" width="140">
        <template #default="{ row }">{{ actionLabel(row.action) }}</template>
      </el-table-column>
      <el-table-column prop="targetType" label="目标类型" width="130" />
      <el-table-column prop="targetId" label="目标 ID" width="110" />
      <el-table-column prop="detail" label="明细" min-width="240" show-overflow-tooltip />
      <el-table-column label="时间" width="180">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-if="total > size"
      class="ledger-pagination"
      layout="prev, pager, next"
      :current-page="page"
      :page-size="size"
      :total="total"
      @current-change="handlePageChange"
    />
  </div>
</template>
