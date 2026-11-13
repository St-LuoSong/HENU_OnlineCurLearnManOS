<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'

import {
  getPointAccount,
  listPointTransactions,
  type PointAccount,
  type PointTransaction
} from '@/api/points'
import { formatDateTime } from '@/utils/date'

const loading = ref(true)
const account = ref<PointAccount>({
  id: 0,
  userId: 0,
  balance: 0,
  totalEarned: 0,
  totalSpent: 0,
  updatedAt: ''
})
const transactions = ref<PointTransaction[]>([])
const total = ref(0)
const page = ref(1)
const size = 8

onMounted(load)

async function load(): Promise<void> {
  loading.value = true
  try {
    const [accountData, transactionPage] = await Promise.all([
      getPointAccount(),
      listPointTransactions({ page: page.value, size })
    ])
    account.value = accountData
    transactions.value = transactionPage.records
    total.value = transactionPage.total
  } catch {
    ElMessage.error('积分账本加载失败')
  } finally {
    loading.value = false
  }
}

function changePage(value: number): void {
  page.value = value
  void load()
}

function typeLabel(type: string): string {
  const labels: Record<string, string> = {
    AI_CHAT: '智能问答',
    AI_PLAN: '学习规划',
    AI_CAREER: '生涯分析',
    AI_PRACTICE: '练习评估',
    AI_PRACTICE_SET: '练习套题',
    DAILY_CHECKIN: '每日签到',
    INITIAL_GRANT: '初始积分',
    CLASS_TASK_REWARD: '云班级任务奖励',
    ADMIN_ADJUST: '管理调整'
  }
  return labels[type] ?? type
}

function statusLabel(status: string): string {
  const labels: Record<string, string> = {
    RESERVED: '预占中',
    CONFIRMED: '已确认',
    REFUNDED: '已退款'
  }
  return labels[status] ?? status
}

function signedAmount(transaction: PointTransaction): string {
  const value = Math.abs(transaction.amount)
  return transaction.direction === 'DEBIT' ? `-${value}` : `+${value}`
}
</script>

<template>
  <section v-loading="loading" class="center-panel">
    <div class="center-panel-head">
      <div>
        <h2>积分账本</h2>
        <p>余额变化可以逐条追溯，签到与 AI 调用都会留下流水。</p>
      </div>
      <span class="center-panel-meta">更新于 {{ formatDateTime(account.updatedAt) }}</span>
    </div>

    <div class="summary-strip center-summary">
      <div class="summary-item">
        <span class="summary-value">{{ account.balance }}</span>
        <span class="summary-label">当前余额</span>
      </div>
      <div class="summary-item">
        <span class="summary-value">{{ account.totalEarned }}</span>
        <span class="summary-label">累计获得</span>
      </div>
      <div class="summary-item">
        <span class="summary-value">{{ account.totalSpent }}</span>
        <span class="summary-label">累计消耗</span>
      </div>
      <div class="summary-item">
        <span class="summary-value">{{ total }}</span>
        <span class="summary-label">流水条数</span>
      </div>
    </div>

    <div v-if="transactions.length === 0" class="empty-ledger">
      还没有积分流水。完成签到或使用一次 AI 功能后会出现在这里。
    </div>
    <div v-for="transaction in transactions" :key="transaction.id" class="task-line">
      <div class="week-day">{{ typeLabel(transaction.type) }}</div>
      <div>
        <div class="task-title">
          {{ transaction.remark ?? transaction.referenceType ?? '积分变动' }}
        </div>
        <div class="task-meta">
          {{ formatDateTime(transaction.createdAt) }}
          · {{ statusLabel(transaction.status) }}
          · 余额 {{ transaction.balanceAfter }}
        </div>
      </div>
      <div
        class="point-amount"
        :class="transaction.direction === 'DEBIT' ? 'amount-negative' : 'amount-positive'"
      >
        {{ signedAmount(transaction) }}
      </div>
    </div>

    <el-pagination
      v-if="total > size"
      class="center-pagination"
      layout="prev, pager, next, jumper"
      :current-page="page"
      :page-size="size"
      :total="total"
      @current-change="changePage"
    />
  </section>
</template>

<style scoped>
.center-summary {
  margin: 18px 0 8px;
}

.point-amount {
  font-family: var(--font-display);
  font-size: 18px;
  font-weight: 700;
  text-align: right;
}

.amount-positive { color: var(--jade); }
.amount-negative { color: var(--cinnabar); }

.center-pagination {
  justify-content: center;
  padding-top: 18px;
}
</style>
