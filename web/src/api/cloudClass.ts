import http from '@/api/http'
import type { ApiEnvelope } from '@/api/types'

export interface CloudClass {
  id: number
  subjectId?: number
  subjectName?: string
  name: string
  description?: string
  teacherId: number
  teacherName?: string
  classCode: string
  academicYear?: string
  semester?: string
  maxMembers?: number
  status: string
  joinEnabled: boolean
  memberCount: number
  taskCount: number
  resourceCount: number
  createdAt: string
  myPendingTaskCount: number
  myOverdueTaskCount: number
  myGradedTaskCount: number
  myAverageScore?: number
}

export interface CloudClassMember {
  studentId: number
  loginId: string
  displayName: string
  status: string
  totalTaskCount: number
  submittedTaskCount: number
  pendingTaskCount: number
  overdueTaskCount: number
  gradedCount: number
  averageScore: number
  joinedAt: string
}

export interface CloudClassRosterMember {
  studentId: number
  loginId: string
  displayName: string
  status: string
  joinedAt: string
  totalTaskCount?: number
  submittedTaskCount?: number
  pendingTaskCount?: number
  overdueTaskCount?: number
  gradedCount?: number
  averageScore?: number
}

export interface CloudClassRoster {
  members: CloudClassRosterMember[]
  total: number
  page: number
  size: number
  readOnly: boolean
}

export interface CloudClassResource {
  id: number
  title: string
  resourceType: string
  content?: string
  externalUrl?: string
  publisherName?: string
  createdAt: string
}

export interface CloudClassTask {
  id: number
  title: string
  description?: string
  taskType: string
  knowledgePointId?: number
  knowledgePointName?: string
  dueAt?: string
  rewardPoints: number
  allowResubmit: boolean
  allowLateSubmit: boolean
  maxScore?: number
  status: string
  submissionCount: number
  mySubmissionId?: number
  mySubmissionStatus?: string
  myScore?: number
}

export interface CloudClassDetail {
  classInfo: CloudClass
  members: CloudClassMember[]
  resources: CloudClassResource[]
  tasks: CloudClassTask[]
}

export interface CloudClassSubmission {
  id: number
  taskId: number
  taskTitle?: string
  studentId: number
  studentName?: string
  content?: string
  score?: number
  feedback?: string
  status: string
  submittedAt: string
  gradedAt?: string
}

export interface CloudClassStatistics {
  memberCount: number
  taskCount: number
  submissionCount: number
  gradedCount: number
  averageScore: number
}

export interface TeacherStudent {
  classId: number
  className: string
  studentId: number
  loginId: string
  displayName: string
  status: string
  totalTaskCount: number
  submittedTaskCount: number
  pendingTaskCount: number
  overdueTaskCount: number
  gradedCount: number
  averageScore: number
}

export async function createCloudClass(payload: {
  subjectId: number
  name: string
  description?: string
  academicYear?: string
  semester?: string
  maxMembers?: number
}): Promise<CloudClass> {
  const response = await http.post<ApiEnvelope<CloudClass>>('/cloud-classes', payload)
  return response.data.data
}

export async function joinCloudClass(classCode: string): Promise<CloudClass> {
  const response = await http.post<ApiEnvelope<CloudClass>>('/cloud-classes/join', { classCode })
  return response.data.data
}

export async function previewCloudClass(classCode: string): Promise<CloudClass> {
  const response = await http.get<ApiEnvelope<CloudClass>>('/cloud-classes/preview', {
    params: { classCode }
  })
  return response.data.data
}

export async function listTeachingClasses(): Promise<CloudClass[]> {
  const response = await http.get<ApiEnvelope<CloudClass[]>>('/cloud-classes/teaching')
  return response.data.data
}

export async function listLearningClasses(): Promise<CloudClass[]> {
  const response = await http.get<ApiEnvelope<CloudClass[]>>('/cloud-classes/learning')
  return response.data.data
}

export async function getCloudClassDetail(id: number): Promise<CloudClassDetail> {
  const response = await http.get<ApiEnvelope<CloudClassDetail>>(`/cloud-classes/${id}`)
  return response.data.data
}

export async function getCloudClassRoster(
  classId: number,
  page = 1,
  size = 50
): Promise<CloudClassRoster> {
  const response = await http.get<ApiEnvelope<CloudClassRoster>>(
    `/cloud-classes/${classId}/roster`,
    { params: { page, size } }
  )
  return response.data.data
}

