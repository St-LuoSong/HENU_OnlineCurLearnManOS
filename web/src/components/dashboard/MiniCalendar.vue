<script setup lang="ts">
import { ArrowLeft, ArrowRight, Check } from '@element-plus/icons-vue'
import { computed, onUnmounted, ref } from 'vue'

import type { CalendarEvent, DashboardNote, DashboardNoteTone } from '@/api/dashboard'
import {
  WEEKDAY_LABELS,
  buildMonthGrid,
  formatChineseDate,
  formatClock,
  formatWeekday,
  monthRange,
  toLocalDateKey
} from '@/utils/date'

const props = withDefaults(
  defineProps<{
    modelValue: string
    events?: CalendarEvent[]
    notes?: DashboardNote[]
    checkedDates?: string[]
  }>(),
  {
    events: () => [],
    notes: () => [],
    checkedDates: () => []
  }
)

const emit = defineEmits<{
  (event: 'update:modelValue', value: string): void
  (event: 'month-change', value: { from: string; to: string }): void
}>()

/** 便签优先级：重要 > 放松 > 普通，一格只显示一个色点。 */
const TONE_PRIORITY: DashboardNoteTone[] = ['IMPORTANT', 'RELAX', 'DEFAULT']

const initial = new Date()
const selectedDate = new Date((props.modelValue || toLocalDateKey(initial)) + 'T00:00:00')
const anchor = Number.isNaN(selectedDate.getTime()) ? initial : selectedDate
const viewYear = ref(anchor.getFullYear())
const viewMonth = ref(anchor.getMonth() + 1)

const now = ref(new Date())
const clockTimer = window.setInterval(() => {
  now.value = new Date()
}, 1000)
onUnmounted(() => window.clearInterval(clockTimer))

const monthLabel = computed(() => viewYear.value + ' 年 ' + viewMonth.value + ' 月')

const eventsByDate = computed(() => {
  const grouped = new Map<string, CalendarEvent[]>()
  for (const event of props.events) {
    if (!event?.date) {
      continue
    }
    const list = grouped.get(event.date) ?? []
    list.push(event)
    grouped.set(event.date, list)
  }
  return grouped
})

const notesByDate = computed(() => {
  const grouped = new Map<string, DashboardNote[]>()
  for (const note of props.notes) {
    const list = grouped.get(note.noteDate) ?? []
    list.push(note)
    grouped.set(note.noteDate, list)
  }
  return grouped
})

const checkedDateSet = computed(() => new Set(props.checkedDates))

function toneOf(date: string): DashboardNoteTone | null {
  const list = notesByDate.value.get(date)
  if (!list || list.length === 0) {
    return null
  }
  return TONE_PRIORITY.find((tone) => list.some((note) => note.tone === tone)) ?? 'DEFAULT'
}

const gridCells = computed(() =>
  buildMonthGrid(viewYear.value, viewMonth.value).map((cell) => {
    const tone = toneOf(cell.key)
    const dayEvents = eventsByDate.value.get(cell.key) ?? []
    const checked = checkedDateSet.value.has(cell.key)
    return {
      key: cell.key,
      day: cell.day,
      inMonth: cell.inMonth,
      isToday: cell.isToday,
      checked,
      toneClass: tone ? 'tone-' + tone.toLowerCase() : '',
      eventCount: dayEvents.length,
      title: [
        ...dayEvents
          .map((event) => event.title)
          .slice(0, 3),
        checked ? '已签到' : ''
      ]
        .filter(Boolean)
        .join('、')
    }
  })
)

function shiftMonth(step: number): void {
  const target = new Date(viewYear.value, viewMonth.value - 1 + step, 1)
  viewYear.value = target.getFullYear()
  viewMonth.value = target.getMonth() + 1
  emit('month-change', monthRange(viewYear.value, viewMonth.value))
}

function selectDate(cell: { key: string; inMonth: boolean }): void {
  if (!cell.inMonth) {
    const target = new Date(cell.key + 'T00:00:00')
    if (!Number.isNaN(target.getTime())) {
      viewYear.value = target.getFullYear()
      viewMonth.value = target.getMonth() + 1
      emit('month-change', monthRange(viewYear.value, viewMonth.value))
    }
  }
  emit('update:modelValue', cell.key)
}
</script>

