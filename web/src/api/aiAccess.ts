import http from '@/api/http'
import type { ApiEnvelope } from '@/api/types'

export interface AiAccess {
  standardUsed: number
  standardLimit: number
  standardRemaining: number
  expertUsed: number
  expertLimit: number
  expertRemaining: number
}

export async function getAiAccess(): Promise<AiAccess> {
  const response = await http.get<ApiEnvelope<AiAccess>>('/ai/access')
  return response.data.data
}
