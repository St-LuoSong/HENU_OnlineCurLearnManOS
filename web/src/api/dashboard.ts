import http from '@/api/http'
import type { ApiEnvelope } from '@/api/types'
import type { DashboardSummary } from '@/api/learning'

export interface InteractionBreakdown {
  aiQuestions: number
  practiceAnswers: number
  resourceViews: number
  forumActivities: number
  classSubmissions: number
}

export interface InteractionTrendPoint {
  date: string
  breakdown: InteractionBreakdown
}

export interface InteractionOverview {
  totalThisWeek: number
  todayCount: number
  activeDaysThisWeek: number
  practiceAccuracy: number
  breakdown: InteractionBreakdown
  trend: InteractionTrendPoint[]
}

export interface StudentCourseCard {
  subjectId: number
  subjectName: string
  className?: string
  teacherName?: string
  masteryPercent: number
  masteredPoints: number
  totalPoints: number
  pendingTaskCount: number
  averageScore?: number
  lastActivityAt?: string
  nextAction: string
}

export interface StudentDashboardSummary {
  summary: DashboardSummary
  teacherNames: string[]
  classNames: string[]
  interaction: InteractionOverview
  recentScores: RecentScore[]
  myCourses: StudentCourseCard[]
}

export interface RecentScore {
  sourceType: 'PRACTICE_SET' | 'LEARNING_RECORD' | 'CLASS_TASK'
  sourceId: number
  title: string
  subjectName?: string
  score: number
  maxScore: number
  recordedAt: string
}

export interface TeacherClassSummary {
  classId: number
  className: string
  subjectName?: string
  memberCount: number
  taskCount: number
  pendingSubmissionCount: number
  gradedSubmissionCount: number
  averageScore: number
}

export interface TeacherDashboardSummary {
  classCount: number
  studentCount: number
  pendingSubmissionCount: number
  gradedSubmissionCount: number
  averageScore: number
  classes: TeacherClassSummary[]
  riskStudents: RiskStudent[]
}

export interface RiskStudent {
  classId: number
  className: string
  studentId: number
  displayName: string
  overdueTaskCount: number
  pendingTaskCount: number
  averageScore: number
}

export interface DashboardOverview {
  role: string
  student?: StudentDashboardSummary
  teacher?: TeacherDashboardSummary
}

export async function getDashboardOverview(): Promise<DashboardOverview> {
  const response = await http.get<ApiEnvelope<DashboardOverview>>('/dashboard/overview')
  return response.data.data
}

export type DashboardNoteTone = 'DEFAULT' | 'IMPORTANT' | 'RELAX'

export interface DashboardNote {
  id: number
  noteDate: string
  content: string
  tone: DashboardNoteTone
  updatedAt?: string
}

export interface DashboardNoteSaveRequest {
  noteDate: string
  content: string
  tone: DashboardNoteTone
}

/** 便签按可见月份拉取，区间由前端用 {@link monthRange} 计算，不传默认当月。 */
export async function listDashboardNotes(params: {
  from?: string
  to?: string
} = {}): Promise<DashboardNote[]> {
  const response = await http.get<ApiEnvelope<DashboardNote[]>>('/dashboard/notes', { params })
  return response.data.data
}

export async function createDashboardNote(
  payload: DashboardNoteSaveRequest
): Promise<DashboardNote> {
  const response = await http.post<ApiEnvelope<DashboardNote>>('/dashboard/notes', payload)
  return response.data.data
}

export async function updateDashboardNote(
  id: number,
  payload: DashboardNoteSaveRequest
): Promise<DashboardNote> {
  const response = await http.put<ApiEnvelope<DashboardNote>>(`/dashboard/notes/${id}`, payload)
  return response.data.data
}

export async function deleteDashboardNote(id: number): Promise<void> {
  await http.delete(`/dashboard/notes/${id}`)
}

export interface CalendarEvent {
  type: string
  sourceId?: number
  title: string
  date: string
  status?: string
  detail?: string
}

/** 后端聚合目标、任务、学习计划、生涯行动与云班级任务的日历事件。 */
export async function listCalendarEvents(params: {
  from?: string
  to?: string
} = {}): Promise<CalendarEvent[]> {
  const response = await http.get<ApiEnvelope<CalendarEvent[]>>('/dashboard/calendar', { params })
  return response.data.data
}
