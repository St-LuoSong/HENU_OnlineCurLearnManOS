import http from '@/api/http'
import type { ApiEnvelope } from '@/api/types'
import type { CareerAnalysis } from '@/api/career'
import type { Goal, Task } from '@/api/learning'
import type { LearningPlan } from '@/api/learningPlan'

export interface GrowthSummary {
  activeGoals: number
  completedGoals: number
  openTasks: number
  overdueTasks: number
  dueToday: number
  dueThisWeek: number
  activePlans: number
  totalPlanItems: number
  completedPlanItems: number
  careerAnalysisCount: number
  careerConsentGranted: boolean
}

export interface GrowthCalendarEvent {
  type: string
  sourceId?: number
  title: string
  date: string
  status: string
  detail?: string
}

export interface GrowthAdvice {
  code: string
  title: string
  detail: string
  severity: string
}

export interface GrowthWorkspace {
  summary: GrowthSummary
  cycle: GrowthCycle
  cycles: GrowthCycle[]
  direction: GrowthDirection | null
  currentStage: 'DIRECTION' | 'GOALS' | 'PLAN' | 'ACTION' | 'REVIEW'
  currentPlan: LearningPlan | null
  currentReview: GrowthReview | null
  reviews: GrowthReview[]
  goals: Goal[]
  plans: LearningPlan[]
  todayTasks: Task[]
  upcomingTasks: Task[]
  calendarEvents: GrowthCalendarEvent[]
  recentAnalyses: CareerAnalysis[]
  advice: GrowthAdvice[]
}

export interface GrowthCycle {
  id: number
  title: string
  startDate: string
  endDate: string
  status: 'ACTIVE' | 'COMPLETED' | 'ARCHIVED'
  completedAt?: string
  archivedAt?: string
  createdAt: string
}

export interface GrowthDirection {
  cycleId: number
  sourceAnalysisId?: number
  summary: string
  snapshotJson?: string
  updatedAt: string
}

export interface GrowthReview {
  id: number
  cycleId: number
  weekStart: string
  weekEnd: string
  evidenceJson?: string
  note?: string
  updatedAt: string
}

export async function getGrowthWorkspace(cycleId?: number): Promise<GrowthWorkspace> {
  const response = await http.get<ApiEnvelope<GrowthWorkspace>>('/growth/workspace', {
    params: cycleId ? { cycleId } : undefined
  })
  return response.data.data
}

export async function createGrowthCycle(payload: {
  title: string
  startDate: string
  endDate: string
  carryOverGoalIds?: number[]
  carryOverTaskIds?: number[]
}): Promise<GrowthCycle> {
  const response = await http.post<ApiEnvelope<GrowthCycle>>('/growth/cycles', payload)
  return response.data.data
}

export async function completeGrowthCycle(id: number): Promise<GrowthCycle> {
  const response = await http.post<ApiEnvelope<GrowthCycle>>(`/growth/cycles/${id}/complete`)
  return response.data.data
}

export async function archiveGrowthCycle(id: number): Promise<GrowthCycle> {
  const response = await http.post<ApiEnvelope<GrowthCycle>>(`/growth/cycles/${id}/archive`)
  return response.data.data
}

export async function refreshGrowthDirection(id: number): Promise<GrowthDirection> {
  const response = await http.post<ApiEnvelope<GrowthDirection>>(
    `/growth/cycles/${id}/direction/refresh`
  )
  return response.data.data
}

export async function saveGrowthReview(
  cycleId: number,
  weekStart: string,
  note: string
): Promise<GrowthReview> {
  const response = await http.put<ApiEnvelope<GrowthReview>>(
    `/growth/cycles/${cycleId}/reviews/${weekStart}`,
    { note }
  )
  return response.data.data
}