<template>
  <section class="mini-calendar">
    <header class="mini-clock">
      <span class="mini-clock-date">{{ formatChineseDate(now) }}</span>
      <span class="mini-clock-week">{{ formatWeekday(now) }}</span>
      <strong class="mini-clock-time">{{ formatClock(now) }}</strong>
    </header>

    <div class="mini-nav">
      <button type="button" class="mini-nav-button" aria-label="上一个月" @click="shiftMonth(-1)">
        <el-icon><ArrowLeft /></el-icon>
      </button>
      <span class="mini-month">{{ monthLabel }}</span>
      <button type="button" class="mini-nav-button" aria-label="下一个月" @click="shiftMonth(1)">
        <el-icon><ArrowRight /></el-icon>
      </button>
    </div>

    <div class="mini-weekdays" aria-hidden="true">
      <span v-for="label in WEEKDAY_LABELS" :key="label">{{ label }}</span>
    </div>

    <div class="mini-grid">
      <button
        v-for="cell in gridCells"
        :key="cell.key"
        type="button"
        class="mini-cell"
        :class="{
          'is-out': !cell.inMonth,
          'is-today': cell.isToday,
          'is-selected': cell.key === modelValue
        }"
        :title="cell.title"
        :aria-pressed="cell.key === modelValue"
        :aria-label="cell.checked ? `${cell.key}，已签到` : cell.key"
        @click="selectDate(cell)"
      >
        <span class="mini-day">{{ cell.day }}</span>
        <span v-if="cell.checked" class="mini-checkin" title="已签到">
          <el-icon><Check /></el-icon>
        </span>
        <span class="mini-marks">
          <i v-if="cell.eventCount" class="mini-dot mini-dot-event" />
          <i v-if="cell.toneClass" class="mini-dot mini-dot-note" :class="cell.toneClass" />
        </span>
      </button>
    </div>

    <footer class="mini-legend">
      <span><i class="mini-dot mini-dot-event" />学习事件</span>
      <span><i class="mini-dot mini-dot-note tone-important" />重要</span>
      <span><i class="mini-dot mini-dot-note tone-relax" />放松</span>
      <span><i class="mini-checkin-key"><el-icon><Check /></el-icon></i>已签到</span>
    </footer>
  </section>
</template>

<style scoped>
.mini-calendar {
  display: grid;
  gap: 12px;
  padding: 16px 16px 14px;
  border: 1px solid var(--rule);
  border-radius: 8px;
  background:
    radial-gradient(circle at 12% -10%, rgba(47, 107, 95, 0.14), transparent 62%),
    var(--white);
}

.mini-clock {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 2px 10px;
  align-items: baseline;
  padding-bottom: 12px;
  border-bottom: 1px dashed var(--rule);
}

.mini-clock-date {
  font-family: var(--font-display);
  font-size: 16px;
  font-weight: 700;
}

.mini-clock-week {
  color: var(--ink-soft);
  font-size: 12px;
}

.mini-clock-time {
  grid-column: 1 / -1;
  color: var(--jade);
  font-family: "DIN Alternate", "Menlo", monospace;
  font-size: 22px;
  letter-spacing: 1px;
}

.mini-nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.mini-nav-button {
  display: inline-flex;
  width: 26px;
  height: 26px;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--rule);
  border-radius: 6px;
  background: var(--paper);
  color: var(--ink-soft);
  cursor: pointer;
  transition: border-color 160ms ease, color 160ms ease, background 160ms ease;
}

.mini-nav-button:hover {
  border-color: var(--jade);
  background: var(--white);
  color: var(--jade);
}

.mini-month {
  font-family: var(--font-display);
  font-size: 15px;
  font-weight: 700;
}

.mini-weekdays,
.mini-grid {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 4px;
}

.mini-weekdays span {
  color: var(--ink-soft);
  font-size: 11px;
  text-align: center;
}

.mini-cell {
  position: relative;
  display: grid;
  aspect-ratio: 1 / 1;
  align-content: center;
  justify-items: center;
  gap: 2px;
  padding: 0;
  border: 1px solid transparent;
  border-radius: 6px;
  background: transparent;
  color: var(--ink);
  cursor: pointer;
  transition: background 160ms ease, border-color 160ms ease, transform 160ms ease;
}

.mini-cell:hover {
  border-color: var(--rule-strong);
  background: var(--paper);
  transform: translateY(-1px);
}

.mini-cell.is-out {
  color: var(--rule-strong);
}

