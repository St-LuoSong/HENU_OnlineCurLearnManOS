<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Delete } from '@element-plus/icons-vue'

import {
  deleteAdminUser,
  listAdminUsers,
  updateAdminUserStatus,
  type AdminUser
} from '@/api/admin'

const loading = ref(false)
const users = ref<AdminUser[]>([])
const total = ref(0)
const page = ref(1)
const size = 10
const role = ref<string | undefined>()
const enabled = ref<boolean | undefined>()
const keyword = ref('')
const deleteDialogVisible = ref(false)
const deleteTarget = ref<AdminUser | null>(null)
const deleteReason = ref('')
const deleteConfirmLoginId = ref('')
const deleteSubmitting = ref(false)

onMounted(() => {
  void load()
})

watch(role, () => {
  page.value = 1
  void load()
})

watch(enabled, () => {
  page.value = 1
  void load()
})

async function load(): Promise<void> {
  loading.value = true
  try {
    const result = await listAdminUsers({
      page: page.value,
      size,
      role: role.value,
      enabled: enabled.value,
      keyword: keyword.value.trim() || undefined
    })
    users.value = result.records
    total.value = result.total
  } catch {
    ElMessage.error('用户列表加载失败')
  } finally {
    loading.value = false
  }
}

async function changeStatus(user: AdminUser, nextEnabled: boolean): Promise<void> {
  try {
    const updated = await updateAdminUserStatus(user.id, nextEnabled)
    const index = users.value.findIndex((item) => item.id === user.id)
    if (index >= 0) {
      users.value[index] = updated
    }
    ElMessage.success(nextEnabled ? '账号已启用' : '账号已停用')
  } catch {
    ElMessage.error('账号状态更新失败')
    await load()
  }
}

function handleStatusChange(row: unknown, value: unknown): void {
  void changeStatus(row as AdminUser, Boolean(value))
}

function openDeleteDialog(value: unknown): void {
  deleteTarget.value = value as AdminUser
  deleteReason.value = ''
  deleteConfirmLoginId.value = ''
  deleteDialogVisible.value = true
}

async function submitDelete(): Promise<void> {
  const target = deleteTarget.value
  if (!target || deleteSubmitting.value) {
    return
  }
  const reason = deleteReason.value.trim()
  const confirmLoginId = deleteConfirmLoginId.value.trim()
  if (!reason) {
    ElMessage.warning('请填写删除原因')
    return
  }
  if (confirmLoginId !== target.loginId) {
    ElMessage.warning('请输入完整的学号 / 工号确认删除')
    return
  }

  deleteSubmitting.value = true
  try {
    await deleteAdminUser(target.id, { reason, confirmLoginId })
    ElMessage.success('账号已删除，历史数据已保留')
    deleteDialogVisible.value = false
    users.value = users.value.filter((item) => item.id !== target.id)
    total.value = Math.max(0, total.value - 1)
    if (users.value.length === 0 && page.value > 1) {
      page.value -= 1
      await load()
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '账号删除失败')
  } finally {
    deleteSubmitting.value = false
  }
}

function handlePageChange(value: number): void {
  page.value = value
  void load()
}

function formatDateTime(value: string): string {
  return value.replace('T', ' ').slice(0, 16)
}
</script>

<template>
  <div class="ledger-page">
    <header class="ledger-header">
      <div>
        <div class="ledger-kicker">管理后台</div>
        <h1 class="ledger-title">用户管理</h1>
        <p class="ledger-subtitle">检查学生、教师和管理员账号的角色、联系方式与启用状态。</p>
      </div>
      <div class="ledger-date">共 {{ total }} 个账号</div>
    </header>

    <div class="page-toolbar">
      <div class="toolbar-filters">
        <el-select v-model="role" clearable placeholder="全部角色" style="width: 150px">
          <el-option label="学生" value="STUDENT" />
          <el-option label="教师" value="TEACHER" />
          <el-option label="管理员" value="ADMIN" />
        </el-select>
        <el-select v-model="enabled" clearable placeholder="全部状态" style="width: 150px">
          <el-option label="正常" :value="true" />
          <el-option label="已停用" :value="false" />
        </el-select>
        <el-input
          v-model="keyword"
          clearable
          placeholder="搜索学号/工号或姓名"
          style="width: 220px"
          @keyup.enter="load()"
        />
        <el-button @click="load">搜索</el-button>
      </div>
    </div>

    <div v-loading="loading" class="users-table-wrap">
      <el-table v-if="users.length > 0" :data="users" border>
      <el-table-column prop="loginId" label="学号 / 工号" min-width="140" />
      <el-table-column prop="displayName" label="姓名" min-width="120" />
      <el-table-column prop="email" label="邮箱" min-width="180" />
      <el-table-column label="角色" min-width="140">
        <template #default="{ row }">
          <el-tag v-for="item in row.roles" :key="item" class="tag-gap">{{ item }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag v-if="row.status === 2" type="warning">待审核</el-tag>
          <el-tag v-else :type="row.enabled ? 'success' : 'info'">
            {{ row.enabled ? '正常' : '已停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-switch
            class="status-switch"
            :model-value="row.enabled"
            inline-prompt
            :active-text="row.status === 2 ? '通过' : '启用'"
            inactive-text="停用"
            @change="handleStatusChange(row, $event)"
          />
          <el-button
            class="delete-user-button"
            type="danger"
            link
            :icon="Delete"
            @click="openDeleteDialog(row)"
          >
            删除账号
          </el-button>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="170">
        <template #default="{ row }">
          {{ formatDateTime(row.createdAt) }}
        </template>
      </el-table-column>
      </el-table>
      <el-empty v-else description="暂无匹配账号" />
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

    <el-dialog
      v-model="deleteDialogVisible"
      title="删除账号"
      width="560px"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
    >
      <div v-if="deleteTarget" class="delete-account-dialog">
        <el-alert
          type="warning"
          :closable="false"
          show-icon
          title="删除后该账号将立即无法登录，旧登录凭证同时失效；帖子、作业、积分与审计记录会继续保留。"
        />
        <el-descriptions :column="1" border class="delete-account-summary">
          <el-descriptions-item label="学号 / 工号">
            {{ deleteTarget.loginId }}
          </el-descriptions-item>
          <el-descriptions-item label="姓名">
            {{ deleteTarget.displayName }}
          </el-descriptions-item>
          <el-descriptions-item label="角色">
            {{ deleteTarget.roles.join('、') }}
          </el-descriptions-item>
        </el-descriptions>
        <el-form label-position="top">
          <el-form-item label="删除原因" required>
            <el-input
              v-model="deleteReason"
              type="textarea"
              :rows="3"
              maxlength="500"
              show-word-limit
              placeholder="请说明删除原因，该内容会写入审计日志"
            />
          </el-form-item>
          <el-form-item
            :label="`请输入该账号的学号 / 工号“${deleteTarget.loginId}”进行确认`"
            required
          >
            <el-input
              v-model="deleteConfirmLoginId"
              :placeholder="deleteTarget.loginId"
              autocomplete="off"
            />
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="deleteDialogVisible = false">取消</el-button>
        <el-button type="danger" :loading="deleteSubmitting" @click="submitDelete">
          确认删除
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.delete-account-dialog {
  display: grid;
  gap: 18px;
}

.users-table-wrap {
  min-height: 220px;
}

.delete-account-summary {
  margin-top: 2px;
}

.status-switch {
  margin-right: 12px;
}

.delete-user-button {
  padding: 0;
}
</style>
