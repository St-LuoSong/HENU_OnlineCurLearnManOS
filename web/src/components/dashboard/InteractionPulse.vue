<script setup lang="ts">
import { computed, ref } from 'vue'

import type { InteractionBreakdown, InteractionTrendPoint } from '@/api/dashboard'

type SegmentKey = keyof InteractionBreakdown

interface Segment {
  key: SegmentKey
  label: string
  color: string
}

interface HoverState {
  day: number
  segment?: SegmentKey
}

const props = defineProps<{
  trend: InteractionTrendPoint[]
}>()

const WIDTH = 760
const HEIGHT = 250
const PAD_LEFT = 42
const PAD_RIGHT = 38
const PAD_TOP = 20
const PAD_BOTTOM = 34
const PLOT_WIDTH = WIDTH - PAD_LEFT - PAD_RIGHT
const PLOT_HEIGHT = HEIGHT - PAD_TOP - PAD_BOTTOM
const PLOT_BOTTOM = PAD_TOP + PLOT_HEIGHT

const SEGMENTS: Segment[] = [
  { key: 'aiQuestions', label: 'AI 问答', color: '#3b78f0' },
  { key: 'practiceAnswers', label: '练习', color: '#22a06b' },
  { key: 'resourceViews', label: '资源', color: '#7a6cf0' },
  { key: 'forumActivities', label: '论坛', color: '#f0a23a' },
  { key: 'classSubmissions', label: '班级任务', color: '#ef6a5b' }
]

const hover = ref<HoverState | null>(null)

const maxTotal = computed(() =>
  Math.max(1, ...props.trend.map((point) => totalOf(point.breakdown)))
)
const isEmpty = computed(() =>
  props.trend.every((point) => totalOf(point.breakdown) === 0)
)

function totalOf(breakdown: InteractionBreakdown): number {
  return SEGMENTS.reduce((sum, segment) => sum + breakdown[segment.key], 0)
}

function xAt(index: number): number {
  if (props.trend.length <= 1) {
    return PAD_LEFT + PLOT_WIDTH / 2
  }
  return PAD_LEFT + (PLOT_WIDTH * index) / (props.trend.length - 1)
}

function yAt(value: number, max = maxTotal.value): number {
  return PLOT_BOTTOM - (PLOT_HEIGHT * Math.min(value, max)) / max
}

function dateLabel(date: string): string {
  return String(date).slice(5).replace('-', '/')
}

function barWidth(index: number): number {
  return hover.value?.day === index ? 36 : 28
}

const bars = computed(() =>
  props.trend.map((point, index) => {
    const total = totalOf(point.breakdown)
    let cumulativeShare = 0
    return {
      index,
      total,
      x: xAt(index),
      segments: SEGMENTS.map((segment) => {
        const value = point.breakdown[segment.key]
        const share = total === 0 ? 0 : value / total
        const y = PLOT_BOTTOM - (cumulativeShare + share) * PLOT_HEIGHT
        const height = share * PLOT_HEIGHT
        cumulativeShare += share
        return {
          ...segment,
          value,
          share,
          y,
          height
        }
      })
    }
  })
)

const linePath = computed(() =>
  props.trend
    .map((point, index) => {
      const x = xAt(index)
      const y = yAt(totalOf(point.breakdown))
      return `${index === 0 ? 'M' : 'L'}${x.toFixed(1)},${y.toFixed(1)}`
    })
    .join(' ')
)

const countTicks = computed(() => [1, 0.5, 0].map((ratio) => {
  const value = Math.round(maxTotal.value * ratio)
  return {
    y: PAD_TOP + PLOT_HEIGHT * (1 - ratio),
    count: value,
    percent: Math.round(ratio * 100)
  }
}))

const tooltip = computed(() => {
  if (!hover.value) {
    return null
  }
  const point = props.trend[hover.value.day]
  if (!point) {
    return null
  }
  const total = totalOf(point.breakdown)
  if (hover.value.segment) {
    const segment = SEGMENTS.find((item) => item.key === hover.value?.segment)
    if (!segment) {
      return null
    }
    const value = point.breakdown[segment.key]
    return {
      title: `${dateLabel(point.date)} · ${segment.label}`,
      lines: [`${value} 次`, `占当日 ${total === 0 ? 0 : Math.round(value / total * 100)}%`]
    }
  }
  return {
    title: `${dateLabel(point.date)} · ${total} 次互动`,
    lines: SEGMENTS.map((segment) => `${segment.label} ${point.breakdown[segment.key]} 次`)
  }
})

const tooltipStyle = computed(() => {
  const day = hover.value?.day ?? 0
  const left = Math.min(88, Math.max(12, (xAt(day) / WIDTH) * 100))
  return { left: `${left}%` }
})

function setHover(day: number, segment?: SegmentKey): void {
  hover.value = { day, segment }
}

function clearHover(): void {
  hover.value = null
}
</script>

