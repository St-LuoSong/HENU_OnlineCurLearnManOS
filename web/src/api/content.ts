import http from '@/api/http'
import type { ApiEnvelope, PageResult } from '@/api/types'

export interface Subject {
  id: number
  code: string
  name: string
  icon?: string
  color?: string
  knowledgePointCount: number
  questionCount: number
}

export interface KnowledgePoint {
  id: number
  subjectId: number
  subjectCode: string
  subjectName: string
  code: string
  name: string
  title: string
  content: string
  sourceType: string
  sourceUrl?: string
  keyPoints: string[]
  commonMistakes: string[]
  intuitionPumps: string[]
}

export interface QuestionOption {
  key: string
  text: string
}

export interface Question {
  id: number
  subjectId: number
  subjectName: string
  knowledgePointId: number
  knowledgePointName: string
  questionType: string
  difficulty: number
  content: string
  correctAnswer: string
  explanation?: string
  transfer: boolean
  options: QuestionOption[]
}

export interface LearningResource {
  id: number
  subjectId: number
  subjectName: string
  title: string
  description?: string
  resourceType: string
  content?: string
  externalUrl?: string
  publisherId?: number
  status: string
  knowledgePointIds: number[]
  knowledgePointNames: string[]
  attachmentCount: number
  primaryAttachment?: ResourceAttachmentSummary | null
  createdAt?: string
  updatedAt?: string
}

export interface ResourceAttachmentSummary {
  fileId: number
  originalName: string
  extension: string
  mimeType: string
  sizeBytes: number
}

export interface LearningResourceSaveRequest {
  subjectId: number
  title: string
  description?: string
  resourceType: string
  content?: string
  externalUrl?: string
  knowledgePointIds: number[]
}

export interface KnowledgePointSaveRequest {
  subjectId: number
  code: string
  name: string
  title: string
  content: string
  sourceType?: string
  sourceUrl?: string
  keyPoints: string[]
  commonMistakes: string[]
  intuitionPumps: string[]
}

export interface QuestionSaveRequest {
  subjectId: number
  knowledgePointId: number
  questionType: string
  difficulty: number
  content: string
  correctAnswer: string
  explanation?: string
  transfer: boolean
  options: QuestionOption[]
}

export async function listSubjects(): Promise<Subject[]> {
  const response = await http.get<ApiEnvelope<Subject[]>>('/subjects')
  return response.data.data
}

export async function listKnowledgePoints(
  subjectId?: number,
  keyword?: string
): Promise<KnowledgePoint[]> {
  const response = await http.get<ApiEnvelope<KnowledgePoint[]>>('/knowledge-points', {
    params: { subjectId, keyword }
  })
  return response.data.data
}

export interface LearningResourceStatusRequest {
  status: 'DRAFT' | 'PUBLISHED'
}

export async function createKnowledgePoint(
  payload: KnowledgePointSaveRequest
): Promise<KnowledgePoint> {
  const response = await http.post<ApiEnvelope<KnowledgePoint>>('/knowledge-points', payload)
  return response.data.data
}

export async function updateKnowledgePoint(
  id: number,
  payload: KnowledgePointSaveRequest
): Promise<KnowledgePoint> {
  const response = await http.put<ApiEnvelope<KnowledgePoint>>(
    `/knowledge-points/${id}`,
    payload
  )
  return response.data.data
}

export async function deleteKnowledgePoint(id: number): Promise<void> {
  await http.delete(`/knowledge-points/${id}`)
}

export async function listQuestions(params: {
  subjectId?: number
  knowledgePointId?: number
  questionType?: string
}): Promise<Question[]> {
  const response = await http.get<ApiEnvelope<Question[]>>('/questions', { params })
  return response.data.data
}

export async function createQuestion(payload: QuestionSaveRequest): Promise<Question> {
  const response = await http.post<ApiEnvelope<Question>>('/questions', payload)
  return response.data.data
}

export async function updateQuestion(id: number, payload: QuestionSaveRequest): Promise<Question> {
  const response = await http.put<ApiEnvelope<Question>>(`/questions/${id}`, payload)
  return response.data.data
}

export async function deleteQuestion(id: number): Promise<void> {
  await http.delete(`/questions/${id}`)
}

export async function listResources(params: {
  page?: number
  size?: number
  subjectId?: number
  knowledgePointId?: number
  keyword?: string
}): Promise<PageResult<LearningResource>> {
  const response = await http.get<ApiEnvelope<PageResult<LearningResource>>>('/resources', {
    params
  })
  return response.data.data
}

export async function getResource(id: number): Promise<LearningResource> {
  const response = await http.get<ApiEnvelope<LearningResource>>(`/resources/${id}`)
  return response.data.data
}

export async function recordResourceView(id: number): Promise<void> {
  await http.post(`/resources/${id}/view`)
}

export async function createResource(
  payload: LearningResourceSaveRequest
): Promise<LearningResource> {
  const response = await http.post<ApiEnvelope<LearningResource>>('/resources', payload)
  return response.data.data
}

export async function updateResource(
  id: number,
  payload: LearningResourceSaveRequest
): Promise<LearningResource> {
  const response = await http.put<ApiEnvelope<LearningResource>>(`/resources/${id}`, payload)
  return response.data.data
}

export async function deleteResource(id: number): Promise<void> {
  await http.delete(`/resources/${id}`)
}

export async function updateResourceStatus(
  id: number,
  status: 'DRAFT' | 'PUBLISHED'
): Promise<LearningResource> {
  const response = await http.put<ApiEnvelope<LearningResource>>(`/resources/${id}/status`, {
    status
  })
  return response.data.data
}
