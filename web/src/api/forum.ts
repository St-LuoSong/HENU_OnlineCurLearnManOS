import http from '@/api/http'
import type { ApiEnvelope, PageResult } from '@/api/types'

export interface ForumCategory {
  id: number
  code: string
  name: string
  description?: string
}

export interface ForumPostSummary {
  id: number
  categoryId: number
  categoryName?: string
  authorId: number
  authorName: string
  authorRole: string
  authorBadges: string[]
  title: string
  contentPreview: string
  viewCount: number
  likeCount: number
  replyCount: number
  likedByMe: boolean
  canEdit: boolean
  canDelete: boolean
  canReport: boolean
  media: ForumMedia[]
  createdAt: string
}

/** 帖子图片。内容一律通过带 JWT 的 Blob 接口取回，DTO 不暴露物理路径。 */
export interface ForumMedia {
  id: number
  fileId: number
  originalName: string
  mimeType: string
  sizeBytes: number
  sortOrder: number
  canDelete: boolean
}

export type ForumRankingWindow = 'ALL' | 'WEEK' | 'MONTH'
export type ForumRankingMetric = 'view' | 'like' | 'reply'

/** 右侧排行榜：同一时间窗口下按浏览量 / 点赞量 / 评论量各取前 N 条。 */
export interface ForumRankings {
  window: ForumRankingWindow
  mostViewed: ForumPostSummary[]
  mostLiked: ForumPostSummary[]
  mostReplied: ForumPostSummary[]
}

export type ForumMessageType = 'REPLY' | 'ANNOUNCEMENT'

/** 论坛消息由回复和教师公示派生，acknowledged 表示用户是否已了解。 */
export interface ForumMessage {
  type: ForumMessageType
  sourceId: number
  postId?: number | null
  replyId?: number | null
  title: string
  content: string
  authorId?: number | null
  authorName: string
  createdAt: string
  acknowledged: boolean
}

export interface ForumMessageList {
  records: ForumMessage[]
  unreadCount: number
}

export interface ForumMessageAckRequest {
  type: ForumMessageType
  sourceId: number
}

/** 教师重要公示：学生只读，教师只能绑定自己负责的云班级，管理员可发全局。 */
export interface ForumAnnouncement {
  id: number
  authorId: number
  authorName: string
  authorBadges: string[]
  classId?: number | null
  className?: string | null
  subjectId?: number | null
  subjectName?: string | null
  title: string
  content: string
  status: 'PUBLISHED' | 'HIDDEN'
  canEdit: boolean
  canDelete: boolean
  createdAt: string
  updatedAt?: string | null
}

export interface ForumAnnouncementRequest {
  classId?: number | null
  title: string
  content: string
}

export interface ForumReply {
  id: number
  postId: number
  authorId: number
  authorName: string
  authorRole: string
  authorBadges: string[]
  content: string
  canEdit: boolean
  canDelete: boolean
  canReport: boolean
  createdAt: string
}

export interface ForumPostDetail {
  id: number
  categoryId: number
  categoryName?: string
  authorId: number
  authorName: string
  authorRole: string
  authorBadges: string[]
  title: string
  content: string
  viewCount: number
  likeCount: number
  replyCount: number
  likedByMe: boolean
  canEdit: boolean
  canDelete: boolean
  canReport: boolean
  media: ForumMedia[]
  createdAt: string
  updatedAt: string
  replies: ForumReply[]
}

export interface ForumPostSaveRequest {
  categoryId: number
  title: string
  content: string
  /** 图片文件 ID，数组顺序即九宫格展示顺序；为空表示纯文本帖。 */
  mediaFileIds?: number[]
}

export interface ForumReplySaveRequest {
  content: string
}

export interface ForumReport {
  id: number
  targetType: 'POST' | 'REPLY'
  targetId: number
  targetSummary?: string
  reporterId: number
  reporterName?: string
  reason: string
  detail?: string
  status: 'PENDING' | 'RESOLVED' | 'DISMISSED'
  handlerId?: number
  handlerName?: string
  handleAction?: string
  handleNote?: string
  handledAt?: string
  createdAt: string
}

export interface ForumReportRequest {
  targetType: 'POST' | 'REPLY'
  targetId: number
  reason: string
  detail?: string
}

export interface ForumModerationRequest {
  targetType: 'POST' | 'REPLY'
  targetId: number
  action: 'DISMISS' | 'EDIT' | 'HIDE' | 'DELETE' | 'RESTORE'
  note: string
  reportId?: number
  title?: string
  content?: string
}

export interface ForumModerationLog {
  id: number
  targetType: string
  targetId: number
  reportId?: number
  operatorId: number
  operatorName?: string
  action: string
  reason: string
  beforeSummary?: string
  afterSummary?: string
  patrol: boolean
  createdAt: string
}

export async function listForumCategories(): Promise<ForumCategory[]> {
  const response = await http.get<ApiEnvelope<ForumCategory[]>>('/forum/categories')
  return response.data.data
}

export async function listForumRankings(
  window: ForumRankingWindow = 'ALL',
  limit = 5
): Promise<ForumRankings> {
  const response = await http.get<ApiEnvelope<ForumRankings>>('/forum/rankings', {
    params: { window, limit }
  })
  return response.data.data
}