<template>
  <div class="interaction-pulse" @pointerleave="clearHover">
    <svg
      class="pulse-svg"
      :viewBox="`0 0 ${WIDTH} ${HEIGHT}`"
      role="img"
      aria-label="学习互动趋势：折线为每日互动次数，柱状图为互动类型占比"
      @pointerleave="clearHover"
    >
      <g class="pulse-grid">
        <g v-for="tick in countTicks" :key="tick.percent">
          <line
            :x1="PAD_LEFT"
            :x2="WIDTH - PAD_RIGHT"
            :y1="tick.y"
            :y2="tick.y"
          />
          <text :x="PAD_LEFT - 9" :y="tick.y + 3" text-anchor="end">{{ tick.count }}</text>
          <text :x="WIDTH - PAD_RIGHT + 9" :y="tick.y + 3" text-anchor="start">{{ tick.percent }}%</text>
        </g>
      </g>

      <g
        v-for="bar in bars"
        :key="bar.index"
        class="pulse-day"
        @pointerenter="setHover(bar.index)"
      >
        <rect
          class="pulse-track"
          :x="bar.x - barWidth(bar.index) / 2"
          :y="PAD_TOP"
          :width="barWidth(bar.index)"
          :height="PLOT_HEIGHT"
        />
        <rect
          v-for="segment in bar.segments"
          :key="segment.key"
          class="pulse-segment"
          :class="{ active: hover?.day === bar.index && hover?.segment === segment.key }"
          :x="bar.x - barWidth(bar.index) / 2"
          :y="segment.y"
          :width="barWidth(bar.index)"
          :height="segment.height"
          :style="{ fill: segment.color }"
          @pointerenter.stop="setHover(bar.index, segment.key)"
        />
        <text
          class="pulse-total-label"
          :x="bar.x"
          :y="yAt(bar.total) - 8"
          text-anchor="middle"
        >
          {{ bar.total }}
        </text>
        <text class="pulse-date-label" :x="bar.x" :y="HEIGHT - 10" text-anchor="middle">
          {{ dateLabel(trend[bar.index]?.date ?? '') }}
        </text>
      </g>

      <path class="pulse-line" :d="linePath" />
      <circle
        v-for="(point, index) in trend"
        :key="point.date"
        class="pulse-point"
        :class="{ active: hover?.day === index }"
        :cx="xAt(index)"
        :cy="yAt(totalOf(point.breakdown))"
        :r="hover?.day === index ? 6 : 3.8"
        @pointerenter="setHover(index)"
      />
    </svg>

    <div v-if="tooltip" class="pulse-tooltip" :style="tooltipStyle">
      <strong>{{ tooltip.title }}</strong>
      <span v-for="line in tooltip.lines" :key="line">{{ line }}</span>
    </div>

    <div class="pulse-legend">
      <span v-for="segment in SEGMENTS" :key="segment.key" class="pulse-legend-item">
        <i :style="{ background: segment.color }" />
        {{ segment.label }}
      </span>
    </div>
    <p v-if="isEmpty" class="pulse-empty">最近 7 天还没有学习互动，完成一次问答或练习就会点亮这里。</p>
  </div>
</template>

<style scoped>
.interaction-pulse {
  position: relative;
  display: grid;
  gap: 10px;
}

.pulse-svg {
  width: 100%;
  height: auto;
  overflow: visible;
}

.pulse-grid line {
  stroke: var(--rule);
  stroke-dasharray: 3 5;
  stroke-width: 1;
}

.pulse-grid text {
  fill: var(--ink-soft);
  font-family: var(--font-numeric);
  font-size: 10px;
}

.pulse-track {
  fill: var(--paper-deep);
  opacity: 0.72;
  transition: width 120ms ease, x 120ms ease;
}

.pulse-segment {
  cursor: pointer;
  stroke: var(--white);
  stroke-width: 1;
  transition: opacity 140ms ease, width 120ms ease, x 120ms ease;
}

.pulse-segment:hover,
.pulse-segment.active {
  opacity: 0.78;
}

.pulse-day {
  cursor: pointer;
}

.pulse-total-label {
  fill: var(--ink);
  font-family: var(--font-numeric);
  font-size: 11px;
  font-weight: 700;
  animation: pulse-count-in 500ms ease-out;
}

.pulse-date-label {
  fill: var(--ink-soft);
  font-size: 10px;
}

.pulse-line {
  fill: none;
  pointer-events: none;
  stroke: var(--primary-deep);
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 2;
  animation: pulse-line-in 550ms ease-out;
}

.pulse-point {
  fill: var(--white);
  pointer-events: all;
  stroke: var(--primary-deep);
  stroke-width: 2;
  transition: r 140ms ease, fill 140ms ease;
}

.pulse-point:hover,
.pulse-point.active {
  fill: var(--primary-deep);
  r: 6;
}

.pulse-tooltip {
  position: absolute;
  top: 8px;
  z-index: 2;
  display: grid;
  min-width: 150px;
  gap: 3px;
  padding: 9px 11px;
  color: var(--ink-soft);
  font-size: 11px;
  line-height: 1.55;
  background: var(--white);
  border: 1px solid var(--rule-strong);
  box-shadow: var(--shadow-soft);
  pointer-events: none;
  transform: translateX(-50%);
}

.pulse-tooltip strong {
  color: var(--ink);
  font-size: 12px;
}

.pulse-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 18px;
  padding-top: 10px;
  border-top: 1px solid var(--rule);
}

.pulse-legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--ink-soft);
  font-size: 11px;
}

.pulse-legend-item i {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.pulse-empty {
  margin: 0;
  color: var(--ink-soft);
  font-size: 12px;
}

@keyframes pulse-line-in {
  from { opacity: 0; }
  to { opacity: 1; }
}

@keyframes pulse-count-in {
  from { opacity: 0; }
  to { opacity: 1; }
}

@media (prefers-reduced-motion: reduce) {
  .pulse-track,
  .pulse-segment,
  .pulse-point { transition: none; }
  .pulse-line,
  .pulse-total-label { animation: none; }
}
</style>
