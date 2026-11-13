import http from '@/api/http'
import type { ApiEnvelope, PageResult } from '@/api/types'

export interface KnowledgeFavorite {
  id: number
  messageId: number
  sessionId: number
  subjectId?: number
  subjectName?: string
  knowledgePointId?: number
  knowledgePointName?: string
  question?: string
  answer: string
  note?: string
  createdAt: string
}

export async function listFavorites(params: {
  page?: number
  size?: number
  subjectId?: number
  keyword?: string
} = {}): Promise<PageResult<KnowledgeFavorite>> {
  const response = await http.get<ApiEnvelope<PageResult<KnowledgeFavorite>>>('/knowledge-favorites', {
    params
  })
  return response.data.data
}

export async function listFavoriteMessageIds(): Promise<number[]> {
  const response = await http.get<ApiEnvelope<number[]>>('/knowledge-favorites/ids')
  return response.data.data
}

export async function addFavorite(
  messageId: number,
  note?: string
): Promise<KnowledgeFavorite> {
  const response = await http.post<ApiEnvelope<KnowledgeFavorite>>('/knowledge-favorites', {
    messageId,
    note
  })
  return response.data.data
}

export async function updateFavoriteNote(
  id: number,
  note: string
): Promise<KnowledgeFavorite> {
  const response = await http.put<ApiEnvelope<KnowledgeFavorite>>(
    `/knowledge-favorites/${id}/note`,
    { note }
  )
  return response.data.data
}

export async function deleteFavorite(id: number): Promise<void> {
  await http.delete(`/knowledge-favorites/${id}`)
}

export async function deleteFavoriteByMessage(messageId: number): Promise<void> {
  await http.delete(`/knowledge-favorites/messages/${messageId}`)
}
