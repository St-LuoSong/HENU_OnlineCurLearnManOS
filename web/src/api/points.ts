import http from '@/api/http'
import type { ApiEnvelope, PageResult } from '@/api/types'

export interface PointAccount {
  id: number
  userId: number
  balance: number
  totalEarned: number
  totalSpent: number
  updatedAt: string
}

export interface PointTransaction {
  id: number
  type: string
  amount: number
  direction: 'DEBIT' | 'CREDIT'
  balanceAfter: number
  status: string
  referenceType?: string
  referenceId?: string
  remark?: string
  createdAt: string
  completedAt?: string
}

export async function getPointAccount(): Promise<PointAccount> {
  const response = await http.get<ApiEnvelope<PointAccount>>('/points/account')
  return response.data.data
}

export async function listPointTransactions(params: {
  page: number
  size: number
}): Promise<PageResult<PointTransaction>> {
  const response = await http.get<ApiEnvelope<PageResult<PointTransaction>>>(
    '/points/transactions',
    { params }
  )
  return response.data.data
}
