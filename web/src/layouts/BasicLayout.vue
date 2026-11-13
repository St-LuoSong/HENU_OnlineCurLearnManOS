<script setup lang="ts">
import {
  Collection,
  DataAnalysis,
  EditPen,
  Management,
  Notebook,
  Menu as MenuIcon,
  Reading,
  School,
  Star,
  SwitchButton,
  TrendCharts,
  User,
  UserFilled
} from '@element-plus/icons-vue'
import { computed, onMounted, ref, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useAuthStore } from '@/stores/auth'

interface NavItem {
  index: string
  label: string
  icon: Component
}

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const mobileMenuOpen = ref(false)

const activeMenu = computed(() => {
  if (route.path.startsWith('/admin')) {
    return '/admin'
  }
  if (route.path.startsWith('/forum')) {
    return '/forum'
  }
  if (route.path.startsWith('/resources')) {
    return '/resources'
  }
  return route.path
})
const pageTitle = computed(() => String(route.meta.title ?? '线上课程学习管理系统'))
const isAdmin = computed(() => auth.user?.roles.includes('ADMIN') === true)
const isTeacher = computed(() => auth.user?.roles.includes('TEACHER') === true)
const isChatRoute = computed(() =>
  route.name === 'assistant' || route.name === 'practice-workspace'
)
const roleLabel = computed(() => {
  if (auth.user?.roles.includes('ADMIN')) {
    return '管理员'
  }
  if (auth.user?.roles.includes('TEACHER')) {
    return '教师'
  }
  return '学生'
})
const userInitial = computed(() => (auth.user?.displayName ?? '用').slice(0, 1))

const navItems = computed<NavItem[]>(() => {
  if (isAdmin.value) {
    return [{ index: '/admin', label: '管理控制台', icon: Management }]
  }
  return [
    { index: '/dashboard', label: '学习概览', icon: DataAnalysis },
    { index: '/resources', label: '学习资源', icon: Collection },
    ...(isTeacher.value
      ? [{ index: '/questions', label: '题库管理', icon: EditPen }]
      : []),
    { index: '/assistant', label: 'AI 学习助手', icon: Management },
    { index: '/practice', label: 'AI 练习场', icon: Notebook },
    { index: '/knowledge-bag', label: '知识锦囊', icon: Star },
    { index: '/growth', label: '成长规划', icon: TrendCharts },
    { index: '/forum', label: '学习论坛', icon: User },
    { index: '/cloud-classes', label: '云班级', icon: School },
    ...(isTeacher.value
      ? [{ index: '/teacher/students', label: '学生状态', icon: UserFilled }]
      : []),
    { index: '/profile', label: '个人中心', icon: UserFilled }
  ]
})

onMounted(async () => {
  if (auth.isAuthenticated && !auth.user) {
    try {
      await auth.loadCurrentUser()
    } catch {
      auth.logout()
      await router.push({ name: 'login' })
    }
  }
})

function handleLogout(): void {
  auth.logout()
  router.push({ name: 'login' })
}
</script>

<template>
  <el-container class="app-shell">
    <el-aside width="220px" class="app-sidebar">
      <div class="brand">
        <div class="brand-mark"><el-icon><Reading /></el-icon></div>
        <div>
          <div class="brand-title">线上课程学习</div>
          <div class="brand-subtitle">管理系统</div>
        </div>
      </div>

      <el-menu :default-active="activeMenu" router class="app-menu">
        <el-menu-item
          v-for="item in navItems"
          :key="item.index"
          :index="item.index"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </el-menu-item>
      </el-menu>
      <div class="app-sidebar-foot">
        <div class="sidebar-role">当前身份：<strong>{{ roleLabel }}</strong></div>
        <p class="sidebar-note">学习，让未来更好</p>
      </div>
    </el-aside>

    <el-container>
      <el-header class="app-header">
        <div class="header-leading">
          <el-button
            class="mobile-menu-button"
            text
            :icon="MenuIcon"
            aria-label="打开导航菜单"
            @click="mobileMenuOpen = true"
          />
          <div class="page-title">{{ pageTitle }}</div>
        </div>
        <div class="header-actions">
          <div class="header-user">
            <span class="user-avatar">{{ userInitial }}</span>
            <span class="user-meta">
              <span class="user-name">{{ auth.user?.displayName ?? '当前用户' }}</span>
              <span class="user-role">{{ roleLabel }}</span>
            </span>
          </div>
          <el-button class="header-logout" text :icon="SwitchButton" @click="handleLogout">
            退出
          </el-button>
        </div>
      </el-header>
      <el-main class="app-main" :class="{ 'app-main--flush': isChatRoute }">
        <router-view />
      </el-main>
    </el-container>

    <el-drawer
      v-model="mobileMenuOpen"
      direction="ltr"
      size="260px"
      :with-header="false"
      class="mobile-nav-drawer"
    >
      <div class="brand brand-drawer">
        <div class="brand-mark"><el-icon><Reading /></el-icon></div>
        <div>
          <div class="brand-title">线上课程学习</div>
          <div class="brand-subtitle">管理系统</div>
        </div>
      </div>
      <el-menu
        :default-active="activeMenu"
        router
        class="app-menu"
        @select="mobileMenuOpen = false"
      >
        <el-menu-item
          v-for="item in navItems"
          :key="`mobile-${item.index}`"
          :index="item.index"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </el-menu-item>
      </el-menu>
    </el-drawer>
  </el-container>
</template>
