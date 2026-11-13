import http, { aiHttp } from '@/api/http'
import type { ApiEnvelope } from '@/api/types'

export interface CareerProfile {
  id?: number
  consentGranted: boolean
  consentVersion: string
  consentedAt?: string
  revokedAt?: string
  includeLearningRecords: boolean
  includeGoals: boolean
  includeGrades: boolean
  major?: string
  gradeLevel?: string
  weeklyHours?: number | null
  interestTags: string[]
  strengthNotes?: string
  careerGoal?: string
  updatedAt?: string
}

export interface CareerDataPreview {
  consentGranted: boolean
  consentVersion: string
  privacyNotice: string
  includeLearningRecords: boolean
  includeGoals: boolean
  includeGrades: boolean
  learningRecordCount: number
  learningMinutes: number
  masteredPointCount: number
  goalCount: number
  taskCount: number
  completedTaskCount: number
  gradedSubmissionCount: number
  averageScore?: number | null
  subjects: string[]
  goalTitles: string[]
  sources: string[]
}

export interface CareerInsight {
  id: number
  category: string
  title: string
  content?: string
  reason?: string
  targetDate?: string
  convertedGoalId?: number
  convertedTaskId?: number
  score?: number | null
  sortOrder: number
}

export interface CareerComparison {
  analysisId: number
  analysisCreatedAt: string
  learningRecordCountBefore?: number | null
  learningRecordCountAfter: number
  learningMinutesBefore?: number | null
  learningMinutesAfter: number
  masteredPointCountBefore?: number | null
  masteredPointCountAfter: number
  completedTaskCountBefore?: number | null
  completedTaskCountAfter: number
  gradedSubmissionCountBefore?: number | null
  gradedSubmissionCountAfter: number
  averageScoreBefore?: number | null
  averageScoreAfter?: number | null
  notes: string[]
}

export interface CareerInsightConvertResult {
  target: string
  targetId: number
}

export interface CareerAnalysis {
  id: number
  title: string
  summary?: string
  status: string
  providerCode?: string
  model?: string
  dataSources: string[]
  createdAt: string
  insights: CareerInsight[]
}

export interface CareerProfilePayload {
  major?: string
  gradeLevel?: string
  weeklyHours?: number | null
  interestTags: string[]
  strengthNotes?: string
  careerGoal?: string
}

export interface CareerConsentPayload {
  granted: boolean
  includeLearningRecords: boolean
  includeGoals: boolean
  includeGrades: boolean
}

export async function getCareerProfile(): Promise<CareerProfile> {
  const response = await http.get<ApiEnvelope<CareerProfile>>('/career/profile')
  return response.data.data
}

export async function saveCareerProfile(
  payload: CareerProfilePayload
): Promise<CareerProfile> {
  const response = await http.put<ApiEnvelope<CareerProfile>>('/career/profile', payload)
  return response.data.data
}

export async function updateCareerConsent(
  payload: CareerConsentPayload
): Promise<CareerProfile> {
  const response = await http.post<ApiEnvelope<CareerProfile>>('/career/consent', payload)
  return response.data.data
}

export async function getCareerDataPreview(): Promise<CareerDataPreview> {
  const response = await http.get<ApiEnvelope<CareerDataPreview>>('/career/data-preview')
  return response.data.data
}

export async function listCareerAnalyses(): Promise<CareerAnalysis[]> {
  const response = await http.get<ApiEnvelope<CareerAnalysis[]>>('/career/analyses')
  return response.data.data
}

export async function generateCareerAnalysis(focus?: string): Promise<CareerAnalysis> {
  const response = await aiHttp.post<ApiEnvelope<CareerAnalysis>>('/career/analyses', {
    focus: focus?.trim() ? focus.trim() : null
  })
  return response.data.data
}

export async function deleteCareerAnalysis(id: number): Promise<void> {
  await http.delete(`/career/analyses/${id}`)
}

export async function convertCareerInsight(
  insightId: number,
  target: 'TASK' | 'GOAL'
): Promise<CareerInsightConvertResult> {
  const response = await http.post<ApiEnvelope<CareerInsightConvertResult>>(
    `/career/insights/${insightId}/convert`,
    { target }
  )
  return response.data.data
}

export async function getCareerComparison(analysisId: number): Promise<CareerComparison> {
  const response = await http.get<ApiEnvelope<CareerComparison>>(
    `/career/analyses/${analysisId}/comparison`
  )
  return response.data.data
}
