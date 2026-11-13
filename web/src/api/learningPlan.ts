import http, { aiHttp } from '@/api/http'
import type { ApiEnvelope } from '@/api/types'

export interface LearningPlanItem {
  id: number
  title: string
  description?: string
  startDate?: string
  endDate?: string
  resourceId?: number
  knowledgePointId?: number
  goalId?: number
  taskId?: number
  status: string
  sortOrder: number
}

export interface LearningPlan {
  id: number
  cycleId?: number
  title: string
  summary?: string
  status: string
  providerCode?: string
  model?: string
  createdAt: string
  updatedAt: string
  totalItemCount: number
  completedItemCount: number
  progress: number
  items: LearningPlanItem[]
}

export type LearningPlanStatus = 'DRAFT' | 'ACTIVE' | 'COMPLETED' | 'ARCHIVED'

export interface LearningPlanItemPayload {
  title: string
  description?: string
  startDate?: string
  endDate?: string
}

export async function listLearningPlans(): Promise<LearningPlan[]> {
  const response = await http.get<ApiEnvelope<LearningPlan[]>>('/learning-plans')
  return response.data.data
}

export async function getLearningPlan(id: number): Promise<LearningPlan> {
  const response = await http.get<ApiEnvelope<LearningPlan>>(`/learning-plans/${id}`)
  return response.data.data
}

export async function generateLearningPlan(requirement: string): Promise<LearningPlan> {
  const response = await aiHttp.post<ApiEnvelope<LearningPlan>>('/learning-plans/generate', {
    requirement
  })
  return response.data.data
}

export async function deleteLearningPlan(id: number): Promise<void> {
  await http.delete(`/learning-plans/${id}`)
}

export async function updateLearningPlanStatus(
  id: number,
  status: LearningPlanStatus
): Promise<LearningPlan> {
  const response = await http.put<ApiEnvelope<LearningPlan>>(`/learning-plans/${id}/status`, {
    status
  })
  return response.data.data
}

export async function activateLearningPlan(id: number): Promise<LearningPlan> {
  const response = await http.post<ApiEnvelope<LearningPlan>>(`/learning-plans/${id}/activate`)
  return response.data.data
}

export async function updateLearningPlanItemStatus(
  planId: number,
  itemId: number,
  status: 'TODO' | 'DONE'
): Promise<LearningPlan> {
  const response = await http.put<ApiEnvelope<LearningPlan>>(
    `/learning-plans/${planId}/items/${itemId}/status`,
    { status }
  )
  return response.data.data
}

export async function updateLearningPlanItem(
  planId: number,
  itemId: number,
  payload: LearningPlanItemPayload
): Promise<LearningPlan> {
  const response = await http.put<ApiEnvelope<LearningPlan>>(
    `/learning-plans/${planId}/items/${itemId}`,
    payload
  )
  return response.data.data
}

export async function convertLearningPlanItem(
  planId: number,
  itemId: number,
  target: 'TASK' | 'GOAL'
): Promise<LearningPlan> {
  const response = await http.post<ApiEnvelope<LearningPlan>>(
    `/learning-plans/${planId}/items/${itemId}/convert`,
    { target }
  )
  return response.data.data
}

export async function moveLearningPlanItem(
  planId: number,
  itemId: number,
  direction: 'UP' | 'DOWN'
): Promise<LearningPlan> {
  const response = await http.put<ApiEnvelope<LearningPlan>>(
    `/learning-plans/${planId}/items/${itemId}/order`,
    { direction }
  )
  return response.data.data
}
