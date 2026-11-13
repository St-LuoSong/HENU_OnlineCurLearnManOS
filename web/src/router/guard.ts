import type { RouteLocationNormalized, RouteLocationRaw } from 'vue-router'

export interface GuardAuthState {
  isAuthenticated: boolean
  user?: { roles: string[] } | null
}

/**
 * 角色首页：管理员进管理后台，学生与教师进学习概览。
 */
export function homeRoute(auth: GuardAuthState): RouteLocationRaw {
  return auth.user?.roles.includes('ADMIN')
    ? { name: 'admin-dashboard' }
    : { name: 'dashboard' }
}

/**
 * 路由守卫中与浏览器无关的判定逻辑。
 *
 * <p>抽成纯函数是为了在不启动真实路由与组件的情况下做权限矩阵回归：
 * 未登录、学生、教师、管理员四种身份访问管理端 / 教师端 / 角色限定页面时的落点。</p>
 */
export function resolveNavigation(
  to: Pick<RouteLocationNormalized, 'meta' | 'fullPath' | 'name'>,
  auth: GuardAuthState,
  authEnabled: boolean
): true | RouteLocationRaw {
  if (!authEnabled) {
    return true
  }
  // 管理端登录入口：已登录用户不应停留在登录页
  if (to.meta.adminLogin) {
    return auth.isAuthenticated ? homeRoute(auth) : true
  }
  // 管理端页面：未登录去管理员专属入口，非管理员回到自己的首页
  if (to.meta.requiresAdmin) {
    if (!auth.isAuthenticated) {
      return { name: 'admin-login' }
    }
    if (!auth.user?.roles.includes('ADMIN')) {
      return homeRoute(auth)
    }
    return true
  }
  if (to.meta.requiresAuth && !auth.isAuthenticated) {
    return {
      name: 'login',
      query: {
        redirect: to.fullPath
      }
    }
  }
  if (to.meta.requiresTeacher && !auth.user?.roles.includes('TEACHER')) {
    return homeRoute(auth)
  }
  const roles = to.meta.roles as string[] | undefined
  if (roles && !roles.some((role) => auth.user?.roles.includes(role))) {
    return homeRoute(auth)
  }
  if (to.name === 'login' && auth.isAuthenticated) {
    return homeRoute(auth)
  }
  return true
}
