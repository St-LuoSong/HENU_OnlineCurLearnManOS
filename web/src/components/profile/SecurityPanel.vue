<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { onMounted, reactive, ref } from 'vue'

import {
  cancelPasswordRequest,
  getPendingPasswordRequest,
  submitPasswordRequest,
  type PasswordChangeRequest
} from '@/api/settings'
import { formatDateTime } from '@/utils/date'

const pendingRequest = ref<PasswordChangeRequest | null>(null)
const submitting = ref(false)
const canceling = ref(false)
const form = reactive({
  currentPassword: '',
  newPassword: '',
  confirmPassword: ''
})

onMounted(loadPending)

async function loadPending(): Promise<void> {
  try {
    pendingRequest.value = await getPendingPasswordRequest()
  } catch {
    pendingRequest.value = null
  }
}

async function submit(): Promise<void> {
  if (!form.currentPassword || !form.newPassword) {
    ElMessage.warning('请填写当前密码与新密码')
    return
  }
  if (form.newPassword.length < 8 || !/[A-Za-z]/.test(form.newPassword) || !/\d/.test(form.newPassword)) {
    ElMessage.warning('新密码至少 8 位，并同时包含字母和数字')
    return
  }
  if (form.newPassword === form.currentPassword) {
    ElMessage.warning('新密码不能与当前密码相同')
    return
  }
  if (form.newPassword !== form.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  submitting.value = true
  try {
    pendingRequest.value = await submitPasswordRequest(
      form.currentPassword,
      form.newPassword
    )
    form.currentPassword = ''
    form.newPassword = ''
    form.confirmPassword = ''
    ElMessage.success('申请已提交，等待管理员审批')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '密码变更申请提交失败')
  } finally {
    submitting.value = false
  }
}

async function cancel(): Promise<void> {
  if (!pendingRequest.value) {
    return
  }
  canceling.value = true
  try {
    await cancelPasswordRequest(pendingRequest.value.id)
    pendingRequest.value = null
    ElMessage.success('已撤销密码变更申请')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '撤销申请失败')
  } finally {
    canceling.value = false
  }
}
</script>

<template>
  <section class="center-panel">
    <div class="center-panel-head">
      <div>
        <h2>账号安全</h2>
        <p>密码变更需要管理员审批，审批通过后当前登录会话会失效。</p>
      </div>
      <span class="center-panel-meta">保护账号使用安全</span>
    </div>

    <div v-if="pendingRequest" class="pending-panel">
      <div class="pending-panel-title">已有待审批的密码变更申请</div>
      <div class="pending-panel-meta">提交时间：{{ formatDateTime(pendingRequest.requestedAt) }}</div>
      <p class="security-hint">
        审批通过前当前密码仍然有效。如需更换，请先撤销本次申请。
      </p>
      <el-button :loading="canceling" @click="cancel">撤销申请</el-button>
    </div>

    <template v-else>
      <p class="security-hint">
        新密码需至少 8 位，并同时包含字母和数字。提交后由管理员审批。
      </p>
      <el-form label-width="96px" class="center-form">
        <el-form-item label="当前密码">
          <el-input v-model="form.currentPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="form.newPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="确认新密码">
          <el-input v-model="form.confirmPassword" type="password" show-password />
        </el-form-item>
      </el-form>
      <div class="center-actions">
        <el-button type="primary" :loading="submitting" @click="submit">提交变更申请</el-button>
      </div>
    </template>
  </section>
</template>
