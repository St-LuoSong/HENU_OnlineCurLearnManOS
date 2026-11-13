<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Medal } from '@element-plus/icons-vue'

import {
  getPointAccount,
  listPointTransactions,
  type PointAccount,
  type PointTransaction
} from '@/api/points'
import {
  checkinToday,
  getCheckinStatus,
  type CheckinStatus
} from '@/api/checkin'
import { formatDateTime as formatDateTimeText } from '@/utils/date'

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
const size = 10
const checkin = ref<CheckinStatus>({
  checkedToday: false,
  currentStreak: 0,
  todayReward: 10,
  nextReward: 10,
  totalCheckins: 0,
  recentCheckins: []
})
const checking = ref(false)

onMounted(async () => {
  await loadAll()
})

async function loadAll(): Promise<void> {
  loading.value = true
  try {
    const [accountData, transactionPage, checkinData] = await Promise.all([
      getPointAccount(),
      listPointTransactions({ page: page.value, size }),
      getCheckinStatus()
    ])
    account.value = accountData
    transactions.value = transactionPage.records
    total.value = transactionPage.total
    checkin.value = checkinData
  } catch {
    ElMessage.error('积分账本加载失败')
  } finally {
    loading.value = false
  }
}

async function handleCheckin(): Promise<void> {
  if (checkin.value.checkedToday) {
    return
  }
  checking.value = true
  try {
    const result = await checkinToday()
    checkin.value = result.status
    ElMessage.success(`签到成功，获得 ${result.earnedPoints} 积分`)
    await loadAll()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '签到失败')
  } finally {
    checking.value = false
  }
}

function handlePageChange(value: number): void {
  page.value = value
  void loadAll()
}

function typeLabel(type: string): string {
  const labels: Record<string, string> = {
    AI_CHAT: '智能问答',
    AI_PLAN: '学习规划',
    AI_CAREER: '生涯分析',
    AI_PRACTICE: '练习评估',
    AI_PRACTICE_SET: '练习出题',
    DAILY_CHECKIN: '每日签到',
    INITIAL_GRANT: '初始积分',
    CLASS_TASK_REWARD: '云班级任务奖励',
    ADMIN_ADJUST: '管理调整'
  }
  return labels[type] ?? type
}

function signedAmount(transaction: PointTransaction): string {
  const value = Math.abs(transaction.amount)
  return transaction.direction === 'DEBIT' ? `-${value}` : `+${value}`
}

function statusLabel(status: string): string {
  const labels: Record<string, string> = {
    RESERVED: '预占中',
    CONFIRMED: '已确认',
    REFUNDED: '已退款'
  }
  return labels[status] ?? status
}

function formatDateTime(value?: string): string {
  return formatDateTimeText(value, '未完成')
}
</script>

<template>
  <div v-loading="loading" class="ledger-page">
    <header class="ledger-header">
      <div>
        <div class="ledger-kicker">积分账本</div>
        <h1 class="ledger-title">每一次 AI 调用都有记录</h1>
        <p class="ledger-subtitle">
          积分预占、确认和退款都写入流水。余额变化应该能从账本中逐条解释。
        </p>
      </div>
      <div class="ledger-date">
        <el-button
          type="primary"
          :icon="Medal"
          :loading="checking"
          :disabled="checkin.checkedToday"
          @click="handleCheckin"
        >
          {{ checkin.checkedToday ? '今日已签到' : `签到 +${checkin.todayReward}` }}
        </el-button>
      </div>
    </header>

    <div class="summary-strip">
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
      <div class="summary-item">
        <span class="summary-value">#{{ account.id }}</span>
        <span class="summary-label">积分账户</span>
      </div>
      <div class="summary-item">
        <span class="summary-value">{{ checkin.currentStreak }}</span>
        <span class="summary-label">连续签到天数</span>
      </div>
    </div>

    <div class="ledger-grid">
      <section class="ledger-section">
        <h2 class="ledger-section-title">
          积分流水
          <small>按时间倒序</small>
        </h2>

        <div v-if="transactions.length === 0" class="empty-ledger">
          还没有积分流水。使用一次智能问答后，这里会出现预占、确认或退款记录。
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
          class="ledger-pagination"
          layout="prev, pager, next"
          :current-page="page"
          :page-size="size"
          :total="total"
          @current-change="handlePageChange"
        />
      </section>

      <aside class="ledger-section">
        <h2 class="ledger-section-title">
          签到与 AI 规则
          <small>普通免费，专家限次</small>
        </h2>
        <div class="checkin-panel">
          <div class="checkin-streak">
            连续 <strong>{{ checkin.currentStreak }}</strong> 天
          </div>
          <div class="checkin-days">
            <span
              v-for="day in 7"
              :key="day"
              class="checkin-day"
              :class="{ active: checkin.currentStreak >= day }"
            >
              {{ day }}
            </span>
          </div>
          <div class="empty-ledger">
            {{ checkin.checkedToday
              ? `今天已签到，明天签到可得 ${checkin.nextReward} 积分。`
              : `今天签到可得 ${checkin.todayReward} 积分，连续第 7 天额外 +30。` }}
          </div>
        </div>
        <div class="week-rail">
          <div class="week-row">
            <div class="week-day">普通</div>
            <div class="task-title">问答与练习不扣积分，每日 50 次</div>
            <div class="week-value">免费</div>
          </div>
          <div class="week-row">
            <div class="week-day">专家</div>
            <div class="task-title">按专家模型价格扣积分，每日 3 次</div>
            <div class="week-value">限次</div>
          </div>
          <div class="week-row">
            <div class="week-day">练习</div>
            <div class="task-title">普通模式每科目首套免费，重做扣积分</div>
            <div class="week-value">已接入</div>
          </div>
          <div class="week-row">
            <div class="week-day">退款</div>
            <div class="task-title">调用失败或未出内容即停止，自动退回</div>
            <div class="week-value">已接入</div>
          </div>
        </div>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.point-amount {
  font-family: var(--font-display);
  font-size: 18px;
  font-weight: 700;
  text-align: right;
}

.amount-positive {
  color: var(--jade);
}

.amount-negative {
  color: var(--cinnabar);
}

.checkin-panel {
  padding-bottom: 12px;
  border-bottom: 1px solid var(--rule);
}

.checkin-streak {
  color: var(--ink-soft);
  font-size: 13px;
}

.checkin-streak strong {
  color: var(--primary);
  font-family: var(--font-numeric);
  font-size: 28px;
}

.checkin-days {
  display: flex;
  gap: 8px;
  margin: 10px 0;
}

.checkin-day {
  display: inline-flex;
  width: 28px;
  height: 28px;
  align-items: center;
  justify-content: center;
  color: var(--ink-soft);
  font-family: var(--font-numeric);
  border: 1px solid var(--rule);
}

.checkin-day.active {
  color: var(--white);
  background: var(--jade);
  border-color: var(--jade);
}
</style>
