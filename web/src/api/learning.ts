import http, { aiHttp } from '@/api/http'
import type { ApiEnvelope } from '@/api/types'

export interface Goal {
  id: number
  cycleId?: number
  subjectId?: number
  subjectName?: string
  title: string
  description?: string
  targetDate?: string
  priority: string
  status: string
  completedAt?: string
  taskCount: number
  completedTaskCount: number
  progress: number
}

export interface GoalSaveRequest {
  subjectId?: number
  title: string
  description?: string
  targetDate?: string
  priority: string
}

export interface GoalSuggestion {
  title: string
  description?: string
  targetDate?: string
  priority: string
}

export interface Task {
  id: number
  cycleId?: number
  goalId?: number
  goalTitle?: string
  title: string
  description?: string
  category?: string
  dueAt?: string
  priority: string
  status: string
  completedAt?: string
}

export interface TaskSaveRequest {
  goalId?: number
  title: string
  description?: string
  category?: string
  dueAt?: string
  priority: string
}

export interface LearningRecord {
  id: number
  subjectName?: string
  knowledgePointName?: string
  resourceTitle?: string
  recordType: string
  durationMinutes: number
  score?: number
  mastered: boolean
  remark?: string
  recordedAt: string
}

export interface LearningRecordSaveRequest {
  subjectId?: number
  knowledgePointId?: number
  resourceId?: number
  recordType: string
  durationMinutes: number
  score?: number
  mastered: boolean
  remark?: string
  recordedAt?: string
}

export interface Progress {
  id: number
  subjectId: number
  subjectName?: string
  knowledgePointId: number
  knowledgePointName?: string
  currentGrade: string
  attemptCount: number
  correctCount: number
  mastered: boolean
  transferPassed: boolean
  lastActivity: string
}

export interface ProgressUpdateRequest {
  subjectId: number
  knowledgePointId: number
  currentGrade: string
  attemptCount: number
  correctCount: number
  mastered: boolean
  transferPassed: boolean
}

export interface DashboardSummary {
  activeGoals: number
  completedGoals: number
  pendingTasks: number
  dueThisWeek: number
  masteredPoints: number
}

export async function listGoals(): Promise<Goal[]> {
  const response = await http.get<ApiEnvelope<Goal[]>>('/goals')
  return response.data.data
}

export async function createGoal(payload: GoalSaveRequest): Promise<Goal> {
  const response = await http.post<ApiEnvelope<Goal>>('/goals', payload)
  return response.data.data
}

export async function updateGoal(id: number, payload: GoalSaveRequest): Promise<Goal> {
  const response = await http.put<ApiEnvelope<Goal>>(`/goals/${id}`, payload)
  return response.data.data
}

export async function completeGoal(id: number): Promise<Goal> {
  const response = await http.post<ApiEnvelope<Goal>>(`/goals/${id}/complete`)
  return response.data.data
}

export async function deleteGoal(id: number): Promise<void> {
  await http.delete(`/goals/${id}`)
}

export async function generateGoals(requirement: string): Promise<GoalSuggestion[]> {
  const response = await aiHttp.post<ApiEnvelope<GoalSuggestion[]>>('/goals/generate', {
    requirement
  })
  return response.data.data
}

export async function listTasks(params: {
  goalId?: number
  status?: string
}): Promise<Task[]> {
  const response = await http.get<ApiEnvelope<Task[]>>('/tasks', { params })
  return response.data.data
}

export async function createTask(payload: TaskSaveRequest): Promise<Task> {
  const response = await http.post<ApiEnvelope<Task>>('/tasks', payload)
  return response.data.data
}

export async function updateTask(id: number, payload: TaskSaveRequest): Promise<Task> {
  const response = await http.put<ApiEnvelope<Task>>(`/tasks/${id}`, payload)
  return response.data.data
}

export async function completeTask(id: number): Promise<Task> {
  const response = await http.post<ApiEnvelope<Task>>(`/tasks/${id}/complete`)
  return response.data.data
}

export async function reopenTask(id: number): Promise<Task> {
  const response = await http.post<ApiEnvelope<Task>>(`/tasks/${id}/reopen`)
  return response.data.data
}

export async function deleteTask(id: number): Promise<void> {
  await http.delete(`/tasks/${id}`)
}

export async function listLearningRecords(params: {
  from?: string
  to?: string
}): Promise<LearningRecord[]> {
  const response = await http.get<ApiEnvelope<LearningRecord[]>>('/learning-records', { params })
  return response.data.data
}

export async function createLearningRecord(
  payload: LearningRecordSaveRequest
): Promise<LearningRecord> {
  const response = await http.post<ApiEnvelope<LearningRecord>>('/learning-records', payload)
  return response.data.data
}

export async function deleteLearningRecord(id: number): Promise<void> {
  await http.delete(`/learning-records/${id}`)
}

export async function listProgress(): Promise<Progress[]> {
  const response = await http.get<ApiEnvelope<Progress[]>>('/progress')
  return response.data.data
}

export async function updateProgress(payload: ProgressUpdateRequest): Promise<Progress> {
  const response = await http.put<ApiEnvelope<Progress>>('/progress', payload)
  return response.data.data
}

export async function getDashboardSummary(): Promise<DashboardSummary> {
  const response = await http.get<ApiEnvelope<DashboardSummary>>('/dashboard/summary')
  return response.data.data
}