.mini-cell.is-today {
  border-color: var(--ochre);
  background: rgba(193, 138, 46, 0.1);
}

.mini-cell.is-selected {
  border-color: var(--jade);
  background: rgba(47, 107, 95, 0.12);
  font-weight: 700;
}

.mini-day {
  font-size: 12px;
  line-height: 1;
}

.mini-checkin {
  position: absolute;
  top: 3px;
  right: 3px;
  display: inline-flex;
  width: 12px;
  height: 12px;
  align-items: center;
  justify-content: center;
  color: var(--white);
  background: var(--jade);
  border-radius: 50%;
  box-shadow: 0 0 0 1px var(--white);
}

.mini-checkin .el-icon {
  font-size: 8px;
}

.mini-marks {
  display: flex;
  height: 6px;
  align-items: center;
  justify-content: center;
  gap: 3px;
}

.mini-dot {
  display: inline-block;
  width: 5px;
  height: 5px;
  border-radius: 50%;
}

.mini-dot-event {
  background: var(--ink-soft);
}

.mini-dot-note.tone-important {
  background: var(--cinnabar);
}

.mini-dot-note.tone-relax {
  background: var(--jade);
}

.mini-dot-note.tone-default {
  background: var(--ochre);
}

.mini-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  padding-top: 10px;
  border-top: 1px dashed var(--rule);
  color: var(--ink-soft);
  font-size: 11px;
}

.mini-legend span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.mini-checkin-key {
  display: inline-flex;
  width: 13px;
  height: 13px;
  align-items: center;
  justify-content: center;
  color: var(--white);
  background: var(--jade);
  border-radius: 50%;
}

.mini-checkin-key .el-icon {
  font-size: 8px;
}

@media (prefers-reduced-motion: reduce) {
  .mini-cell,
  .mini-nav-button {
    transition: none;
  }
}

/* 2026 视觉刷新：与概览页右栏的白色卡片对齐 */
.mini-calendar {
  gap: 12px;
  padding: 16px;
  background: var(--white);
  border: 1px solid var(--rule);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
}

.mini-clock {
  padding: 12px 14px;
  background: linear-gradient(135deg, #eff5ff, #f8fbff);
  border: 1px solid #e1ebfa;
  border-bottom: 1px solid #e1ebfa;
  border-radius: var(--radius-md);
}

.mini-clock-date {
  font-family: var(--font-display);
  font-size: 14px;
  font-weight: 800;
}

.mini-clock-week {
  color: var(--ink-soft);
  font-size: 11px;
}

.mini-clock-time {
  color: var(--primary-deep);
  font-family: var(--font-numeric);
  font-size: 20px;
  letter-spacing: 0;
}

.mini-nav-button {
  width: 28px;
  height: 28px;
  background: var(--primary-wash);
  border: none;
  border-radius: var(--radius-md);
  color: var(--primary);
}

.mini-nav-button:hover {
  background: var(--primary-soft);
  color: var(--primary-deep);
}

.mini-month {
  font-family: var(--font-display);
  font-size: 14px;
  font-weight: 800;
}

.mini-weekdays span {
  color: var(--ink-faint);
  font-size: 11px;
}

.mini-cell {
  border-radius: var(--radius-md);
}

.mini-cell:hover {
  background: var(--primary-wash);
  border-color: var(--rule);
  transform: none;
}

.mini-cell.is-today {
  color: var(--primary-deep);
  background: transparent;
  border-color: var(--primary);
  font-weight: 700;
}

.mini-cell.is-selected {
  color: var(--white);
  background: var(--primary-deep);
  border-color: var(--primary-deep);
  font-weight: 700;
}

.mini-cell.is-selected .mini-day {
  color: var(--white);
}

.mini-cell.is-selected .mini-dot {
  background: rgba(255, 255, 255, 0.78);
}

.mini-cell.is-selected .mini-checkin {
  color: var(--jade);
  background: var(--white);
  box-shadow: none;
}

.mini-dot-event {
  background: var(--primary);
}

.mini-dot-note.tone-important {
  background: var(--cinnabar);
}

.mini-dot-note.tone-relax {
  background: var(--jade);
}

.mini-dot-note.tone-default {
  background: var(--ochre);
}

.mini-legend {
  border-top: 1px solid var(--rule);
  color: var(--ink-soft);
  font-size: 11px;
}
</style>

