import http from '@/api/http'
import type { ApiEnvelope, PageResult } from '@/api/types'

export interface AdminUser {
  id: number
  loginId: string
  displayName: string
  email?: string
  enabled: boolean
  status: number
  roles: string[]
  createdAt: string
}

export interface AdminDashboardSummary {
  totalUsers: number
  studentUsers: number
  teacherUsers: number
  pendingTeachers: number
  activeCloudClasses: number
  llmCallsToday: number
  llmFailuresToday: number
  reservedPointTransactions: number
  configuredProviders: number
  enabledModels: number
}

export interface PointAnomalyReservation {
  transactionId: number
  userId: number
  type: string
  amount: number
  idempotencyKey: string
  remark?: string
  createdAt: string
}

export interface PointAnomalyCall {
  callRecordId: number
  userId: number
  providerCode: string
  model: string
  status: string
  errorMessage?: string
  idempotencyKey: string
  createdAt: string
}

export interface PointAnomalySummary {
  staleReservations: PointAnomalyReservation[]
  staleRunningCalls: PointAnomalyCall[]
  succeededButUnconfirmed: PointAnomalyCall[]
  failedButNotRefunded: PointAnomalyCall[]
  scannedCallCount: number
}

export async function getPointAnomalies(): Promise<PointAnomalySummary> {
  const response = await http.get<ApiEnvelope<PointAnomalySummary>>('/admin/points/anomalies')
  return response.data.data
}

export interface AdminAuditLog {
  id: number
  operatorId: number
  operatorName: string
  action: string
  targetType: string
  targetId?: string
  detail?: string
  createdAt: string
}

export async function listAuditLogs(params: {
  page: number
  size: number
}): Promise<PageResult<AdminAuditLog>> {
  const response = await http.get<ApiEnvelope<PageResult<AdminAuditLog>>>(
    '/admin/audit-logs',
    { params }
  )
  return response.data.data
}

export async function getAdminDashboardSummary(): Promise<AdminDashboardSummary> {
  const response = await http.get<ApiEnvelope<AdminDashboardSummary>>('/admin/dashboard/summary')
  return response.data.data
}

export interface LlmProviderConfig {
  id: number
  code: string
  adapterCode: string
  name: string
  baseUrl: string
  defaultModel: string
  enabled: boolean
  apiKeyConfigured: boolean
  updatedAt?: string
  callsToday: number
  failuresToday: number
  lastErrorMessage?: string
  affectedUserCount: number
}

export interface LlmProviderUpdateRequest {
  baseUrl: string
  defaultModel: string
  apiKey?: string
  enabled: boolean
}

export interface LlmProviderTestResponse {
  success: boolean
  providerCode: string
  model: string
  content: string
}

export interface LlmModelConfig {
  id: number
  providerId: number
  providerCode: string
  providerName: string
  modelCode: string
  displayName: string
  tier: 'STANDARD' | 'EXPERT'
  pointCost: number
  enabled: boolean
  updatedAt?: string
}

export interface LlmModelUpdateRequest {
  displayName: string
  tier: 'STANDARD' | 'EXPERT'
  pointCost: number
  enabled: boolean
}

export async function listLlmProviders(): Promise<LlmProviderConfig[]> {
  const response = await http.get<ApiEnvelope<LlmProviderConfig[]>>('/admin/llm/providers')
  return response.data.data
}

export async function updateLlmProvider(
  id: number,
  payload: LlmProviderUpdateRequest
): Promise<LlmProviderConfig> {
  const response = await http.put<ApiEnvelope<LlmProviderConfig>>(
    `/admin/llm/providers/${id}`,
    payload
  )
  return response.data.data
}

export async function testLlmProvider(
  id: number,
  message = '请回复：连接成功'
): Promise<LlmProviderTestResponse> {
  const response = await http.post<ApiEnvelope<LlmProviderTestResponse>>(
    `/admin/llm/providers/${id}/test`,
    { message }
  )
  return response.data.data
}

export async function listAdminUsers(params: {
  page: number
  size: number
  role?: string
  enabled?: boolean
  keyword?: string
}): Promise<PageResult<AdminUser>> {
  const response = await http.get<ApiEnvelope<PageResult<AdminUser>>>('/admin/users', { params })
  return response.data.data
}

export async function updateAdminUserStatus(
  id: number,
  enabled: boolean
): Promise<AdminUser> {
  const response = await http.put<ApiEnvelope<AdminUser>>(`/admin/users/${id}/status`, {
    enabled
  })
  return response.data.data
}

export interface AdminUserDeleteRequest {
  reason: string
  confirmLoginId: string
}

export async function deleteAdminUser(
  id: number,
  payload: AdminUserDeleteRequest
): Promise<void> {
  await http.delete(`/admin/users/${id}`, { data: payload })
}

export async function listAdminModels(): Promise<LlmModelConfig[]> {
  const response = await http.get<ApiEnvelope<LlmModelConfig[]>>('/admin/llm/models')
  return response.data.data
}

export async function updateAdminModel(
  id: number,
  payload: LlmModelUpdateRequest
): Promise<LlmModelConfig> {
  const response = await http.put<ApiEnvelope<LlmModelConfig>>(
    `/admin/llm/models/${id}`,
    payload
  )
  return response.data.data
}
