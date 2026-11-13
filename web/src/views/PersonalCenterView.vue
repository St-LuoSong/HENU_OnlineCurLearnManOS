<script setup lang="ts">
import {
  Bell,
  DataBoard,
  MagicStick,
  Medal,
  Setting,
  User,
  Wallet
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, onMounted, ref, watch, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { getAiAccess, type AiAccess } from '@/api/aiAccess'
import { getCheckinStatus, type CheckinStatus } from '@/api/checkin'
import { getPointAccount, type PointAccount } from '@/api/points'
import { getUserProfile, type UserProfile } from '@/api/settings'
import AiPreferencePanel from '@/components/profile/AiPreferencePanel.vue'
import NotificationPanel from '@/components/profile/NotificationPanel.vue'
import PointsLedgerPanel from '@/components/profile/PointsLedgerPanel.vue'
import ProfileSettingsPanel from '@/components/profile/ProfileSettingsPanel.vue'
import SecurityPanel from '@/components/profile/SecurityPanel.vue'

type TabKey = 'overview' | 'profile' | 'points' | 'ai' | 'security' | 'notify'

interface TabItem {
  key: TabKey
  label: string
  icon: Component
  hint: string
}

const route = useRoute()
const router = useRouter()
const loading = ref(true)
const activeTab = ref<TabKey>('overview')
const profile = ref<UserProfile | null>(null)
const account = ref<PointAccount>({
  id: 0,
  userId: 0,
  balance: 0,
  totalEarned: 0,
  totalSpent: 0,
  updatedAt: ''
})
const checkin = ref<CheckinStatus>({
  checkedToday: false,
  currentStreak: 0,
  todayReward: 10,
  nextReward: 10,
  totalCheckins: 0,
  recentCheckins: []
})
const aiAccess = ref<AiAccess>({
  standardUsed: 0,
  standardLimit: 50,
  standardRemaining: 50,
  expertUsed: 0,
  expertLimit: 3,
  expertRemaining: 3
})

const tabs: TabItem[] = [
  { key: 'overview', label: '账户概览', icon: DataBoard, hint: '今日状态' },
  { key: 'profile', label: '基本资料', icon: User, hint: '身份与联系方式' },
  { key: 'points', label: '积分账本', icon: Wallet, hint: '余额与流水' },
  { key: 'ai', label: 'AI 与模型', icon: MagicStick, hint: '模型偏好' },
  { key: 'security', label: '账号安全', icon: Setting, hint: '密码申请' },
  { key: 'notify', label: '通知与隐私', icon: Bell, hint: '提醒方式' }
]
activeTab.value = readTab(route.query.tab)

const roleLabel = computed(() => {
  if (profile.value?.roles.includes('ADMIN')) return '管理员'
  if (profile.value?.roles.includes('TEACHER')) return '教师'
  return '学生'
})
const userInitial = computed(() => (profile.value?.displayName ?? '我').slice(0, 1))
const profileCompletion = computed(() => {
  const value = profile.value
  if (!value) return 0
  const fields = [
    value.displayName,
    value.email,
    value.department,
    value.major,
    value.bio,
    value.gradeLevel || value.teacherTitle
  ]
  return Math.round(fields.filter((field) => Boolean(field && String(field).trim())).length / fields.length * 100)
})

onMounted(async () => {
  await Promise.all([loadProfile(), loadAccount(), loadCheckin(), loadAiAccess()])
  loading.value = false
})

watch(
  () => route.query.tab,
  (value) => {
    activeTab.value = readTab(value)
  }
)

function readTab(value: unknown): TabKey {
  const tab = String(value ?? '')
  return tabs.some((item) => item.key === tab) ? tab as TabKey : 'overview'
}

async function loadProfile(): Promise<void> {
  try {
    profile.value = await getUserProfile()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '个人资料加载失败')
  }
}

async function loadAccount(): Promise<void> {
  try {
    account.value = await getPointAccount()
  } catch {
    // 账户摘要失败不阻塞其他设置。
  }
}

async function loadCheckin(): Promise<void> {
  try {
    checkin.value = await getCheckinStatus()
  } catch {
    // 签到记录由首页负责展示。
  }
}

async function loadAiAccess(): Promise<void> {
  try {
    aiAccess.value = await getAiAccess()
  } catch {
    // 配额展示失败时不影响模型偏好设置。
  }
}

async function selectTab(key: TabKey): Promise<void> {
  activeTab.value = key
  const query = { ...route.query }
  if (key === 'overview') {
    delete query.tab
  } else {
    query.tab = key
  }
  await router.replace({ query })
}

function applyProfile(saved: UserProfile): void {
  profile.value = saved
}
</script>

