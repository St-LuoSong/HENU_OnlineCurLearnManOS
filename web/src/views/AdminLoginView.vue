<script setup lang="ts">
import { Lock, Reading, User } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()
const form = reactive({
  username: '',
  password: ''
})
const submitting = ref(false)

async function submit(): Promise<void> {
  if (!form.username.trim() || !form.password) {
    ElMessage.warning('请输入管理员账号和密码')
    return
  }
  submitting.value = true
  try {
    await auth.loginAdmin(form.username.trim(), form.password)
    if (!auth.user?.roles.includes('ADMIN')) {
      auth.logout()
      ElMessage.error('该账号不是管理员账号')
      return
    }
    form.password = ''
    await router.push('/admin/dashboard')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '登录失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="admin-login">
    <section class="admin-login-panel">
      <div class="admin-login-brand">
        <span class="admin-login-mark"><el-icon><Reading /></el-icon></span>
        <span>线上课程学习管理系统 · 管理控制台</span>
      </div>
      <h1 class="admin-login-title">管理员登录</h1>
      <p class="admin-login-copy">
        该入口仅供平台管理员使用，学生与教师请返回普通登录入口。
      </p>
      <el-form label-position="top" @submit.prevent="submit">
        <el-form-item label="管理员账号">
          <el-input
            v-model="form.username"
            :prefix-icon="User"
            autocomplete="username"
            placeholder="请输入管理员账号"
          />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="form.password"
            :prefix-icon="Lock"
            type="password"
            show-password
            autocomplete="current-password"
            placeholder="请输入密码"
            @keyup.enter="submit"
          />
        </el-form-item>
        <el-button
          type="primary"
          native-type="submit"
          :loading="submitting"
          class="admin-login-submit"
        >
          进入管理控制台
        </el-button>
      </el-form>
      <p class="admin-login-footnote">
        学生 / 教师请使用
        <router-link to="/login">普通登录入口</router-link>
        。
      </p>
    </section>
  </div>
</template>

<style scoped>
.admin-login {
  display: grid;
  min-height: 100vh;
  place-items: center;
  padding: 32px;
  background: linear-gradient(180deg, #f8fbff 0%, #eef5ff 100%);
}

.admin-login-panel {
  width: min(430px, 100%);
  padding: 36px 34px;
  background: var(--white);
  border: 1px solid var(--rule);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-float);
}

.admin-login-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--ink-soft);
  font-size: 12px;
  font-weight: 700;
}

.admin-login-mark {
  display: grid;
  width: 38px;
  height: 38px;
  place-items: center;
  color: var(--white);
  font-size: 18px;
  background: linear-gradient(135deg, #5b91ff, #2f66e3);
  border-radius: 10px;
  box-shadow: 0 8px 18px rgba(59, 120, 240, 0.28);
}

.admin-login-title {
  margin: 22px 0 8px;
  font-family: var(--font-display);
  font-size: 28px;
  font-weight: 800;
}

.admin-login-copy {
  margin: 0 0 24px;
  color: var(--ink-soft);
  font-size: 13px;
  line-height: 1.8;
}

.admin-login-submit {
  width: 100%;
  height: 44px;
  margin-top: 8px;
}

.admin-login-footnote {
  margin: 20px 0 0;
  color: var(--ink-soft);
  font-size: 13px;
  text-align: center;
}

.admin-login-footnote a {
  color: var(--primary);
  font-weight: 700;
}
</style>
