<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import {
  listAdminForumAnnouncements,
  listForumModerationLogs,
  listForumReports,
  moderateForumContent,
  updateForumAnnouncementStatus,
  type ForumAnnouncement,
  type ForumModerationLog,
  type ForumReport
} from '@/api/forum'

type ModerationAction = 'DISMISS' | 'HIDE' | 'DELETE' | 'RESTORE'

const loading = ref(false)
const saving = ref(false)
const reports = ref<ForumReport[]>([])
const total = ref(0)
const page = ref(1)
const size = 10
const status = ref<string | undefined>('PENDING')
const targetType = ref<string | undefined>()
const logs = ref<ForumModerationLog[]>([])
const editDialogVisible = ref(false)
const activeReport = ref<ForumReport | null>(null)
const editForm = reactive({
  title: '',
  content: '',
  note: ''
})

/** 教师公示治理：管理员可以隐藏明显违规的公示，也可以恢复被隐藏的公示。 */
const announcements = ref<ForumAnnouncement[]>([])
const announcementTotal = ref(0)
const announcementPage = ref(1)
const announcementStatus = ref<string | undefined>()
const announcementBusyId = ref<number | null>(null)

const reasonLabels: Record<string, string> = {
  SPAM: '垃圾信息 / 广告',
  ABUSE: '辱骂或人身攻击',
  WRONG_CONTENT: '内容错误 / 误导',
  ADVERTISING: '推广引流',
  OTHER: '其他'
}

const statusLabels: Record<string, string> = {
  PENDING: '待处理',
  RESOLVED: '已处理',
  DISMISSED: '已驳回'
}

onMounted(async () => {
  await Promise.all([loadReports(), loadLogs(), loadAnnouncements()])
})

async function loadReports(): Promise<void> {
  loading.value = true
  try {
    const result = await listForumReports({
      page: page.value,
      size,
      status: status.value || undefined,
      targetType: targetType.value
    })
    reports.value = result.records
    total.value = result.total
  } catch {
    ElMessage.error('举报列表加载失败')
  } finally {
    loading.value = false
  }
}

async function loadLogs(): Promise<void> {
  try {
    logs.value = (await listForumModerationLogs({ page: 1, size: 10 })).records
  } catch {
    // 治理日志失败不阻塞举报处理
  }
}

function handleFilterChange(): void {
  page.value = 1
  void loadReports()
}

async function loadAnnouncements(): Promise<void> {
  try {
    const result = await listAdminForumAnnouncements({
      page: announcementPage.value,
      size: 10,
      status: announcementStatus.value || undefined
    })
    announcements.value = result.records
    announcementTotal.value = result.total
  } catch {
    ElMessage.error('教师公示加载失败')
  }
}

function handleAnnouncementFilterChange(): void {
  announcementPage.value = 1
  void loadAnnouncements()
}

function handleAnnouncementPageChange(value: number): void {
  announcementPage.value = value
  void loadAnnouncements()
}

async function toggleAnnouncementStatus(item: ForumAnnouncement): Promise<void> {
  const hiding = item.status === 'PUBLISHED'
  try {
    await ElMessageBox.confirm(
      hiding
        ? `隐藏公示“${item.title}”后，普通用户将不再看到它。`
        : `恢复公示“${item.title}”后，它会重新出现在论坛右侧。`,
      hiding ? '隐藏公示' : '恢复公示',
      { type: 'warning' }
    )
    announcementBusyId.value = item.id
    await updateForumAnnouncementStatus(item.id, hiding ? 'HIDDEN' : 'PUBLISHED')
    ElMessage.success(hiding ? '公示已隐藏' : '公示已恢复')
    await loadAnnouncements()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error instanceof Error ? error.message : '公示状态更新失败')
    }
  } finally {
    announcementBusyId.value = null
  }
}

function handlePageChange(value: number): void {
  page.value = value
  void loadReports()
}

async function quickModerate(
  report: ForumReport,
  action: ModerationAction
): Promise<void> {
  const label = actionLabel(action)
  try {
    const { value } = await ElMessageBox.prompt(
      `请填写「${label}」的理由，该理由会写入治理日志与管理员审计流水。`,
      label,
      {
        inputPlaceholder: '例如：内容与课程无关 / 含广告链接',
        inputValidator: (input) =>
          (input && input.trim().length >= 2) || '请至少填写 2 个字的理由'
      }
    )
    await moderate(report, action, value.trim())
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error instanceof Error ? error.message : '处置失败')
    }
  }
}

