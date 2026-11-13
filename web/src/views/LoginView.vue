<script setup lang="ts">
import {
  Collection,
  Lock,
  Reading,
  School,
  TrendCharts,
  User
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useAuthStore } from '@/stores/auth'

type AuthMode = 'login' | 'register'
type AuthRole = 'STUDENT' | 'TEACHER'
type RegisterRole = 'STUDENT' | 'TEACHER'

interface LoginForm {
  loginId: string
  password: string
}

interface RegisterForm {
  loginId: string
  displayName: string
  email: string
  password: string
  confirmPassword: string
}

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const mode = ref<AuthMode>('login')
const selectedRole = ref<AuthRole>('STUDENT')
const submitting = ref(false)

/**
 * 每个身份持有独立的表单实例：切换身份或切换登录/注册时不会把密码、
 * 学号或工号带入另一个表单，也不会互相覆盖。
 */
const loginForms = reactive<Record<AuthRole, LoginForm>>({
  STUDENT: { loginId: '', password: '' },
  TEACHER: { loginId: '', password: '' }
})

const registerForms = reactive<Record<RegisterRole, RegisterForm>>({
  STUDENT: {
    loginId: '',
    displayName: '',
    email: '',
    password: '',
    confirmPassword: ''
  },
  TEACHER: {
    loginId: '',
    displayName: '',
    email: '',
    password: '',
    confirmPassword: ''
  }
})

const isRegister = computed(() => mode.value === 'register')
const loginForm = computed(() => loginForms[selectedRole.value])
const registerForm = computed<RegisterForm>(
  () => registerForms[selectedRole.value === 'TEACHER' ? 'TEACHER' : 'STUDENT']
)

const idLabel = computed(() => {
  return selectedRole.value === 'TEACHER' ? '工号' : '学号'
})

const idPlaceholder = computed(() => {
  return selectedRole.value === 'TEACHER' ? '请输入工号' : '请输入学号'
})

const loginSubmitText = computed(() => {
  return selectedRole.value === 'TEACHER' ? '进入教师空间' : '进入学习空间'
})

const registerSubmitText = computed(() =>
  selectedRole.value === 'TEACHER' ? '创建教师账号' : '创建学生账号'
)

const registerHint = computed(() =>
  selectedRole.value === 'TEACHER'
    ? '教师账号需要管理员审核通过后才能登录，请填写真实工号与姓名。'
    : '学生使用学号注册，注册后即可直接登录学习。'
)

const termLabel = computed(() => {
  const now = new Date()
  const year = now.getFullYear()
  const month = now.getMonth() + 1
  if (month === 1) {
    return `${year - 1} 秋季学期`
  }
  if (month >= 8) {
    return `${year} 秋季学期`
  }
  return `${year} 春季学期`
})

const features = [
  { title: '课程知识库', icon: Collection },
  { title: '云班级协作', icon: School },
  { title: '成长规划', icon: TrendCharts }
]

function selectRole(next: AuthRole): void {
  selectedRole.value = next
}

function roleLabel(role: AuthRole): string {
  return role === 'TEACHER' ? '教师账号' : '学生账号'
}

function homeRouteForRoles(roles: string[]): string {
  return roles.includes('ADMIN') ? '/admin/dashboard' : '/dashboard'
}

/**
 * redirect 只有在当前角色可访问时才生效：管理员不会被带回学生页面，
 * 学生和教师也不能通过 redirect 进入管理后台。
 */
function resolveRedirect(roles: string[]): string {
  const raw = typeof route.query.redirect === 'string' ? route.query.redirect : ''
  const home = homeRouteForRoles(roles)
  if (!raw || !raw.startsWith('/')) {
    return home
  }
  if (raw.startsWith('/admin')) {
    return roles.includes('ADMIN') ? raw : home
  }
  return roles.includes('ADMIN') ? home : raw
}