<template>
  <div v-loading="loading" class="ledger-page personal-center-page">
    <header class="ledger-header">
      <div>
        <div class="ledger-kicker">个人中心</div>
        <h1 class="ledger-title">我的学习账户</h1>
        <p class="ledger-subtitle">
          资料、积分、模型偏好与账号安全都在这里维护。签到入口已移至学习首页。
        </p>
      </div>
      <div class="ledger-date">{{ roleLabel }} · {{ profile?.loginId || '未设置登录标识' }}</div>
    </header>

    <section class="center-identity">
      <el-avatar :size="68" :src="profile?.avatarUrl || undefined" class="center-avatar">
        {{ userInitial }}
      </el-avatar>
      <div class="center-identity-copy">
        <div class="center-identity-name">{{ profile?.displayName || '我的账号' }}</div>
        <div class="center-identity-meta">
          {{ roleLabel }}
          <template v-if="profile?.department"> · {{ profile.department }}</template>
          <template v-if="profile?.major"> · {{ profile.major }}</template>
        </div>
        <div class="center-identity-note">
          {{ profile?.bio || '完善个人资料后，学习记录和教师沟通会更有上下文。' }}
        </div>
      </div>
      <div class="center-identity-stats">
        <div>
          <strong>{{ account.balance }}</strong>
          <span>积分余额</span>
        </div>
        <div>
          <strong>{{ checkin.currentStreak }}</strong>
          <span>连续签到</span>
        </div>
        <div>
          <strong>{{ aiAccess.expertRemaining }}</strong>
          <span>专家次数</span>
        </div>
      </div>
    </section>

    <div class="center-shell">
      <nav class="center-nav" aria-label="个人中心导航">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          type="button"
          class="center-nav-item"
          :class="{ active: activeTab === tab.key }"
          @click="selectTab(tab.key)"
        >
          <el-icon><component :is="tab.icon" /></el-icon>
          <span>
            <b>{{ tab.label }}</b>
            <small>{{ tab.hint }}</small>
          </span>
        </button>
      </nav>

      <main class="center-content">
        <template v-if="activeTab === 'overview'">
          <section class="center-panel">
            <div class="center-panel-head">
              <div>
                <h2>账户概览</h2>
                <p>先看今天的状态，再决定下一步做什么。</p>
              </div>
              <span class="center-panel-meta">资料完整度 {{ profileCompletion }}%</span>
            </div>
            <div class="overview-progress">
              <div class="overview-progress-track">
                <div :style="{ width: profileCompletion + '%' }" />
              </div>
              <span>完善资料后，学习建议会更贴合你的专业与目标。</span>
            </div>
            <div class="overview-metrics">
              <div class="overview-metric">
                <div class="overview-metric-label">
                  <el-icon><Wallet /></el-icon>
                  <span>累计获得积分</span>
                </div>
                <strong>{{ account.totalEarned }}</strong>
                <small>签到与任务奖励都会累积到这里</small>
              </div>
              <div class="overview-metric">
                <div class="overview-metric-label">
                  <el-icon><Medal /></el-icon>
                  <span>累计签到次数</span>
                </div>
                <strong>{{ checkin.totalCheckins }}</strong>
                <small>最近记录 {{ checkin.recentCheckins.length }} 天</small>
              </div>
              <div class="overview-metric">
                <div class="overview-metric-label">
                  <el-icon><MagicStick /></el-icon>
                  <span>今日普通模式剩余</span>
                </div>
                <strong>{{ aiAccess.standardRemaining }}</strong>
                <small>今日已用 {{ aiAccess.standardUsed }}/{{ aiAccess.standardLimit }} 次</small>
              </div>
            </div>
            <div class="overview-checkin">
              <span>最近签到</span>
              <span v-if="checkin.recentCheckins.length === 0" class="overview-checkin-empty">
                还没有签到记录，去首页完成第一次签到。
              </span>
              <span v-for="date in checkin.recentCheckins.slice(0, 7)" :key="date" class="overview-checkin-day">
                {{ date.slice(5) }}
              </span>
            </div>
            <div class="overview-actions">
              <el-button type="primary" @click="router.push('/dashboard')">返回学习首页</el-button>
              <el-button @click="router.push('/practice')">去练习场</el-button>
              <el-button @click="router.push('/knowledge-bag')">打开知识锦囊</el-button>
            </div>
          </section>
        </template>
        <ProfileSettingsPanel v-else-if="activeTab === 'profile'" :profile="profile" @saved="applyProfile" />
        <PointsLedgerPanel v-else-if="activeTab === 'points'" />
        <AiPreferencePanel v-else-if="activeTab === 'ai'" />
        <SecurityPanel v-else-if="activeTab === 'security'" />
        <NotificationPanel v-else-if="activeTab === 'notify'" :profile="profile" @saved="applyProfile" />
      </main>
    </div>
  </div>
</template>

<style scoped>
.personal-center-page {
  display: grid;
  gap: 18px;
}

.center-identity {
  display: grid;
  grid-template-columns: 68px minmax(0, 1fr) auto;
  gap: 18px;
  align-items: center;
  padding: 22px 24px;
  background: var(--white);
  border: 1px solid var(--rule);
  border-left: 4px solid var(--primary);
}

