import { createRouter, createWebHistory } from 'vue-router'

import AdminLayout from '@/layouts/AdminLayout.vue'
import BasicLayout from '@/layouts/BasicLayout.vue'
import { resolveNavigation } from '@/router/guard'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: {
        title: '登录'
      }
    },
    {
      path: '/admin/login',
      name: 'admin-login',
      component: () => import('@/views/AdminLoginView.vue'),
      meta: {
        title: '管理员登录',
        adminLogin: true
      }
    },
    {
      path: '/admin',
      component: AdminLayout,
      meta: {
        requiresAuth: true,
        requiresAdmin: true
      },
      children: [
        {
          path: '',
          redirect: '/admin/dashboard'
        },
        {
          path: 'dashboard',
          name: 'admin-dashboard',
          component: () => import('@/views/AdminDashboardView.vue'),
          meta: { title: '管理概览' }
        },
        {
          path: 'users',
          name: 'admin-users',
          component: () => import('@/views/AdminUsersView.vue'),
          meta: { title: '用户管理' }
        },
        {
          path: 'password-requests',
          name: 'admin-password-requests',
          component: () => import('@/views/AdminPasswordRequestsView.vue'),
          meta: { title: '安全审批' }
        },
        {
          path: 'files',
          name: 'admin-files',
          component: () => import('@/views/AdminFilesView.vue'),
          meta: { title: '文件治理' }
        },
        {
          path: 'llm',
          name: 'admin-llm',
          component: () => import('@/views/AdminSettingsView.vue'),
          meta: { title: 'LLM 供应商' }
        },
        {
          path: 'models',
          name: 'admin-models',
          component: () => import('@/views/AdminModelSettingsView.vue'),
          meta: { title: '模型与价格' }
        },
        {
          path: 'resources',
          name: 'admin-resources',
          component: () => import('@/views/ResourceListView.vue'),
          meta: { title: '学习资源维护' }
        },
        {
          path: 'knowledge-points',
          name: 'admin-knowledge-points',
          component: () => import('@/views/AdminKnowledgePointsView.vue'),
          meta: { title: '知识点维护' }
        },
        {
          path: 'points',
          name: 'admin-points',
          component: () => import('@/views/AdminPointAnomaliesView.vue'),
          meta: { title: '积分与 AI 异常' }
        },
        {
          path: 'audit-logs',
          name: 'admin-audit-logs',
          component: () => import('@/views/AdminAuditLogsView.vue'),
          meta: { title: '审计日志' }
        },
        {
          path: 'forum',
          name: 'admin-forum',
          component: () => import('@/views/AdminForumModerationView.vue'),
          meta: { title: '内容治理' }
        },
        {
          path: 'cloud-classes',
          name: 'admin-cloud-classes',
          component: () => import('@/views/AdminCloudClassesView.vue'),
          meta: { title: '云班级总览' }
        }
      ]
    },
    {
      path: '/',
      component: BasicLayout,
      meta: {
        requiresAuth: true
      },
      children: [
        {
          path: '',
          redirect: () =>
            useAuthStore().user?.roles.includes('ADMIN')
              ? '/admin/dashboard'
              : '/dashboard'
        },
        {
          path: 'dashboard',
          name: 'dashboard',
          component: () => import('@/views/DashboardView.vue'),
          meta: {
            title: '学习概览'
          }
        },
        {
          path: 'resources',
          name: 'resources',
          component: () => import('@/views/ResourceListView.vue'),
          meta: {
            title: '学习资源'
          }
        },
        {
          path: 'resources/:id',
          name: 'resource-detail',
          component: () => import('@/views/ResourceDetailView.vue'),
          meta: {
            title: '资源详情'
          }
        },
        {
          path: 'questions',
          name: 'questions',
          component: () => import('@/views/QuestionBankView.vue'),
          meta: {
            title: '题库练习'
          }
        },
        {
          path: 'assistant',
          name: 'assistant',
          component: () => import('@/views/AssistantView.vue'),
          meta: {
            title: 'AI 学习助手',
            roles: ['STUDENT', 'TEACHER']
          }
        },
        {
          path: 'practice',
          name: 'practice-workspace',
          component: () => import('@/views/PracticeWorkspaceView.vue'),
          meta: {
            title: 'AI 练习场',
            roles: ['STUDENT', 'TEACHER']
          }
        },
        {
          path: 'chat',
          redirect: { name: 'assistant' }
        },
        {
          path: 'knowledge-bag',
          name: 'knowledge-bag',
          component: () => import('@/views/KnowledgeBagView.vue'),
          meta: {
            title: '知识锦囊',
            roles: ['STUDENT', 'TEACHER']
          }
        },
        {
          path: 'growth',
          name: 'growth',
          component: () => import('@/views/GrowthView.vue'),
          meta: {
            title: '成长规划',
            roles: ['STUDENT', 'TEACHER']
          }
        },
        {
          path: 'goals',
          name: 'goals',
          redirect: {
            name: 'growth',
            query: { stage: 'goals' }
          }
        },
        {
          path: 'learning-plans',
          name: 'learning-plans',
          redirect: {
            name: 'growth',
            query: { stage: 'plan' }
          }
        },
        {
          path: 'career',
          name: 'career-analysis',
          redirect: {
            name: 'growth',
            query: { stage: 'direction' }
          }
        },
        {
          path: 'forum',
          name: 'forum',
          component: () => import('@/views/ForumView.vue'),
          meta: {
            title: '学习论坛'
          }
        },
        {
          path: 'forum/posts/:id',
          name: 'forum-post-detail',
          component: () => import('@/views/ForumPostDetailView.vue'),
          meta: {
            title: '帖子详情'
          }
        },
        {
          path: 'profile',
          name: 'profile',
          component: () => import('@/views/PersonalCenterView.vue'),
          meta: {
            title: '个人中心'
          }
        },
        {
          path: 'points',
          redirect: { name: 'profile', query: { tab: 'points' } }
        },
        {
          path: 'settings',
          redirect: { name: 'profile', query: { tab: 'profile' } }
        },
        {
          path: 'cloud-classes',
          name: 'cloud-classes',
          component: () => import('@/views/CloudClassView.vue'),
          meta: {
            title: '云班级',
            roles: ['STUDENT', 'TEACHER']
          }
        },
        {
          path: 'teacher/students',
          name: 'teacher-students',
          component: () => import('@/views/TeacherStudentsView.vue'),
          meta: {
            title: '学生状态',
            requiresTeacher: true
          }
        }
      ]
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/views/NotFoundView.vue')
    }
  ]
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  const authEnabled = import.meta.env.VITE_AUTH_ENABLED !== 'false'
  if (authEnabled && auth.isAuthenticated && !auth.user) {
    try {
      await auth.loadCurrentUser()
    } catch {
      auth.logout()
      return { name: 'login' }
    }
  }
  if (authEnabled && to.meta.requiresAuth && !auth.isAuthenticated) {
    return {
      name: 'login',
      query: {
        redirect: to.fullPath
      }
    }
  }
  return resolveNavigation(to, auth, authEnabled)
})

export default router