async function submitLogin(): Promise<void> {
  const form = loginForm.value
  if (!form.loginId.trim() || !form.password) {
    ElMessage.warning(`请输入${idLabel.value}和密码`)
    return
  }

  submitting.value = true
  try {
    await auth.login(form.loginId.trim(), form.password, selectedRole.value)
    const roles = auth.user?.roles ?? []
    if (!roles.includes(selectedRole.value)) {
      auth.logout()
      ElMessage.error(`该账号不是${roleLabel(selectedRole.value)}，请重新选择身份`)
      return
    }
    form.password = ''
    await router.push(resolveRedirect(roles))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '登录失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}

async function submitRegister(): Promise<void> {
  const form = registerForm.value
  const role: RegisterRole = selectedRole.value === 'TEACHER' ? 'TEACHER' : 'STUDENT'
  if (!form.loginId.trim()) {
    ElMessage.warning(role === 'TEACHER' ? '请输入工号' : '请输入学号')
    return
  }
  if (!form.displayName.trim()) {
    ElMessage.warning('请输入姓名')
    return
  }
  if (form.password.length < 8) {
    ElMessage.warning('密码至少需要 8 位')
    return
  }
  if (form.password !== form.confirmPassword) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }

  submitting.value = true
  try {
    const pendingReview = await auth.register({
      username: form.loginId.trim(),
      password: form.password,
      displayName: form.displayName.trim(),
      email: form.email.trim() || undefined,
      role
    })
    if (pendingReview) {
      auth.logout()
      mode.value = 'login'
      form.password = ''
      form.confirmPassword = ''
      ElMessage.success('教师账号已提交，等待管理员审核通过后即可登录')
      return
    }
    form.password = ''
    form.confirmPassword = ''
    await router.push(resolveRedirect(auth.user?.roles ?? []))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '注册失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <section class="auth-hero">
      <svg
        class="auth-scene"
        viewBox="0 0 760 760"
        preserveAspectRatio="xMidYMax slice"
        aria-hidden="true"
      >
        <g fill="none" stroke="rgba(217, 230, 247, 0.3)" stroke-width="1.2">
          <path d="M96 620V356C96 196 224 68 380 68s284 128 284 288v264" opacity=".55" />
          <path d="M150 620V370c0-128 104-232 230-232s230 104 230 232v250" opacity=".28" />
          <path d="M96 620h568M126 644h508M156 668h448" />
          <path d="M232 620V438c0-82 66-148 148-148s148 66 148 148v182" />
          <path d="M300 620V470c0-44 36-80 80-80s80 36 80 80v150" />
          <path d="M232 470h296M262 520h236M292 570h176" opacity=".6" />
          <path d="M380 68v552" opacity=".22" />
        </g>
        <g fill="none" stroke="rgba(217, 164, 65, 0.52)" stroke-width="1.6">
          <path d="M380 620V150" />
          <path d="M380 128l10 12-10 12-10-12Z" fill="rgba(217, 164, 65, 0.7)" stroke="none" />
        </g>
        <g fill="none" stroke="rgba(217, 230, 247, 0.55)" stroke-width="1.4">
          <path d="M244 620c44-26 90-26 136 0v42c-44-22-90-22-136 0Z" />
          <path d="M380 620c46-26 92-26 136 0v42c-46-22-92-22-136 0Z" />
          <path d="M380 620v42" />
          <path d="M270 646h62M270 658h40M428 646h62M428 658h40" opacity=".55" />
        </g>
        <g fill="rgba(217, 230, 247, 0.45)">
          <circle cx="142" cy="250" r="2" />
          <circle cx="646" cy="206" r="2.4" />
          <circle cx="690" cy="420" r="1.8" />
          <circle cx="110" cy="470" r="1.6" />
          <circle cx="596" cy="540" r="1.6" />
        </g>
      </svg>

      <header class="auth-brand">
        <span class="auth-brand-mark"><el-icon><Reading /></el-icon></span>
        <span class="auth-brand-name">线上课程学习管理系统</span>
      </header>

      <div class="auth-hero-body">
        <p class="auth-term">{{ termLabel }}</p>
        <h1>把每一次理解<br />留在账本里</h1>
        <p class="auth-copy">
          课程资源、云班级与成长规划，让每天的学习都有迹可循。
        </p>
        <ul class="auth-features">
          <li v-for="feature in features" :key="feature.title" class="auth-feature">
            <el-icon><component :is="feature.icon" /></el-icon>
            <span>{{ feature.title }}</span>
          </li>
        </ul>
      </div>

      <footer class="auth-hero-foot">学习，让未来更好</footer>
    </section>

    <section class="auth-panel">
      <div class="auth-panel-top">
        <span>学生与教师入口</span>
        <span>{{ termLabel }}</span>
      </div>

      <div class="auth-form-wrap">
        <header class="auth-card-head">
          <h2>{{ isRegister ? '创建账号' : '欢迎回来' }}</h2>
          <p v-if="isRegister">{{ registerHint }}</p>
          <p v-else>登录后继续你的学习之旅</p>
        </header>

        <div class="auth-role-grid">
          <button
            type="button"
            :class="{ active: selectedRole === 'STUDENT' }"
            @click="selectRole('STUDENT')"
          >
            学生
          </button>
          <button
            type="button"
            :class="{ active: selectedRole === 'TEACHER' }"
            @click="selectRole('TEACHER')"
          >
            教师
          </button>
        </div>

        <div class="auth-mode" role="tablist" aria-label="登录或注册">
          <button
            type="button"
            :class="{ active: mode === 'login' }"
            role="tab"
            :aria-selected="mode === 'login'"
            @click="mode = 'login'"
          >
            登录
          </button>
          <button
            type="button"
            :class="{ active: mode === 'register' }"
            role="tab"
            :aria-selected="mode === 'register'"
            @click="mode = 'register'"
          >
            注册
          </button>
        </div>

        <el-form v-if="!isRegister" label-position="top" @submit.prevent="submitLogin">
          <el-form-item :label="idLabel">
            <el-input
              v-model="loginForm.loginId"
              :prefix-icon="User"
              autocomplete="username"
              :placeholder="idPlaceholder"
            />
          </el-form-item>
          <el-form-item label="密码">
            <el-input
              v-model="loginForm.password"
              :prefix-icon="Lock"
              type="password"
              show-password
              autocomplete="current-password"
              placeholder="请输入密码"
              @keyup.enter="submitLogin"
            />
          </el-form-item>
          <el-button
            type="primary"
            native-type="submit"
            :loading="submitting"
            class="auth-submit"
          >
            {{ loginSubmitText }}
          </el-button>
        </el-form>

        <el-form v-else label-position="top" @submit.prevent="submitRegister">
          <el-form-item :label="idLabel">
            <el-input
              v-model="registerForm.loginId"
              :prefix-icon="User"
              autocomplete="username"
              :placeholder="idPlaceholder"
            />
          </el-form-item>
          <el-form-item label="姓名">
            <el-input
              v-model="registerForm.displayName"
              autocomplete="name"
              placeholder="请输入真实姓名"
            />
          </el-form-item>
          <el-form-item label="邮箱">
            <el-input
              v-model="registerForm.email"
              type="email"
              autocomplete="email"
              placeholder="可选，用于后续通知"
            />
          </el-form-item>
          <div class="auth-password-grid">
            <el-form-item label="密码">
              <el-input
                v-model="registerForm.password"
                :prefix-icon="Lock"
                type="password"
                show-password
                autocomplete="new-password"
                placeholder="至少 8 位"
              />
            </el-form-item>
            <el-form-item label="确认密码">
              <el-input
                v-model="registerForm.confirmPassword"
                :prefix-icon="Lock"
                type="password"
                show-password
                autocomplete="new-password"
                placeholder="再次输入"
              />
            </el-form-item>
          </div>
          <el-button
            type="primary"
            native-type="submit"
            :loading="submitting"
            class="auth-submit"
          >
            {{ registerSubmitText }}
          </el-button>
        </el-form>

        <p class="auth-footnote">
          {{ isRegister ? '已有账号？' : '还没有账号？' }}
          <button
            type="button"
            class="auth-link"
            @click="mode = isRegister ? 'login' : 'register'"
          >
            {{ isRegister ? '去登录' : '立即注册' }}
          </button>
        </p>
      </div>

      <footer class="auth-panel-foot">线上课程学习管理系统</footer>
    </section>
  </div>
</template>

<style scoped>
.auth-page {
  display: grid;
  grid-template-columns: minmax(0, 1.55fr) minmax(440px, 1fr);
  min-height: 100vh;
  background: #fbfcff;
}

.auth-hero {
  position: relative;
  display: flex;
  min-height: 100vh;
  flex-direction: column;
  justify-content: flex-start;
  padding: 36px 52px 30px;
  overflow: hidden;
  color: #ffffff;
  background:
    linear-gradient(
      90deg,
      rgba(9, 22, 40, 0.98) 0%,
      rgba(10, 27, 48, 0.94) 42%,
      rgba(12, 32, 56, 0.62) 100%
    ),
    linear-gradient(160deg, #0b1b31 0%, #102a4c 58%, #0a182b 100%);
}

.auth-hero::before {
  position: absolute;
  inset: 0;
  content: "";
  pointer-events: none;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.035) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.035) 1px, transparent 1px);
  background-size: 48px 48px;
  mask-image: linear-gradient(90deg, transparent 0%, #000 38%, #000 100%);
}

.auth-hero::after {
  position: absolute;
  top: 0;
  right: 0;
  width: 1px;
  height: 100%;
  background: rgba(255, 255, 255, 0.12);
  content: "";
}

.auth-scene {
  position: absolute;
  right: -6%;
  bottom: -4%;
  width: 72%;
  height: 88%;
  pointer-events: none;
  opacity: 0.9;
}

.auth-brand,
.auth-hero-body,
.auth-hero-foot {
  position: relative;
  z-index: 1;
}

.auth-brand {
  display: flex;
  align-items: center;
  gap: 11px;
  color: #ffffff;
  font-family: var(--font-sans);
  font-size: 14px;
  font-weight: 700;
}

.auth-brand-mark {
  display: grid;
  width: 38px;
  height: 38px;
  place-items: center;
  color: #0d1f36;
  font-size: 19px;
  background: #d9a441;
  border-radius: 9px;
}

.auth-hero-body {
  max-width: 640px;
  padding: clamp(48px, 10vh, 120px) 0 40px;
}

.auth-term {
  margin: 0 0 18px;
  color: rgba(217, 164, 65, 0.95);
  font-size: 12px;
  font-weight: 600;
}

.auth-hero-body h1 {
  margin: 0;
  color: #ffffff;
  font-family: "Noto Serif SC", "Songti SC", "SimSun", serif;
  font-size: clamp(42px, 4.2vw, 68px);
  font-weight: 600;
  line-height: 1.18;
}

.auth-copy {
  max-width: 500px;
  margin: 22px 0 0;
  color: rgba(226, 235, 247, 0.74);
  font-size: 15px;
  line-height: 1.9;
}

.auth-features {
  display: flex;
  flex-wrap: wrap;
  gap: 0;
  margin: 34px 0 0;
  padding: 0;
  list-style: none;
}

.auth-feature {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 0 20px;
  color: rgba(226, 235, 247, 0.7);
  font-size: 13px;
  border-left: 1px solid rgba(255, 255, 255, 0.14);
}

.auth-feature:first-child {
  padding-left: 0;
  border-left: none;
}

.auth-feature .el-icon {
  color: rgba(217, 164, 65, 0.92);
  font-size: 15px;
}

.auth-hero-foot {
  margin-top: auto;
  color: rgba(226, 235, 247, 0.5);
  font-size: 12px;
}

.auth-panel {
  display: flex;
  min-height: 100vh;
  flex-direction: column;
  padding: 32px 48px 24px;
  background: #fbfcff;
}

.auth-panel-top {
  display: flex;
  width: min(404px, 100%);
  align-items: center;
  justify-content: space-between;
  margin: 0 auto;
  padding-bottom: 14px;
  color: #6b7a8f;
  font-size: 12px;
  border-bottom: 1px solid #e8eef8;
}

.auth-form-wrap {
  width: min(404px, 100%);
  margin: 0 auto;
  padding: clamp(64px, 13vh, 140px) 0 40px;
}

.auth-card-head {
  margin-bottom: 26px;
}

.auth-card-head h2 {
  margin: 0;
  color: #132f52;
  font-family: var(--font-sans);
  font-size: 30px;
  font-weight: 700;
  line-height: 1.2;
}

.auth-card-head p {
  margin: 10px 0 0;
  color: #6b7a8f;
  font-size: 13px;
  line-height: 1.8;
}

.auth-role-grid {
  display: flex;
  margin-bottom: 24px;
}

.auth-role-grid button {
  position: relative;
  flex: 1;
  padding: 10px 0;
  color: #7b8a9e;
  font-size: 13px;
  background: none;
  border: none;
  cursor: pointer;
}

.auth-role-grid button + button {
  border-left: 1px solid #e8eef8;
}

.auth-role-grid button.active {
  color: #132f52;
  font-weight: 700;
}

.auth-role-grid button.active::after {
  position: absolute;
  bottom: 0;
  left: 50%;
  width: 18px;
  height: 2px;
  background: #d9a441;
  content: "";
  transform: translateX(-50%);
}

.auth-mode {
  display: flex;
  gap: 24px;
  margin-bottom: 22px;
  border-bottom: 1px solid #e8eef8;
}

.auth-mode button {
  padding: 0 0 10px;
  color: #7b8a9e;
  font-size: 13px;
  background: none;
  border: none;
  border-bottom: 2px solid transparent;
  cursor: pointer;
}

.auth-mode button.active {
  color: #132f52;
  font-weight: 700;
  border-bottom-color: #1f55bd;
}

.auth-submit {
  width: 100%;
  height: 46px;
  margin-top: 4px;
  color: #ffffff;
  font-size: 14px;
  background: #1f55bd;
  border-color: #1f55bd;
  border-radius: 8px;
  box-shadow: 0 10px 24px rgba(31, 85, 189, 0.22);
}

.auth-submit:hover {
  background: #173f91;
  border-color: #173f91;
}

.auth-password-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

.auth-footnote {
  margin: 18px 0 0;
  color: #6b7a8f;
  font-size: 12px;
  text-align: left;
}

.auth-link {
  padding: 0;
  color: #1f55bd;
  font: inherit;
  font-weight: 700;
  background: none;
  border: none;
  cursor: pointer;
}

.auth-link:hover {
  text-decoration: underline;
}

.auth-form-wrap :deep(.el-form-item) {
  margin-bottom: 16px;
}

.auth-form-wrap :deep(.el-form-item__label) {
  color: #5b6b80;
  font-size: 12px;
}

.auth-form-wrap :deep(.el-input__wrapper) {
  min-height: 46px;
  background: #ffffff;
  border-radius: 8px;
  box-shadow: 0 0 0 1px #dfe7f3 inset;
}

.auth-form-wrap :deep(.el-input__wrapper.is-focus) {
  box-shadow:
    0 0 0 1px #1f55bd inset,
    0 0 0 4px rgba(31, 85, 189, 0.1);
}

.auth-panel-foot {
  width: min(404px, 100%);
  margin: auto auto 0;
  color: #7b8a9e;
  font-size: 11px;
}

@media (max-width: 1180px) {
  .auth-hero {
    padding: 30px 38px 26px;
  }

  .auth-scene {
    right: -14%;
    width: 84%;
  }

  .auth-panel {
    padding: 28px 34px 22px;
  }
}

@media (max-width: 980px) {
  .auth-page {
    grid-template-columns: 1fr;
  }

  .auth-hero {
    min-height: 380px;
    padding: 26px 28px 24px;
  }

  .auth-hero-body {
    padding: 32px 0 24px;
  }

  .auth-hero-body h1 {
    font-size: 42px;
  }

  .auth-scene {
    right: -18%;
    width: 78%;
    opacity: 0.55;
  }

  .auth-panel {
    min-height: 0;
    padding: 24px 24px 28px;
  }

  .auth-form-wrap {
    padding: 32px 0;
  }

  .auth-panel-top,
  .auth-panel-foot {
    width: min(404px, 100%);
    margin-right: auto;
    margin-left: auto;
  }
}

@media (max-width: 620px) {
  .auth-hero {
    min-height: 320px;
  }

  .auth-hero-body h1 {
    font-size: 34px;
  }

  .auth-copy {
    margin-top: 14px;
    font-size: 14px;
  }

  .auth-features {
    margin-top: 22px;
  }

  .auth-feature {
    padding: 0 12px;
    font-size: 12px;
  }

  .auth-panel {
    padding: 20px 18px 24px;
  }

  .auth-password-grid {
    grid-template-columns: 1fr;
    gap: 0;
  }

  .auth-card-head h2 {
    font-size: 26px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .auth-role-grid button,
  .auth-mode button,
  .auth-link {
    transition: none;
  }
}
</style>
