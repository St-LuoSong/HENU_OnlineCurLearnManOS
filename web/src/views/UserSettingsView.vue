<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'

import {
  cancelPasswordRequest,
  getPendingPasswordRequest,
  getUserAiPreference,
  getUserProfile,
  submitPasswordRequest,
  updateUserAiPreference,
  updateUserProfile,
  type PasswordChangeRequest,
  type UserAiPreference,
  type UserProfile,
  type UserProfileUpdatePayload
} from '@/api/settings'
import type { LlmModelConfig } from '@/api/admin'
import { formatDateTime as formatDateTimeText } from '@/utils/date'

type SettingsTab = 'profile' | 'ai' | 'security' | 'notify'

const loading = ref(true)
const activeTab = ref<SettingsTab>('profile')
const savingProfile = ref(false)
const savingNotify = ref(false)
const savingPreference = ref(false)
const submittingPassword = ref(false)
const cancelingPassword = ref(false)

const profile = ref<UserProfile | null>(null)
const pendingRequest = ref<PasswordChangeRequest | null>(null)
const preference = ref<UserAiPreference>({ availableModels: [] })
const selectedModelId = ref<number | undefined>()

const profileForm = reactive<UserProfileUpdatePayload>({
  displayName: '',
  email: '',
  avatarUrl: '',
  department: '',
  major: '',
  gradeLevel: '',
  phone: '',
  bio: '',
  teacherTitle: '',
  researchArea: '',
  notifyEmail: true,
  notifyForumReply: true
})