async function moderate(
  report: ForumReport,
  action: string,
  note: string
): Promise<void> {
  saving.value = true
  try {
    await moderateForumContent({
      targetType: report.targetType,
      targetId: report.targetId,
      action: action as ModerationAction,
      note,
      reportId: report.id
    })
    ElMessage.success('处置完成')
    await Promise.all([loadReports(), loadLogs()])
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '处置失败')
  } finally {
    saving.value = false
  }
}

function openEdit(report: ForumReport): void {
  activeReport.value = report
  editForm.title = ''
  editForm.content = ''
  editForm.note = ''
  editDialogVisible.value = true
}

async function submitEdit(): Promise<void> {
  const report = activeReport.value
  if (!report) {
    return
  }
  if (report.targetType === 'POST' && !editForm.title.trim()) {
    ElMessage.warning('请填写新的标题')
    return
  }
  if (!editForm.content.trim()) {
    ElMessage.warning('请填写新的正文')
    return
  }
  if (editForm.note.trim().length < 2) {
    ElMessage.warning('请填写处置理由')
    return
  }
  saving.value = true
  try {
    await moderateForumContent({
      targetType: report.targetType,
      targetId: report.targetId,
      action: 'EDIT',
      note: editForm.note.trim(),
      reportId: report.id,
      title: report.targetType === 'POST' ? editForm.title.trim() : undefined,
      content: editForm.content.trim()
    })
    ElMessage.success('内容已修正并记录治理日志')
    editDialogVisible.value = false
    await Promise.all([loadReports(), loadLogs()])
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '编辑失败')
  } finally {
    saving.value = false
  }
}

function actionLabel(action: string): string {
  const labels: Record<string, string> = {
    DISMISS: '驳回举报',
    EDIT: '编辑内容',
    HIDE: '隐藏内容',
    DELETE: '删除内容',
    RESTORE: '恢复内容'
  }
  return labels[action] ?? action
}

function asReport(row: unknown): ForumReport {
  return row as ForumReport
}

function asAnnouncement(row: unknown): ForumAnnouncement {
  return row as ForumAnnouncement
}

function formatDateTime(value?: string): string {
  if (!value) {
    return '-'
  }
  return value.replace('T', ' ').slice(0, 16)
}
</script>

