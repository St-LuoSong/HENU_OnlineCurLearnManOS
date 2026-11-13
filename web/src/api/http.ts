import axios, { AxiosError, type AxiosInstance } from 'axios'

import { clearAccessToken, getAccessToken } from '@/utils/token'

export interface ApiErrorPayload {
  code?: string
  message?: string
  traceId?: string
}

const BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api/v1'
const DEFAULT_TIMEOUT_MS = 15000
const AI_TIMEOUT_MS = Number(import.meta.env.VITE_AI_TIMEOUT_MS ?? '75000')

function createClient(timeout: number): AxiosInstance {
  const client: AxiosInstance = axios.create({
    baseURL: BASE_URL,
    timeout,
    headers: {
      'Content-Type': 'application/json'
    }
  })

  client.interceptors.request.use((config) => {
    const accessToken = getAccessToken()
    if (accessToken) {
      config.headers.Authorization = `Bearer ${accessToken}`
    }
    return config
  })

  client.interceptors.response.use(
    (response) => {
      const payload = response.data as { code?: string; message?: string } | undefined
      if (payload?.code && payload.code !== 'OK') {
        return Promise.reject(new Error(payload.message || payload.code))
      }
      return response
    },
    (error: AxiosError<ApiErrorPayload>) => {
      if (error.response?.status === 401) {
        clearAccessToken()
        if (window.location.pathname !== '/login') {
          window.location.assign('/login')
        }
      }
      const message = error.response?.data?.message ?? error.message
      return Promise.reject(new Error(message))
    }
  )

  return client
}

/**
 * 结构化 AI 生成类请求必须比后端 LLM 超时（默认 60 秒）更晚放弃。
 * 否则前端先报失败、后端仍会完成调用并扣费，用户重试就会重复消费。
 */
export const aiHttp: AxiosInstance = createClient(
  Number.isFinite(AI_TIMEOUT_MS) && AI_TIMEOUT_MS > DEFAULT_TIMEOUT_MS
    ? AI_TIMEOUT_MS
    : 75000
)

const http: AxiosInstance = createClient(DEFAULT_TIMEOUT_MS)

export default http