const passwordForm = reactive({
  currentPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const isTeacher = computed(() => profile.value?.role === 'TEACHER')

const selectedModel = computed(() =>
  preference.value.availableModels.find((model) => model.id === selectedModelId.value)
)

const profileStatusText = computed(() =>
  profile.value?.updatedAt
    ? `最近更新：${formatDateTime(profile.value.updatedAt)}`
    : '尚未完善个人资料'
)

onMounted(async () => {
  await Promise.all([loadProfile(), loadPreference(), loadPendingRequest()])
  loading.value = false
})

async function loadProfile(): Promise<void> {
  try {
    applyProfile(await getUserProfile())
  } catch (error) {
    ElMessage.error(messageOf(error, '个人资料加载失败'))
  }
}

async function loadPreference(): Promise<void> {
  try {
    preference.value = await getUserAiPreference()
    selectedModelId.value = preference.value.selectedModelId
  } catch {
    ElMessage.error('AI 模型设置加载失败')
  }
}

async function loadPendingRequest(): Promise<void> {
  try {
    pendingRequest.value = await getPendingPasswordRequest()
  } catch {
    pendingRequest.value = null
  }
}

function applyProfile(value: UserProfile): void {
  profile.value = value
  profileForm.displayName = value.displayName ?? ''
  profileForm.email = value.email ?? ''
  profileForm.avatarUrl = value.avatarUrl ?? ''
  profileForm.department = value.department ?? ''
  profileForm.major = value.major ?? ''
  profileForm.gradeLevel = value.gradeLevel ?? ''
  profileForm.phone = value.phone ?? ''
  profileForm.bio = value.bio ?? ''
  profileForm.teacherTitle = value.teacherTitle ?? ''
  profileForm.researchArea = value.researchArea ?? ''
  profileForm.notifyEmail = value.notifyEmail !== false
  profileForm.notifyForumReply = value.notifyForumReply !== false
}

async function saveProfile(): Promise<void> {
  if (!profileForm.displayName.trim()) {
    ElMessage.warning('姓名不能为空')
    return
  }
  savingProfile.value = true
  try {
    applyProfile(await updateUserProfile({ ...profileForm }))
    ElMessage.success('基本资料已保存')
  } catch (error) {
    ElMessage.error(messageOf(error, '基本资料保存失败'))
  } finally {
    savingProfile.value = false
  }
}

async function saveNotify(): Promise<void> {
  savingNotify.value = true
  try {
    applyProfile(await updateUserProfile({ ...profileForm }))
    ElMessage.success('通知与隐私设置已保存')
  } catch (error) {
    ElMessage.error(messageOf(error, '通知设置保存失败'))
  } finally {
    savingNotify.value = false
  }
}

function selectModel(modelId: number): void {
  selectedModelId.value = modelId
}

async function savePreference(): Promise<void> {
  if (!selectedModelId.value) {
    ElMessage.warning('请选择一个模型')
    return
  }
  savingPreference.value = true
  try {
    preference.value = await updateUserAiPreference(selectedModelId.value)
    selectedModelId.value = preference.value.selectedModelId
    ElMessage.success('模型设置已保存')
  } catch (error) {
    ElMessage.error(messageOf(error, '模型设置保存失败'))
  } finally {
    savingPreference.value = false
  }
}

async function submitPasswordChange(): Promise<void> {
  if (!passwordForm.currentPassword || !passwordForm.newPassword) {
    ElMessage.warning('请填写当前密码与新密码')
    return
  }
  if (passwordForm.newPassword.length < 8) {
    ElMessage.warning('新密码长度不能少于 8 位')
    return
  }
  if (!/[A-Za-z]/.test(passwordForm.newPassword) || !/\d/.test(passwordForm.newPassword)) {
    ElMessage.warning('新密码需同时包含字母和数字')
    return
  }
  if (passwordForm.newPassword === passwordForm.currentPassword) {
    ElMessage.warning('新密码不能与当前密码相同')
    return
  }
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  submittingPassword.value = true
  try {
    pendingRequest.value = await submitPasswordRequest(
      passwordForm.currentPassword,
      passwordForm.newPassword
    )
    passwordForm.currentPassword = ''
    passwordForm.newPassword = ''
    passwordForm.confirmPassword = ''
    ElMessage.success('申请已提交，等待管理员审批')
  } catch (error) {
    ElMessage.error(messageOf(error, '密码变更申请提交失败'))
  } finally {
    submittingPassword.value = false
  }
}

async function cancelPendingRequest(): Promise<void> {
  if (!pendingRequest.value) {
    return
  }
  cancelingPassword.value = true
  try {
    await cancelPasswordRequest(pendingRequest.value.id)
    pendingRequest.value = null
    ElMessage.success('已撤销密码变更申请')
  } catch (error) {
    ElMessage.error(messageOf(error, '撤销申请失败'))
  } finally {
    cancelingPassword.value = false
  }
}

function providerLabel(model: LlmModelConfig): string {
  return model.providerName ?? model.providerCode
}

function formatDateTime(value?: string | null): string {
  return formatDateTimeText(value, '未记录')
}

function messageOf(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback
}
</script>

<template>
  <div v-loading="loading" class="ledger-page">
    <header class="ledger-header">
      <div>
        <div class="ledger-kicker">个人中心</div>
        <h1 class="ledger-title">{{ profile?.displayName || '我的账号' }}</h1>
        <p class="ledger-subtitle">
          在这里维护个人资料、AI 模型偏好、账号安全与通知设置。登录标识由学校统一分配，如需变更请联系管理员。
        </p>
      </div>
      <div class="ledger-date">{{ profileStatusText }}</div>
    </header>

    <el-tabs v-model="activeTab" class="center-tabs">
      <el-tab-pane label="基本资料" name="profile">
        <section class="ledger-section">
          <h2 class="ledger-section-title">
            基本资料
            <small>带 * 的字段为必填</small>
          </h2>
          <div class="profile-identity">
            <span class="identity-label">登录标识</span>
            <span class="identity-value">{{ profile?.loginId || '—' }}</span>
            <span class="identity-hint">学号 / 工号只读，不可自行修改</span>
          </div>
          <el-form label-width="96px" class="center-form">
            <el-form-item label="姓名">
              <el-input v-model="profileForm.displayName" maxlength="64" placeholder="请输入姓名" />
            </el-form-item>
            <el-form-item label="邮箱">
              <el-input v-model="profileForm.email" maxlength="128" placeholder="用于接收通知" />
            </el-form-item>
            <el-form-item label="头像地址">
              <el-input v-model="profileForm.avatarUrl" maxlength="512" placeholder="可选，图片链接" />
            </el-form-item>
            <el-form-item label="院系">
              <el-input v-model="profileForm.department" maxlength="128" placeholder="例如：计算机学院" />
            </el-form-item>
            <el-form-item label="专业">
              <el-input v-model="profileForm.major" maxlength="128" placeholder="例如：软件工程" />
            </el-form-item>
            <el-form-item v-if="!isTeacher" label="年级">
              <el-input v-model="profileForm.gradeLevel" maxlength="32" placeholder="例如：2024 级" />
            </el-form-item>
            <el-form-item v-if="isTeacher" label="职称">
              <el-input v-model="profileForm.teacherTitle" maxlength="64" placeholder="例如：副教授" />
            </el-form-item>
            <el-form-item v-if="isTeacher" label="研究方向">
              <el-input v-model="profileForm.researchArea" maxlength="255" placeholder="例如：分布式系统" />
            </el-form-item>
            <el-form-item label="手机号">
              <el-input v-model="profileForm.phone" maxlength="32" placeholder="可选" />
            </el-form-item>
            <el-form-item label="个人简介">
              <el-input
                v-model="profileForm.bio"
                type="textarea"
                :rows="3"
                maxlength="500"
                placeholder="简单介绍你的学习或教学方向"
              />
            </el-form-item>
          </el-form>
          <div class="center-actions">
            <el-button type="primary" :loading="savingProfile" @click="saveProfile()">
              保存基本资料
            </el-button>
          </div>
        </section>
      </el-tab-pane>

      <el-tab-pane label="AI 与模型" name="ai">
        <div class="ledger-grid">
          <section class="ledger-section">
            <h2 class="ledger-section-title">
              可用模型
              <small>偏好仅在对应分级内生效</small>
            </h2>
            <div
              v-for="model in preference.availableModels"
              :key="model.id"
              class="model-option"
              :class="{ active: model.id === selectedModelId }"
              @click="selectModel(model.id)"
            >
              <div>
                <div class="model-option-title">{{ model.displayName }}</div>
                <div class="model-option-meta">
                  {{ providerLabel(model) }} · {{ model.modelCode }} · {{ model.tier === 'EXPERT' ? '专家' : '普通' }}
                </div>
              </div>
              <div class="model-option-cost">
                {{ model.tier === 'EXPERT' ? `${model.pointCost} 分 / 次` : '普通模式免费' }}
              </div>
            </div>
            <div v-if="preference.availableModels.length === 0" class="empty-ledger">
              管理员尚未配置可用模型，请稍后再试。
            </div>
          </section>

          <aside class="ledger-section">
            <h2 class="ledger-section-title">
              当前选择
              <small>保存后立即生效</small>
            </h2>
            <div v-if="selectedModel" class="model-summary">
              <div class="model-summary-name">{{ selectedModel.displayName }}</div>
              <div class="model-summary-provider">{{ providerLabel(selectedModel) }}</div>
              <div class="model-summary-price">
                {{ selectedModel.tier === 'EXPERT'
                  ? `专家模式 ${selectedModel.pointCost} 积分 / 次调用`
                  : '普通模式不扣积分' }}
              </div>
              <el-button type="primary" :loading="savingPreference" @click="savePreference">
                保存模型设置
              </el-button>
            </div>
            <div v-else class="empty-ledger">请选择一个可用模型。</div>
          </aside>
        </div>
      </el-tab-pane>

      <el-tab-pane label="账号安全" name="security">
        <section class="ledger-section">
          <h2 class="ledger-section-title">
            修改密码
            <small>需管理员审批后生效</small>
          </h2>

          <div v-if="pendingRequest" class="pending-panel">
            <div class="pending-panel-title">已有待审批的密码变更申请</div>
            <div class="pending-panel-meta">
              提交时间：{{ formatDateTime(pendingRequest.requestedAt) }}
            </div>
            <p class="pending-panel-note">
              为避免账号被冒用，审批通过前当前密码仍然有效。如需更换，请先撤销本次申请。
            </p>
            <el-button :loading="cancelingPassword" @click="cancelPendingRequest">
              撤销申请
            </el-button>
          </div>

          <template v-else>
            <p class="security-hint">
              新密码需至少 8 位并同时包含字母和数字。提交后由管理员审批，审批通过后当前登录会话会自动失效，需要用新密码重新登录。
            </p>
            <el-form label-width="96px" class="center-form">
              <el-form-item label="当前密码">
                <el-input
                  v-model="passwordForm.currentPassword"
                  type="password"
                  show-password
                  autocomplete="current-password"
                  placeholder="请输入当前密码"
                />
              </el-form-item>
              <el-form-item label="新密码">
                <el-input
                  v-model="passwordForm.newPassword"
                  type="password"
                  show-password
                  autocomplete="new-password"
                  placeholder="至少 8 位，含字母和数字"
                />
              </el-form-item>
              <el-form-item label="确认新密码">
                <el-input
                  v-model="passwordForm.confirmPassword"
                  type="password"
                  show-password
                  autocomplete="new-password"
                  placeholder="请再次输入新密码"
                />
              </el-form-item>
            </el-form>
            <div class="center-actions">
              <el-button type="primary" :loading="submittingPassword" @click="submitPasswordChange">
                提交变更申请
              </el-button>
            </div>
          </template>
        </section>
      </el-tab-pane>

      <el-tab-pane label="通知与隐私" name="notify">
        <section class="ledger-section">
          <h2 class="ledger-section-title">
            通知设置
            <small>仅影响你自己的提醒</small>
          </h2>
          <div class="switch-line">
            <div>
              <div class="switch-title">邮件通知</div>
              <div class="switch-desc">接收学习任务、审批结果等重要提醒的邮件</div>
            </div>
            <el-switch v-model="profileForm.notifyEmail" />
          </div>
          <div class="switch-line">
            <div>
              <div class="switch-title">论坛回复提醒</div>
              <div class="switch-desc">有人回复我的帖子时在站内提醒我</div>
            </div>
            <el-switch v-model="profileForm.notifyForumReply" />
          </div>
          <div class="switch-line">
            <div>
              <div class="switch-title">个人资料可见范围</div>
              <div class="switch-desc">
                姓名、院系与专业对本平台教师和学生可见；手机号与邮箱仅自己与管理员可见
              </div>
            </div>
            <el-tag type="info">平台内可见</el-tag>
          </div>
          <div class="center-actions">
            <el-button type="primary" :loading="savingNotify" @click="saveNotify">
              保存通知设置
            </el-button>
          </div>
        </section>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.center-tabs {
  margin-top: 6px;
}

.profile-identity {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: baseline;
  padding: 14px 16px;
  margin-bottom: 18px;
  background: var(--paper-deep);
  border-left: 3px solid var(--jade);
}

.identity-label {
  color: var(--ink-soft);
  font-size: 13px;
  letter-spacing: 0.08em;
}

.identity-value {
  font-family: var(--font-display);
  font-size: 20px;
  font-weight: 700;
}

.identity-hint {
  color: var(--ink-soft);
  font-size: 12px;
}

.center-form {
  max-width: 640px;
}

.center-actions {
  display: flex;
  justify-content: flex-end;
  padding-top: 8px;
}

.security-hint {
  max-width: 720px;
  color: var(--ink-soft);
  font-size: 13px;
  line-height: 1.9;
}

.pending-panel {
  display: grid;
  gap: 8px;
  justify-items: start;
  max-width: 720px;
  padding: 18px 20px;
  background: var(--paper-deep);
  border-left: 3px solid var(--cinnabar);
}

.pending-panel-title {
  font-family: var(--font-display);
  font-size: 18px;
  font-weight: 700;
}

.pending-panel-meta {
  color: var(--ink-soft);
  font-size: 13px;
}

.pending-panel-note {
  margin: 0 0 6px;
  color: var(--ink-soft);
  font-size: 13px;
  line-height: 1.9;
}

.switch-line {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 16px;
  align-items: center;
  padding: 16px 0;
  border-bottom: 1px solid var(--rule);
}

.switch-title {
  font-weight: 700;
}

.switch-desc {
  margin-top: 4px;
  color: var(--ink-soft);
  font-size: 13px;
  line-height: 1.7;
}

.model-option {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 16px;
  align-items: center;
  padding: 16px 14px;
  border-bottom: 1px solid var(--rule);
  cursor: pointer;
}

.model-option:hover,
.model-option.active {
  background: var(--paper-deep);
  border-left: 3px solid var(--jade);
}

.model-option-title {
  font-family: var(--font-display);
  font-size: 18px;
  font-weight: 700;
}

.model-option-meta {
  margin-top: 5px;
  color: var(--ink-soft);
  font-size: 13px;
}

.model-option-cost {
  color: var(--cinnabar);
  font-family: var(--font-display);
  font-size: 17px;
  font-weight: 700;
}

.model-summary {
  display: grid;
  gap: 10px;
  padding: 22px 0;
}

.model-summary-name {
  font-family: var(--font-display);
  font-size: 25px;
  font-weight: 700;
}

.model-summary-provider {
  color: var(--ink-soft);
}

.model-summary-price {
  color: var(--cinnabar);
  font-size: 16px;
  font-weight: 700;
}
</style>
