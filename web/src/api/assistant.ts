import http from '@/api/http'
import type { ApiEnvelope } from '@/api/types'

export type AssistantFilter = 'all' | 'chat' | 'practice'
export type AssistantSourceType = 'CHAT' | 'PRACTICE'

export interface AssistantSession {
  id: number
  sourceType: AssistantSourceType
  sessionMode: 'chat' | 'practice'
  subjectId?: number
  subjectName?: string
  knowledgePointId?: number
  knowledgePointName?: string
  title: string
  topic?: string
  state?: string
  currentGrade?: string
  messageCount: number
  preview?: string
  transferQuestion: boolean
  currentQuestionId?: number
  updatedAt: string
}

export interface AssistantMessage {
  id: number
  role: 'user' | 'assistant'
  content: string
  createdAt: string
  reasoning?: string
  failed?: boolean
  /** 流式回答的实时阶段：连接中 / 生成中；历史消息为空。 */
  streamPhase?: 'connecting' | 'streaming'
  /** 用户主动停止生成。 */
  stopped?: boolean
  messageType?: string
  questionId?: number
  providerCode?: string
  model?: string
  totalTokens?: number
}

export async function listAssistantSessions(): Promise<AssistantSession[]> {
  const response = await http.get<ApiEnvelope<AssistantSession[]>>('/assistant/sessions')
  return response.data.data
}
