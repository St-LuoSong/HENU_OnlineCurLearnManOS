import http, { aiHttp } from '@/api/http'
import type { ApiEnvelope } from '@/api/types'

export interface ChatSession {
  id: number
  subjectId?: number
  subjectName?: string
  knowledgePointId?: number
  knowledgePointName?: string
  title: string
  topic?: string
  status: string
  messageCount: number
  lastMessage: string
  updatedAt: string
}

export interface ChatSessionCreateRequest {
  subjectId?: number
  knowledgePointId?: number
  topic?: string
  title?: string
}

export interface ChatMessage {
  id: number
  role: 'user' | 'assistant'
  content: string
  providerCode?: string
  model?: string
  totalTokens?: number
  createdAt: string
}

export async function listChatSessions(): Promise<ChatSession[]> {
  const response = await http.get<ApiEnvelope<ChatSession[]>>('/chat/sessions')
  return response.data.data
}

export async function createChatSession(
  payload: ChatSessionCreateRequest
): Promise<ChatSession> {
  const response = await http.post<ApiEnvelope<ChatSession>>('/chat/sessions', payload)
  return response.data.data
}

export async function listChatMessages(sessionId: number): Promise<ChatMessage[]> {
  const response = await http.get<ApiEnvelope<ChatMessage[]>>(
    `/chat/sessions/${sessionId}/messages`
  )
  return response.data.data
}

export async function sendChatMessage(
  sessionId: number,
  content: string
): Promise<ChatMessage> {
  const response = await aiHttp.post<ApiEnvelope<ChatMessage>>(
    `/chat/sessions/${sessionId}/messages`,
    { content }
  )
  return response.data.data
}

export async function deleteChatSession(sessionId: number): Promise<void> {
  await http.delete(`/chat/sessions/${sessionId}`)
}

export async function cancelChatStream(
  sessionId: number,
  requestId: string
): Promise<boolean> {
  const response = await http.post<ApiEnvelope<boolean>>(
    `/chat/sessions/${sessionId}/streams/${requestId}/cancel`
  )
  return response.data.data
}
