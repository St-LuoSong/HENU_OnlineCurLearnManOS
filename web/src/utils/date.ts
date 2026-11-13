const DATE_TIME_FORMATTER = new Intl.DateTimeFormat('zh-CN', {
  timeZone: 'Asia/Shanghai',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false
})

const DATE_FORMATTER = new Intl.DateTimeFormat('zh-CN', {
  timeZone: 'Asia/Shanghai',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit'
})

export function formatDate(
  value?: string | null,
  fallback = '未设置'
): string {
  const date = parseDate(value)
  return date ? DATE_FORMATTER.format(date).replace(/\//g, '-') : fallback
}

export function formatDateTime(
  value?: string | null,
  fallback = '未记录'
): string {
  const date = parseDate(value)
  return date ? DATE_TIME_FORMATTER.format(date).replace(/\//g, '-') : fallback
}

export function formatMonthDayTime(
  value?: string | null,
  fallback = ''
): string {
  const formatted = formatDateTime(value, fallback)
  return formatted === fallback ? fallback : formatted.slice(5)
}

export function toDateKey(value?: string | null): string {
  return formatDate(value, '')
}

export function startOfWeek(value: Date = new Date()): Date {
  const date = new Date(value)
  const day = date.getDay()
  const offset = day === 0 ? -6 : 1 - day
  date.setDate(date.getDate() + offset)
  date.setHours(0, 0, 0, 0)
  return date
}

export function addDays(value: Date, days: number): Date {
  const date = new Date(value)
  date.setDate(date.getDate() + days)
  return date
}

function parseDate(value?: string | null): Date | null {
  if (!value) {
    return null
  }
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? null : date
}

export interface CalendarCell {
  key: string
  day: number
  inMonth: boolean
  isToday: boolean
}

export const WEEKDAY_LABELS = ['一', '二', '三', '四', '五', '六', '日']

const WEEKDAY_FULL_LABELS = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六']

/**
 * 按本地年月日拼 key。
 *
 * 与 {@link toDateKey} 的区别：这里处理的是「日历格子」这类本地日期，
 * 走 Intl + Asia/Shanghai 反而会在非 +08 时区把日期挪一天。
 */
export function toLocalDateKey(value: Date): string {
  const month = String(value.getMonth() + 1).padStart(2, '0')
  const day = String(value.getDate()).padStart(2, '0')
  return `${value.getFullYear()}-${month}-${day}`
}

export function formatChineseDate(value: Date): string {
  return `${value.getFullYear()}年${value.getMonth() + 1}月${value.getDate()}日`
}

export function formatWeekday(value: Date): string {
  return WEEKDAY_FULL_LABELS[value.getDay()]
}

export function formatClock(value: Date): string {
  const hour = String(value.getHours()).padStart(2, '0')
  const minute = String(value.getMinutes()).padStart(2, '0')
  const second = String(value.getSeconds()).padStart(2, '0')
  return `${hour}:${minute}:${second}`
}

/** 周一打头的 6 行月历网格，保证月份切换时高度稳定不抖动。 */
export function buildMonthGrid(year: number, month: number): CalendarCell[] {
  const firstDay = new Date(year, month - 1, 1)
  const offset = (firstDay.getDay() + 6) % 7
  const todayKey = toLocalDateKey(new Date())
  const cells: CalendarCell[] = []
  for (let index = 0; index < 42; index += 1) {
    const date = new Date(year, month - 1, 1 - offset + index)
    const key = toLocalDateKey(date)
    cells.push({
      key,
      day: date.getDate(),
      inMonth: date.getMonth() === month - 1,
      isToday: key === todayKey
    })
  }
  return cells
}

export function monthRange(year: number, month: number): { from: string; to: string } {
  const from = new Date(year, month - 1, 1)
  const to = new Date(year, month, 0)
  return { from: toLocalDateKey(from), to: toLocalDateKey(to) }
}