.center-avatar {
  color: var(--white);
  background: var(--primary);
  font-family: var(--font-display);
  font-size: 24px;
  font-weight: 700;
}

.center-identity-copy {
  min-width: 0;
}

.center-identity-name {
  color: var(--ink);
  font-family: var(--font-serif);
  font-size: 28px;
  font-weight: 700;
}

.center-identity-meta {
  margin-top: 4px;
  color: var(--primary-deep);
  font-size: 12px;
}

.center-identity-note {
  max-width: 680px;
  margin-top: 8px;
  overflow: hidden;
  color: var(--ink-soft);
  font-size: 12px;
  line-height: 1.7;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.center-identity-stats {
  display: flex;
  gap: 22px;
  padding-left: 20px;
  border-left: 1px solid var(--rule);
}

.center-identity-stats div {
  display: grid;
  gap: 2px;
  text-align: right;
}

.center-identity-stats strong {
  color: var(--primary-deep);
  font-family: var(--font-numeric);
  font-size: 24px;
}

.center-identity-stats span {
  color: var(--ink-soft);
  font-size: 11px;
}

.center-shell {
  display: grid;
  grid-template-columns: 210px minmax(0, 1fr);
  gap: 22px;
  align-items: start;
}

.center-nav {
  display: grid;
  gap: 2px;
  padding: 8px 0;
  border-top: 1px solid var(--rule);
}

.center-nav-item {
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr);
  gap: 10px;
  align-items: center;
  padding: 11px 10px;
  color: var(--ink-soft);
  text-align: left;
  background: transparent;
  border: 0;
  border-left: 2px solid transparent;
  cursor: pointer;
}

.center-nav-item:hover,
.center-nav-item.active {
  color: var(--primary-deep);
  background: var(--primary-wash);
  border-left-color: var(--primary);
}

.center-nav-item span {
  display: grid;
  gap: 2px;
}

.center-nav-item b {
  font-size: 13px;
  font-weight: 700;
}

.center-nav-item small {
  color: var(--ink-soft);
  font-size: 10px;
}

.center-content {
  min-width: 0;
}

.center-content > * {
  animation: center-panel-in 0.22s ease-out;
}

@keyframes center-panel-in {
  from { opacity: 0; transform: translateY(4px); }
  to { opacity: 1; transform: translateY(0); }
}

.overview-progress {
  display: grid;
  gap: 8px;
  margin: 18px 0;
  color: var(--ink-soft);
  font-size: 12px;
}

.overview-progress-track {
  height: 5px;
  background: var(--paper-deep);
}

.overview-progress-track div {
  height: 5px;
  background: var(--jade);
  transition: width 360ms ease;
}

.overview-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  padding: 18px 0;
  border-top: 1px solid var(--rule);
}

.overview-metric {
  display: grid;
  gap: 4px;
  padding: 14px;
  background: var(--paper);
}

.overview-metric-label {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--primary);
  font-size: 11px;
}

.overview-metric-label :deep(.el-icon) {
  font-size: 13px;
}

.overview-metric strong {
  color: var(--ink);
  font-family: var(--font-numeric);
  font-size: 25px;
}

.overview-metric small {
  color: var(--ink-soft);
  font-size: 11px;
  line-height: 1.5;
}

.overview-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding-top: 16px;
  border-top: 1px solid var(--rule);
}

.overview-checkin {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  padding-top: 14px;
  color: var(--ink-soft);
  font-size: 11px;
  border-top: 1px solid var(--rule);
}

.overview-checkin-day {
  padding: 2px 7px;
  color: var(--jade);
  background: var(--jade-soft);
  border-radius: var(--radius-pill);
}

.overview-checkin-empty {
  color: var(--ink-soft);
}

@media (max-width: 900px) {
  .center-identity {
    grid-template-columns: 56px minmax(0, 1fr);
  }

  .center-identity-stats {
    grid-column: 1 / -1;
    justify-content: flex-start;
    padding: 12px 0 0;
    border-top: 1px solid var(--rule);
    border-left: 0;
  }

  .center-identity-stats div {
    text-align: left;
  }

  .center-shell {
    grid-template-columns: 1fr;
  }

  .center-nav {
    display: flex;
    overflow-x: auto;
    border-top: 1px solid var(--rule);
    border-bottom: 1px solid var(--rule);
  }

  .center-nav-item {
    flex: 0 0 auto;
    min-width: 108px;
    border-bottom: 2px solid transparent;
    border-left: 0;
  }

  .center-nav-item.active {
    border-bottom-color: var(--primary);
    border-left-color: transparent;
  }
}

@media (max-width: 620px) {
  .center-identity-note {
    white-space: normal;
  }

  .overview-metrics {
    grid-template-columns: 1fr;
  }
}

@media (prefers-reduced-motion: reduce) {
  .center-content > * { animation: none; }
  .overview-progress-track div { transition: none; }
}
</style>