<template>
  <div v-loading="loading" class="ledger-page">
    <header class="ledger-header">
      <div>
        <div class="ledger-kicker">内容治理</div>
        <h1 class="ledger-title">论坛举报与处置</h1>
        <p class="ledger-subtitle">
          处理学生与教师的举报。编辑他人内容必须填写理由，处置动作会写入治理日志与管理员审计流水。
        </p>
      </div>
      <div class="ledger-date">待处理 {{ total }}</div>
    </header>

    <div class="page-toolbar">
      <div class="toolbar-filters">
        <el-select v-model="status" clearable placeholder="全部状态" style="width: 150px" @change="handleFilterChange">
          <el-option label="待处理" value="PENDING" />
          <el-option label="已处理" value="RESOLVED" />
          <el-option label="已驳回" value="DISMISSED" />
        </el-select>
        <el-select v-model="targetType" clearable placeholder="全部对象" style="width: 150px" @change="handleFilterChange">
          <el-option label="帖子" value="POST" />
          <el-option label="回复" value="REPLY" />
        </el-select>
        <el-button @click="loadReports">刷新</el-button>
      </div>
    </div>

    <el-table :data="reports" border>
      <el-table-column prop="targetSummary" label="举报对象" min-width="220" show-overflow-tooltip />
      <el-table-column label="类型" width="90">
        <template #default="{ row }">
          {{ row.targetType === 'POST' ? '帖子' : '回复' }}
        </template>
      </el-table-column>
      <el-table-column prop="reporterName" label="举报人" width="120" />
      <el-table-column label="原因" width="150">
        <template #default="{ row }">
          {{ reasonLabels[row.reason] ?? row.reason }}
        </template>
      </el-table-column>
      <el-table-column prop="detail" label="补充说明" min-width="180" show-overflow-tooltip />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          {{ statusLabels[row.status] ?? row.status }}
        </template>
      </el-table-column>
      <el-table-column label="举报时间" width="160">
        <template #default="{ row }">
          {{ formatDateTime(row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="320" fixed="right">
        <template #default="{ row }">
          <template v-if="row.status === 'PENDING'">
            <el-button link type="primary" :disabled="saving" @click="quickModerate(asReport(row), 'DISMISS')">
              驳回
            </el-button>
            <el-button link type="warning" :disabled="saving" @click="quickModerate(asReport(row), 'HIDE')">
              隐藏
            </el-button>
            <el-button link type="primary" :disabled="saving" @click="openEdit(asReport(row))">
              编辑
            </el-button>
            <el-button link type="danger" :disabled="saving" @click="quickModerate(asReport(row), 'DELETE')">
              删除
            </el-button>
          </template>
          <el-button v-else link type="success" :disabled="saving" @click="quickModerate(asReport(row), 'RESTORE')">
            恢复内容
          </el-button>
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

    <h2 class="ledger-section-title">
      最近治理记录
      <small>谁在什么时间、以什么理由处置了什么内容</small>
    </h2>
    <el-table :data="logs" border>
      <el-table-column label="动作" width="110">
        <template #default="{ row }">
          {{ actionLabel(row.action) }}
        </template>
      </el-table-column>
      <el-table-column prop="operatorName" label="管理员" width="120" />
      <el-table-column label="对象" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.beforeSummary || (row.targetType === 'POST' ? '帖子' : '回复') }}
        </template>
      </el-table-column>
      <el-table-column prop="reason" label="处置理由" min-width="220" show-overflow-tooltip />
      <el-table-column label="来源" width="100">
        <template #default="{ row }">
          {{ row.patrol ? '主动巡查' : '举报处理' }}
        </template>
      </el-table-column>
      <el-table-column label="时间" width="160">
        <template #default="{ row }">
          {{ formatDateTime(row.createdAt) }}
        </template>
      </el-table-column>
    </el-table>

    <h2 class="ledger-section-title">
      教师公示治理
      <small>隐藏明显违规的公示，或恢复误判的公示；两次动作都写管理员审计流水</small>
    </h2>
    <div class="page-toolbar">
      <div class="toolbar-filters">
        <el-select
          v-model="announcementStatus"
          clearable
          placeholder="全部状态"
          style="width: 150px"
          @change="handleAnnouncementFilterChange"
        >
          <el-option label="已发布" value="PUBLISHED" />
          <el-option label="已隐藏" value="HIDDEN" />
        </el-select>
        <el-button @click="loadAnnouncements">刷新</el-button>
      </div>
      <div class="ledger-subtitle">共 {{ announcementTotal }} 条公示</div>
    </div>
    <el-table :data="announcements" border>
      <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
      <el-table-column label="范围" width="180">
        <template #default="{ row }">
          {{ row.className || row.subjectName || '全平台' }}
        </template>
      </el-table-column>
      <el-table-column prop="authorName" label="发布教师" width="130" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'PUBLISHED' ? 'success' : 'info'" size="small">
            {{ row.status === 'PUBLISHED' ? '已发布' : '已隐藏' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="发布时间" width="160">
        <template #default="{ row }">
          {{ formatDateTime(row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button
            link
            :type="row.status === 'PUBLISHED' ? 'danger' : 'success'"
            :loading="announcementBusyId === row.id"
            @click="toggleAnnouncementStatus(asAnnouncement(row))"
          >
            {{ row.status === 'PUBLISHED' ? '隐藏' : '恢复' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      v-if="announcementTotal > 10"
      class="ledger-pagination"
      layout="prev, pager, next"
      :current-page="announcementPage"
      :page-size="10"
      :total="announcementTotal"
      @current-change="handleAnnouncementPageChange"
    />

    <el-dialog v-model="editDialogVisible" title="修正被举报内容" width="680px">
      <el-alert
        type="info"
        show-icon
        :closable="false"
        title="修正后会写入治理日志与审计流水，请填写真实理由。"
      />
      <el-form label-width="90px" style="margin-top: 16px">
        <el-form-item v-if="activeReport?.targetType === 'POST'" label="新标题" required>
          <el-input v-model="editForm.title" maxlength="255" />
        </el-form-item>
        <el-form-item label="新正文" required>
          <el-input v-model="editForm.content" type="textarea" :rows="8" maxlength="20000" />
        </el-form-item>
        <el-form-item label="处置理由" required>
          <el-input v-model="editForm.note" type="textarea" :rows="3" maxlength="1000" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitEdit">保存并记录</el-button>
      </template>
    </el-dialog>
  </div>
</template>
