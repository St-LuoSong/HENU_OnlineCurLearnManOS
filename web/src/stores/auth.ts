import { defineStore } from 'pinia'

import http from '@/api/http'
import type { ApiEnvelope } from '@/api/types'
import { clearAccessToken, getAccessToken, setAccessToken } from '@/utils/token'

export interface UserInfo {
  id: number
  username: string
  displayName: string
  roles: string[]
}

interface LoginPayload {
  accessToken: string | null
  user: UserInfo
  pendingReview?: boolean
}

export interface RegisterPayload {
  username: string
  password: string
  displayName: string
  email?: string
  role: 'STUDENT' | 'TEACHER'
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    accessToken: getAccessToken(),
    user: null as UserInfo | null
  }),
  getters: {
    isAuthenticated: (state) => Boolean(state.accessToken)
  },
  actions: {
    applySession(payload: { accessToken: string; user: UserInfo }): void {
      this.accessToken = payload.accessToken
      this.user = payload.user
      setAccessToken(payload.accessToken)
    },
    async login(
      username: string,
      password: string,
      role?: 'STUDENT' | 'TEACHER'
    ): Promise<void> {
      const response = await http.post<ApiEnvelope<LoginPayload>>('/auth/login', {
        username,
        password,
        role
      })
      const data = response.data.data
      if (!data.accessToken) {
        throw new Error('登录失败：服务端未返回登录凭证')
      }
      this.applySession({ accessToken: data.accessToken, user: data.user })
    },
    /** 管理端专属登录：服务端只接受 ADMIN 账号。 */
    async loginAdmin(username: string, password: string): Promise<void> {
      const response = await http.post<ApiEnvelope<LoginPayload>>(
        '/auth/admin/login',
        { username, password }
      )
      const data = response.data.data
      if (!data.accessToken) {
        throw new Error('登录失败：服务端未返回登录凭证')
      }
      this.applySession({ accessToken: data.accessToken, user: data.user })
    },
    /** 返回值表示账号是否进入待审核状态（教师自助注册）。 */
    async register(payload: RegisterPayload): Promise<boolean> {
      const response = await http.post<ApiEnvelope<LoginPayload>>('/auth/register', payload)
      const data = response.data.data
      if (!data.accessToken) {
        return data.pendingReview === true
      }
      this.applySession({ accessToken: data.accessToken, user: data.user })
      return false
    },
    async loadCurrentUser(): Promise<void> {
      if (!this.accessToken) {
        return
      }
      const response = await http.get<ApiEnvelope<UserInfo>>('/auth/me')
      this.user = response.data.data
    },
    logout(): void {
      const token = this.accessToken
      this.accessToken = null
      this.user = null
      clearAccessToken()
      if (token) {
        // 尽力通知服务端把该 token 加入吊销名单；失败不影响本地登出
        void http
          .post('/auth/logout', undefined, {
            headers: { Authorization: `Bearer ${token}` }
          })
          .catch(() => undefined)
      }
    }
  }
})
