import http, { aiHttp } from '@/api/http'
import type { ApiEnvelope } from '@/api/types'

export interface PracticeSession {
  id: number
  subjectId: number
  subjectName?: string
  knowledgePointId?: number
  knowledgePointName?: string
  title: string
  state: string
  currentGrade: string
  consecutiveFailures: number
  currentQuestionId?: number
  transferQuestion: boolean
  messageCount: number
  updatedAt: string
}

export interface PracticeMessage {
  id: number
  role: string
  messageType: string
  content: string
  questionId?: number
  createdAt: string
}

export interface PracticeAnswer {
  id: number
  questionId: number
  transfer: boolean
  correct: boolean
  grade: string
  errorType?: string
  errorLabel?: string
  feedback?: string
  answeredAt: string
}

export interface PracticeSessionDetail {
  session: PracticeSession
  messages: PracticeMessage[]
}

export interface PracticeTurn {
  session: PracticeSession
  messages: PracticeMessage[]
  answer?: PracticeAnswer | null
}

export interface PracticeSessionCreatePayload {
  subjectId: number
  knowledgePointId?: number | null
  title?: string
}

export async function listPracticeSessions(): Promise<PracticeSession[]> {
  const response = await http.get<ApiEnvelope<PracticeSession[]>>('/practice/sessions')
  return response.data.data
}

export async function createPracticeSession(
  payload: PracticeSessionCreatePayload
): Promise<PracticeSessionDetail> {
  const response = await http.post<ApiEnvelope<PracticeSessionDetail>>(
    '/practice/sessions',
    payload
  )
  return response.data.data
}

export async function getPracticeSession(id: number): Promise<PracticeSessionDetail> {
  const response = await http.get<ApiEnvelope<PracticeSessionDetail>>(`/practice/sessions/${id}`)
  return response.data.data
}

export async function sendPracticeMessage(id: number, content: string): Promise<PracticeTurn> {
  const response = await aiHttp.post<ApiEnvelope<PracticeTurn>>(
    `/practice/sessions/${id}/messages`,
    { content }
  )
  return response.data.data
}

export async function startPracticeQuestion(id: number): Promise<PracticeTurn> {
  const response = await aiHttp.post<ApiEnvelope<PracticeTurn>>(
    `/practice/sessions/${id}/questions`
  )
  return response.data.data
}

export async function requestPracticeHint(id: number): Promise<PracticeTurn> {
  const response = await aiHttp.post<ApiEnvelope<PracticeTurn>>(`/practice/sessions/${id}/hint`)
  return response.data.data
}

export async function deletePracticeSession(id: number): Promise<void> {
  await http.delete(`/practice/sessions/${id}`)
}
