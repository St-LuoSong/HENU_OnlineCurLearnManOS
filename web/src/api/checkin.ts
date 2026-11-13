import http from '@/api/http'
import type { ApiEnvelope } from '@/api/types'

export interface CheckinStatus {
  checkedToday: boolean
  currentStreak: number
  todayReward: number
  nextReward: number
  totalCheckins: number
  recentCheckins: string[]
}

export interface CheckinResult {
  earnedPoints: number
  status: CheckinStatus
}

export async function getCheckinStatus(): Promise<CheckinStatus> {
  const response = await http.get<ApiEnvelope<CheckinStatus>>('/checkins/status')
  return response.data.data
}

export async function checkinToday(): Promise<CheckinResult> {
  const response = await http.post<ApiEnvelope<CheckinResult>>('/checkins')
  return response.data.data
}
