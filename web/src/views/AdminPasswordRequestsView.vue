<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'

import {
  listPasswordRequests,
  reviewPasswordRequest,
  type PasswordChangeRequest
} from '@/api/settings'

const STATUS_OPTIONS = [
  { value: 'PENDING', label: '待审批' },
  { value: 'APPROVED', label: '已批准' },
  { value: 'REJECTED', label: '已驳回' },
  { value: 'CANCELLED', label: '已撤销' },
  { value: '', label: '全部' }
]

const STATUS_LABELS: Record<string, string> = {
  PENDING: '待审批',
  APPROVED: '已批准',
  REJECTED: '已驳回',
  CANCELLED: '已撤销'
}

const ROLE_LABELS: Record<string, string> = {
  STUDENT: '学生',
  TEACHER: '教师',
  ADMIN: '管理员'
}

const loading = ref(false)
const submitting = ref(false)
const requests = ref<PasswordChangeRequest[]>([])
const total = ref(0)
const page = ref(1)
const size = 10
const status = ref('PENDING')

const reviewDialogVisible = ref(false)
const reviewTarget = ref<PasswordChangeRequest | null>(null)
const reviewApproved = ref(true)
const reviewNote = ref('')

const pendingCount = computed(
  () => requests.value.filter((item) => item.status === 'PENDING').length
)

onMounted(() => {
  void load()
})

async function load(): Promise<void> {
  loading.value = true
  try {
    const result = await listPasswordRequests({
      page: page.value,
      size,
      status: status.value || undefined
    })
    requests.value = result.records
    total.value = result.total
  } catch {
    ElMessage.error('密码变更申请加载失败')
  } finally {
    loading.value = false
  }
}

function handleStatusChange(): void {
  page.value = 1
  void load()
}

function handlePageChange(value: number): void {
  page.value = value
  void load()
}

function openReview(row: unknown, approved: boolean): void {
  reviewTarget.value = row as PasswordChangeRequest
  reviewApproved.value = approved
  reviewNote.value = ''
  reviewDialogVisible.value = true
}

async function confirmReview(): Promise<void> {
  const target = reviewTarget.value
  if (!target) {
    return
  }
  const note = reviewNote.value.trim()
  if (!reviewApproved.value && !note) {
    ElMessage.warning('驳回申请必须填写说明，便于申请人了解原因')
    return
  }
  submitting.value = true
  try {
    await reviewPasswordRequest(target.id, reviewApproved.value, note)
    reviewDialogVisible.value = false
    ElMessage.success(reviewApproved.value ? '已批准密码变更申请' : '已驳回密码变更申请')
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error && error.message ? error.message : '审批提交失败')
  } finally {
    submitting.value = false
  }
}

function statusLabel(value?: string | null): string {
  return value ? STATUS_LABELS[value] ?? value : '未知'
}

function statusTagType(value?: string | null): 'success' | 'warning' | 'danger' | 'info' {
  if (value === 'APPROVED') {
    return 'success'
  }
  if (value === 'PENDING') {
    return 'warning'
  }
  if (value === 'REJECTED') {
    return 'danger'
  }
  return 'info'
}

function roleLabel(value?: string | null): string {
  return value ? ROLE_LABELS[value] ?? value : '未知'
}

function formatDateTime(value?: string | null): string {
  return value ? value.replace('T', ' ').slice(0, 19) : '—'
}
</script>

<template>
  <div v-loading="loading" class="ledger-page">
    <header class="ledger-header">
      <div>
        <div class="ledger-kicker">管理后台</div>
        <h1 class="ledger-title">账号安全审批</h1>
        <p class="ledger-subtitle">
          学生与教师的密码变更必须经管理员批准后生效。审批通过会立即替换密码并使该用户的旧登录会话全部失效。
        </p>
      </div>
      <div class="ledger-date">
        本页待审批 {{ pendingCount }} 条 / 共 {{ total }} 条
      </div>
    </header>

    <div class="page-toolbar">
      <div class="toolbar-filters">
        <el-select
          v-model="status"
          class="status-filter"
          placeholder="申请状态"
          @change="handleStatusChange"
        >
          <el-option
            v-for="option in STATUS_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-button @click="load()">刷新</el-button>
      </div>
    </div>

    <div v-if="requests.length === 0" class="empty-ledger">当前筛选条件下没有密码变更申请。</div>

    <el-table v-else :data="requests" border>
      <el-table-column label="申请人" min-width="160">
        <template #default="{ row }">
          <div class="applicant-name">{{ row.displayName || '未命名' }}</div>
          <div class="applicant-login">{{ row.loginId || '—' }}</div>
        </template>
      </el-table-column>
      <el-table-column label="身份" width="100">
        <template #default="{ row }">{{ roleLabel(row.role) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="申请时间" width="180">
        <template #default="{ row }">{{ formatDateTime(row.requestedAt) }}</template>
      </el-table-column>
      <el-table-column label="审批人" width="140">
        <template #default="{ row }">{{ row.reviewerName || '—' }}</template>
      </el-table-column>
      <el-table-column label="审批时间" width="180">
        <template #default="{ row }">{{ formatDateTime(row.reviewedAt) }}</template>
      </el-table-column>
      <el-table-column label="审批说明" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">{{ row.reviewNote || '—' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="170" fixed="right">
        <template #default="{ row }">
          <template v-if="row.status === 'PENDING'">
            <el-button type="primary" link @click="openReview(row, true)">批准</el-button>
            <el-button type="danger" link @click="openReview(row, false)">驳回</el-button>
          </template>
          <span v-else class="handled-hint">已处理</span>
        </template>
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

    <el-dialog v-model="reviewDialogVisible" :title="reviewApproved ? '批准密码变更' : '驳回密码变更'" width="440px">
      <div v-if="reviewTarget" class="review-summary">
        <div>申请人：{{ reviewTarget.displayName || '未命名' }}（{{ reviewTarget.loginId || '—' }}）</div>
        <div>身份：{{ roleLabel(reviewTarget.role) }}</div>
        <div>申请时间：{{ formatDateTime(reviewTarget.requestedAt) }}</div>
      </div>
      <p class="review-hint">
        {{
          reviewApproved
            ? '批准后新密码立即生效，该用户所有旧登录会话会被强制下线。'
            : '驳回后原密码保持不变，请填写原因便于申请人调整。'
        }}
      </p>
      <el-input
        v-model="reviewNote"
        type="textarea"
        :rows="3"
        maxlength="500"
        placeholder="审批说明（驳回时必填，最多 500 字）"
      />
      <template #footer>
        <el-button @click="reviewDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="confirmReview">确认提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.status-filter {
  width: 160px;
}

.applicant-name {
  font-weight: 700;
}

.applicant-login {
  margin-top: 2px;
  color: var(--ink-soft);
  font-size: 12px;
}

.handled-hint {
  color: var(--ink-soft);
  font-size: 12px;
}

.review-summary {
  display: grid;
  gap: 6px;
  padding: 12px 14px;
  background: var(--paper-deep);
  font-size: 13px;
}

.review-hint {
  margin: 14px 0 10px;
  color: var(--ink-soft);
  font-size: 13px;
  line-height: 1.8;
}
</style>