export async function listForumMessages(limit = 6): Promise<ForumMessageList> {
  const response = await http.get<ApiEnvelope<ForumMessageList>>('/forum/messages', {
    params: { limit }
  })
  return response.data.data
}

export async function acknowledgeForumMessage(
  payload: ForumMessageAckRequest
): Promise<void> {
  await http.post('/forum/messages/ack', payload)
}

export async function listForumAnnouncements(limit = 5): Promise<ForumAnnouncement[]> {
  const response = await http.get<ApiEnvelope<ForumAnnouncement[]>>('/forum/announcements', {
    params: { limit }
  })
  return response.data.data
}

export async function createForumAnnouncement(
  payload: ForumAnnouncementRequest
): Promise<ForumAnnouncement> {
  const response = await http.post<ApiEnvelope<ForumAnnouncement>>('/forum/announcements', payload)
  return response.data.data
}

export async function updateForumAnnouncement(
  id: number,
  payload: ForumAnnouncementRequest
): Promise<ForumAnnouncement> {
  const response = await http.put<ApiEnvelope<ForumAnnouncement>>(
    `/forum/announcements/${id}`,
    payload
  )
  return response.data.data
}

export async function deleteForumAnnouncement(id: number): Promise<void> {
  await http.delete(`/forum/announcements/${id}`)
}

export async function listAdminForumAnnouncements(params: {
  page: number
  size: number
  status?: string
}): Promise<PageResult<ForumAnnouncement>> {
  const response = await http.get<ApiEnvelope<PageResult<ForumAnnouncement>>>(
    '/admin/forum/announcements',
    { params }
  )
  return response.data.data
}

export async function updateForumAnnouncementStatus(
  id: number,
  status: 'PUBLISHED' | 'HIDDEN'
): Promise<ForumAnnouncement> {
  const response = await http.put<ApiEnvelope<ForumAnnouncement>>(
    `/admin/forum/announcements/${id}/status`,
    undefined,
    { params: { status } }
  )
  return response.data.data
}

export async function listForumPosts(params: {
  page: number
  size: number
  categoryId?: number
  keyword?: string
}): Promise<PageResult<ForumPostSummary>> {
  const response = await http.get<ApiEnvelope<PageResult<ForumPostSummary>>>('/forum/posts', {
    params
  })
  return response.data.data
}

export async function getForumPost(id: number): Promise<ForumPostDetail> {
  const response = await http.get<ApiEnvelope<ForumPostDetail>>(`/forum/posts/${id}`)
  return response.data.data
}

export async function createForumPost(
  payload: ForumPostSaveRequest
): Promise<ForumPostDetail> {
  const response = await http.post<ApiEnvelope<ForumPostDetail>>('/forum/posts', payload)
  return response.data.data
}

export async function updateForumPost(
  id: number,
  payload: ForumPostSaveRequest
): Promise<ForumPostDetail> {
  const response = await http.put<ApiEnvelope<ForumPostDetail>>(`/forum/posts/${id}`, payload)
  return response.data.data
}

export async function deleteForumPost(id: number): Promise<void> {
  await http.delete(`/forum/posts/${id}`)
}

export async function likeForumPost(id: number): Promise<ForumPostSummary> {
  const response = await http.post<ApiEnvelope<ForumPostSummary>>(`/forum/posts/${id}/like`)
  return response.data.data
}

export async function unlikeForumPost(id: number): Promise<ForumPostSummary> {
  const response = await http.delete<ApiEnvelope<ForumPostSummary>>(`/forum/posts/${id}/like`)
  return response.data.data
}

export async function createForumReply(
  postId: number,
  payload: ForumReplySaveRequest
): Promise<ForumReply> {
  const response = await http.post<ApiEnvelope<ForumReply>>(
    `/forum/posts/${postId}/replies`,
    payload
  )
  return response.data.data
}

export async function updateForumReply(
  replyId: number,
  payload: ForumReplySaveRequest
): Promise<ForumReply> {
  const response = await http.put<ApiEnvelope<ForumReply>>(`/forum/replies/${replyId}`, payload)
  return response.data.data
}

export async function deleteForumReply(replyId: number): Promise<void> {
  await http.delete(`/forum/replies/${replyId}`)
}

export async function reportForumContent(
  payload: ForumReportRequest
): Promise<ForumReport> {
  const response = await http.post<ApiEnvelope<ForumReport>>('/forum/reports', payload)
  return response.data.data
}

export async function listForumReports(params: {
  page: number
  size: number
  status?: string
  targetType?: string
}): Promise<PageResult<ForumReport>> {
  const response = await http.get<ApiEnvelope<PageResult<ForumReport>>>(
    '/admin/forum/reports',
    { params }
  )
  return response.data.data
}

export async function moderateForumContent(
  payload: ForumModerationRequest
): Promise<ForumModerationLog> {
  const response = await http.post<ApiEnvelope<ForumModerationLog>>(
    '/admin/forum/moderate',
    payload
  )
  return response.data.data
}

export async function listForumModerationLogs(params: {
  page: number
  size: number
}): Promise<PageResult<ForumModerationLog>> {
  const response = await http.get<ApiEnvelope<PageResult<ForumModerationLog>>>(
    '/admin/forum/moderation-logs',
    { params }
  )
  return response.data.data
}

