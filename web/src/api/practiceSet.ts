import http from '@/api/http'
import type { ApiEnvelope } from '@/api/types'

export type PracticeQuestionType = 'single_choice' | 'fill_blank' | 'short_answer'

export interface PracticeSetItem {
  id: number
  seq: number
  questionType: PracticeQuestionType
  stem: string
  options: string[]
  studentAnswer?: string
  correct?: boolean
  awardedScore?: number
  maxScore: number
  feedback?: string
  errorType?: string
  correctAnswer?: string
  explanation?: string
}

export interface PracticeSet {
  id: number
  title: string
  subjectId: number
  subjectName?: string
  knowledgePointId?: number
  knowledgePointName?: string
  billingMode: 'STANDARD' | 'EXPERT'
  status: 'IN_PROGRESS' | 'SUBMITTED'
  questionCount: number
  answeredCount: number
  correctCount: number
  score?: number
  durationSeconds?: number
  startedAt: string
  submittedAt?: string
  items: PracticeSetItem[]
}

export interface PracticeSetSummary {
  id: number
  title: string
  subjectId: number
  subjectName?: string
  knowledgePointId?: number
  knowledgePointName?: string
  billingMode: 'STANDARD' | 'EXPERT'
  status: 'IN_PROGRESS' | 'SUBMITTED'
  questionCount: number
  answeredCount: number
  correctCount: number
  score?: number
  durationSeconds?: number
  startedAt: string
  submittedAt?: string
}

export async function listPracticeSets(subjectId?: number): Promise<PracticeSetSummary[]> {
  const response = await http.get<ApiEnvelope<PracticeSetSummary[]>>('/practice/sets', {
    params: subjectId ? { subjectId } : undefined
  })
  return response.data.data
}

export async function createPracticeSet(payload: {
  subjectId: number
  knowledgePointId?: number | null
  mode: 'STANDARD' | 'EXPERT'
  title?: string
}): Promise<PracticeSet> {
  const response = await http.post<ApiEnvelope<PracticeSet>>('/practice/sets', payload)
  return response.data.data
}

export async function getPracticeSet(id: number): Promise<PracticeSet> {
  const response = await http.get<ApiEnvelope<PracticeSet>>(`/practice/sets/${id}`)
  return response.data.data
}

export async function savePracticeAnswers(
  id: number,
  answers: Array<{ itemId: number; answer: string }>
): Promise<PracticeSet> {
  const response = await http.put<ApiEnvelope<PracticeSet>>(`/practice/sets/${id}/answers`, {
    answers
  })
  return response.data.data
}

export async function submitPracticeSet(id: number): Promise<PracticeSet> {
  const response = await http.post<ApiEnvelope<PracticeSet>>(`/practice/sets/${id}/submit`)
  return response.data.data
}