export async function publishCloudResource(
  classId: number,
  payload: {
    title: string
    resourceType: string
    content?: string
    externalUrl?: string
  }
): Promise<CloudClassResource> {
  const response = await http.post<ApiEnvelope<CloudClassResource>>(
    `/cloud-classes/${classId}/resources`,
    payload
  )
  return response.data.data
}

export async function publishCloudTask(
  classId: number,
  payload: {
    title: string
    description?: string
    taskType: string
    dueAt?: string
    rewardPoints: number
    allowResubmit?: boolean
    allowLateSubmit?: boolean
    maxScore?: number
    knowledgePointId?: number
    status?: 'DRAFT' | 'PUBLISHED'
  }
): Promise<CloudClassTask> {
  const response = await http.post<ApiEnvelope<CloudClassTask>>(
    `/cloud-classes/${classId}/tasks`,
    payload
  )
  return response.data.data
}

export async function submitCloudTask(
  taskId: number,
  content: string
): Promise<CloudClassSubmission> {
  const response = await http.post<ApiEnvelope<CloudClassSubmission>>(
    `/cloud-classes/tasks/${taskId}/submit`,
    { content }
  )
  return response.data.data
}

export async function listTaskSubmissions(taskId: number): Promise<CloudClassSubmission[]> {
  const response = await http.get<ApiEnvelope<CloudClassSubmission[]>>(
    `/cloud-classes/tasks/${taskId}/submissions`
  )
  return response.data.data
}

export async function gradeSubmission(
  submissionId: number,
  payload: { score: number; feedback?: string }
): Promise<CloudClassSubmission> {
  const response = await http.put<ApiEnvelope<CloudClassSubmission>>(
    `/cloud-classes/submissions/${submissionId}/grade`,
    payload
  )
  return response.data.data
}

export async function getCloudClassStatistics(id: number): Promise<CloudClassStatistics> {
  const response = await http.get<ApiEnvelope<CloudClassStatistics>>(
    `/cloud-classes/${id}/statistics`
  )
  return response.data.data
}

export async function listTeacherStudents(): Promise<TeacherStudent[]> {
  const response = await http.get<ApiEnvelope<TeacherStudent[]>>(
    '/cloud-classes/teaching/students'
  )
  return response.data.data
}

export async function updateClassMemberStatus(
  classId: number,
  studentId: number,
  status: 'ACTIVE' | 'SUSPENDED' | 'REMOVED'
): Promise<CloudClassMember> {
  const response = await http.put<ApiEnvelope<CloudClassMember>>(
    `/cloud-classes/${classId}/members/${studentId}/status`,
    { status }
  )
  return response.data.data
}

export async function updateCloudClassStatus(
  classId: number,
  status: 'ACTIVE' | 'ARCHIVED' | 'CLOSED'
): Promise<CloudClass> {
  const response = await http.put<ApiEnvelope<CloudClass>>(
    `/cloud-classes/${classId}/status`,
    { status }
  )
  return response.data.data
}

export async function updateCloudClassJoinEnabled(
  classId: number,
  enabled: boolean
): Promise<CloudClass> {
  const response = await http.put<ApiEnvelope<CloudClass>>(
    `/cloud-classes/${classId}/join-enabled`,
    { enabled }
  )
  return response.data.data
}

export async function updateCloudTaskStatus(
  taskId: number,
  status: 'DRAFT' | 'PUBLISHED' | 'CLOSED' | 'ARCHIVED'
): Promise<CloudClassTask> {
  const response = await http.put<ApiEnvelope<CloudClassTask>>(
    `/cloud-classes/tasks/${taskId}/status`,
    { status }
  )
  return response.data.data
}

export async function listAllCloudClasses(params?: {
  subjectId?: number
  teacherId?: number
  status?: string
  semester?: string
  keyword?: string
}): Promise<CloudClass[]> {
  const response = await http.get<ApiEnvelope<CloudClass[]>>('/admin/cloud-classes', { params })
  return response.data.data
}

export async function adminUpdateCloudClassStatus(
  classId: number,
  status: 'ACTIVE' | 'ARCHIVED' | 'CLOSED'
): Promise<CloudClass> {
  const response = await http.put<ApiEnvelope<CloudClass>>(
    `/admin/cloud-classes/${classId}/status`,
    { status }
  )
  return response.data.data
}
