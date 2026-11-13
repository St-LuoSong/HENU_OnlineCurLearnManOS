import type { RouteMeta } from 'vue-router'
import { describe, expect, it } from 'vitest'

import { homeRoute, resolveNavigation, type GuardAuthState } from '@/router/guard'

const student: GuardAuthState = { isAuthenticated: true, user: { roles: ['STUDENT'] } }
const teacher: GuardAuthState = { isAuthenticated: true, user: { roles: ['TEACHER'] } }
const admin: GuardAuthState = { isAuthenticated: true, user: { roles: ['ADMIN'] } }
const anonymous: GuardAuthState = { isAuthenticated: false, user: null }

function route(meta: RouteMeta, fullPath = '/target', name: string = 'target') {
  return { meta, fullPath, name }
}

describe('路由守卫权限矩阵', () => {
  it('未登录访问需要登录的页面时带 redirect 去登录页', () => {
    expect(
      resolveNavigation(route({ requiresAuth: true }, '/points'), anonymous, true)
    ).toEqual({ name: 'login', query: { redirect: '/points' } })
  })

  it('学生与教师访问管理后台都会被送回学习概览', () => {
    expect(resolveNavigation(route({ requiresAdmin: true }), student, true)).toEqual({
      name: 'dashboard'
    })
    expect(resolveNavigation(route({ requiresAdmin: true }), teacher, true)).toEqual({
      name: 'dashboard'
    })
  })

  it('管理员可进管理后台，且管理员首页是管理后台', () => {
    expect(resolveNavigation(route({ requiresAdmin: true }), admin, true)).toBe(true)
    expect(homeRoute(admin)).toEqual({ name: 'admin-dashboard' })
    expect(homeRoute(student)).toEqual({ name: 'dashboard' })
  })

  it('教师专属页面只对教师放行', () => {
    expect(resolveNavigation(route({ requiresTeacher: true }), teacher, true)).toBe(true)
    expect(resolveNavigation(route({ requiresTeacher: true }), student, true)).toEqual({
      name: 'dashboard'
    })
    expect(resolveNavigation(route({ requiresTeacher: true }), admin, true)).toEqual({
      name: 'admin-dashboard'
    })
  })

  it('未登录访问管理端会去管理员专属登录页', () => {
    expect(resolveNavigation(route({ requiresAdmin: true }), anonymous, true)).toEqual({
      name: 'admin-login'
    })
  })

  it('已登录用户访问管理员登录页会回到自己的首页', () => {
    expect(
      resolveNavigation(route({ adminLogin: true }, '/admin/login', 'admin-login'), admin, true)
    ).toEqual({ name: 'admin-dashboard' })
    expect(
      resolveNavigation(route({ adminLogin: true }, '/admin/login', 'admin-login'), student, true)
    ).toEqual({ name: 'dashboard' })
    expect(
      resolveNavigation(route({ adminLogin: true }, '/admin/login', 'admin-login'), anonymous, true)
    ).toBe(true)
  })

  it('角色限定页面（云班级 / 生涯分析）对管理员不可达', () => {
    const meta: RouteMeta = { roles: ['STUDENT', 'TEACHER'] }
    expect(resolveNavigation(route(meta), student, true)).toBe(true)
    expect(resolveNavigation(route(meta), teacher, true)).toBe(true)
    expect(resolveNavigation(route(meta), admin, true)).toEqual({ name: 'admin-dashboard' })
  })

  it('已登录用户再访问登录页会跳到角色首页', () => {
    expect(resolveNavigation(route({}, '/login', 'login'), student, true)).toEqual({
      name: 'dashboard'
    })
    expect(resolveNavigation(route({}, '/login', 'login'), admin, true)).toEqual({
      name: 'admin-dashboard'
    })
  })

  it('关闭鉴权开关后一律放行', () => {
    expect(resolveNavigation(route({ requiresAuth: true }), anonymous, false)).toBe(true)
    expect(resolveNavigation(route({ requiresAdmin: true }), student, false)).toBe(true)
  })
})
