import http from '@/api/http'
import type { ApiEnvelope, PageResult } from '@/api/types'
import type { LlmModelConfig } from '@/api/admin'

export interface UserAiPreference {
  selectedModelId?: number
  selectedModel?: LlmModelConfig
  availableModels: LlmModelConfig[]
}

export async function getUserAiPreference(): Promise<UserAiPreference> {
  const response = await http.get<ApiEnvelope<UserAiPreference>>('/settings/ai')
  return response.data.data
}

export async function updateUserAiPreference(modelId: number): Promise<UserAiPreference> {
  const response = await http.put<ApiEnvelope<UserAiPreference>>('/settings/ai', { modelId })
  return response.data.data
}

/** 学生 / 教师个人资料。登录标识（学号 / 工号）由后端下发，普通用户只读。 */
export interface UserProfile {
  userId: number
  loginId: string
  displayName: string
  email?: string | null
  role: string
  roles: string[]
  avatarUrl?: string | null
  department?: string | null
  major?: string | null
  gradeLevel?: string | null
  phone?: string | null
  bio?: string | null
  teacherTitle?: string | null
  researchArea?: string | null
  notifyEmail: boolean
  notifyForumReply: boolean
  updatedAt?: string | null
}

/** 可提交字段：登录标识、角色、审计字段一律不由前端决定。 */
export type UserProfileUpdatePayload = Pick<
  UserProfile,
  | 'displayName'
  | 'email'
  | 'avatarUrl'
  | 'department'
  | 'major'
  | 'gradeLevel'
  | 'phone'
  | 'bio'
  | 'teacherTitle'
  | 'researchArea'
  | 'notifyEmail'
  | 'notifyForumReply'
>

export type PasswordRequestStatus =
  | 'PENDING'
  | 'APPROVED'
  | 'REJECTED'
  | 'CANCELLED'

/** 审批视图只包含申请人、状态与说明，永远不携带任何密码内容。 */
export interface PasswordChangeRequest {
  id: number
  userId: number
  loginId?: string | null
  displayName?: string | null
  role?: string | null
  status: PasswordRequestStatus
  requestedAt?: string | null
  reviewerId?: number | null
  reviewerName?: string | null
  reviewedAt?: string | null
  reviewNote?: string | null
}

export async function getUserProfile(): Promise<UserProfile> {
  const response = await http.get<ApiEnvelope<UserProfile>>('/settings/profile')
  return response.data.data
}

export async function updateUserProfile(
  payload: UserProfileUpdatePayload
): Promise<UserProfile> {
  const response = await http.put<ApiEnvelope<UserProfile>>('/settings/profile', payload)
  return response.data.data
}

/** 当前待审批的密码变更申请，没有则返回 null。 */
export async function getPendingPasswordRequest(): Promise<PasswordChangeRequest | null> {
  const response =
    await http.get<ApiEnvelope<PasswordChangeRequest | null>>('/settings/password-request')
  return response.data.data ?? null
}

export async function submitPasswordRequest(
  currentPassword: string,
  newPassword: string
): Promise<PasswordChangeRequest> {
  const response = await http.post<ApiEnvelope<PasswordChangeRequest>>(
    '/settings/password-request',
    { currentPassword, newPassword }
  )
  return response.data.data
}

export async function cancelPasswordRequest(requestId: number): Promise<void> {
  await http.delete<ApiEnvelope<null>>(`/settings/password-request/${requestId}`)
}

export async function listPasswordRequests(params: {
  page?: number
  size?: number
  status?: string
}): Promise<PageResult<PasswordChangeRequest>> {
  const response = await http.get<ApiEnvelope<PageResult<PasswordChangeRequest>>>(
    '/admin/password-requests',
    { params }
  )
  return response.data.data
}

export async function reviewPasswordRequest(
  requestId: number,
  approved: boolean,
  note?: string
): Promise<PasswordChangeRequest> {
  const response = await http.put<ApiEnvelope<PasswordChangeRequest>>(
    `/admin/password-requests/${requestId}/review`,
    { approved, note: note ?? '' }
  )
  return response.data.data
}
