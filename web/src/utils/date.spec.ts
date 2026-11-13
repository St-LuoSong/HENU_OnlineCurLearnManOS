import { describe, expect, it } from 'vitest'

import { formatDate, formatDateTime, toDateKey } from '@/utils/date'
import {
  buildMonthGrid,
  formatChineseDate,
  formatClock,
  formatWeekday,
  monthRange,
  toLocalDateKey
} from '@/utils/date'

describe('date utils', () => {
  it('按 Asia/Shanghai 输出中文格式化日期', () => {
    expect(formatDate('2026-09-20T16:00:00Z')).toBe('2026-09-21')
    expect(formatDateTime('2026-09-20T16:00:00Z')).toContain('2026-09-21')
    expect(toDateKey('2026-09-20T16:00:00Z')).toBe('2026-09-21')
  })

  it('空值与非法值返回明确占位', () => {
    expect(formatDate(null)).toBe('未设置')
    expect(formatDateTime('not-a-date')).toBe('未记录')
  })

  it('本地日期 key 不随时区挪日', () => {
    expect(toLocalDateKey(new Date(2026, 8, 21, 23, 30))).toBe('2026-09-21')
    expect(toLocalDateKey(new Date(2026, 0, 1, 0, 5))).toBe('2026-01-01')
  })

  it('生成周一打头的 42 格月历，保证高度稳定', () => {
    const grid = buildMonthGrid(2026, 9)
    expect(grid).toHaveLength(42)
    expect(grid[0].key).toBe('2026-08-31')
    expect(grid[0].inMonth).toBe(false)
    expect(grid.filter((cell) => cell.inMonth)).toHaveLength(30)
    expect(grid.some((cell) => cell.key === '2026-09-21')).toBe(true)
  })

  it('月份区间覆盖当月首尾日', () => {
    expect(monthRange(2026, 9)).toEqual({ from: '2026-09-01', to: '2026-09-30' })
    expect(monthRange(2026, 2)).toEqual({ from: '2026-02-01', to: '2026-02-28' })
  })

  it('中文日期与秒级时钟按本地时间输出', () => {
    const value = new Date(2026, 8, 21, 9, 5, 7)
    expect(formatChineseDate(value)).toBe('2026年9月21日')
    expect(formatWeekday(value)).toBe('星期一')
    expect(formatClock(value)).toBe('09:05:07')
  })
})
