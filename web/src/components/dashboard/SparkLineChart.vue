<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    points: Array<{ label: string; value: number }>
    height?: number
    unit?: string
  }>(),
  {
    height: 132,
    unit: '分钟'
  }
)

const WIDTH = 720
const PAD_X = 14
const PAD_TOP = 18
const PAD_BOTTOM = 30

const maxValue = computed(() => Math.max(1, ...props.points.map((point) => point.value)))
const total = computed(() => props.points.reduce((sum, point) => sum + point.value, 0))
const isEmpty = computed(
  () => props.points.length === 0 || props.points.every((point) => point.value === 0)
)
const plotHeight = computed(() => props.height - PAD_TOP - PAD_BOTTOM)
const bottomEdge = computed(() => PAD_TOP + plotHeight.value)

const coordinates = computed(() => {
  const step =
    props.points.length <= 1 ? 0 : (WIDTH - PAD_X * 2) / (props.points.length - 1)
  return props.points.map((point, index) => ({
    ...point,
    x: PAD_X + step * index,
    y: PAD_TOP + plotHeight.value * (1 - point.value / maxValue.value)
  }))
})

const linePath = computed(() =>
  coordinates.value
    .map((item, index) => `${index === 0 ? 'M' : 'L'}${item.x.toFixed(1)},${item.y.toFixed(1)}`)
    .join(' ')
)

const areaPath = computed(() => {
  const items = coordinates.value
  if (items.length === 0) {
    return ''
  }
  const first = items[0]
  const last = items[items.length - 1]
  return `${linePath.value} L${last.x.toFixed(1)},${bottomEdge.value.toFixed(1)} L${first.x.toFixed(1)},${bottomEdge.value.toFixed(1)} Z`
})

const gridLines = computed(() => [0, 0.5, 1].map((ratio) => PAD_TOP + plotHeight.value * ratio))
</script>

<template>
  <div class="spark-chart">
    <svg
      :viewBox="`0 0 ${WIDTH} ${height}`"
      class="spark-canvas"
      role="img"
      :aria-label="`学习时长趋势，共 ${points.length} 天`"
    >
      <line
        v-for="y in gridLines"
        :key="y"
        :x1="PAD_X"
        :x2="WIDTH - PAD_X"
        :y1="y"
        :y2="y"
        class="spark-grid"
      />
      <path v-if="!isEmpty" :d="areaPath" class="spark-area" />
      <path v-if="!isEmpty" :d="linePath" class="spark-line" />
      <circle
        v-for="item in coordinates"
        :key="`dot-${item.label}`"
        :cx="item.x"
        :cy="item.y"
        r="3.4"
        class="spark-dot"
      />
    </svg>
    <div class="spark-axis" aria-hidden="true">
      <span v-for="item in points" :key="`axis-${item.label}`">{{ item.label }}</span>
    </div>
    <p v-if="isEmpty" class="spark-note">最近 {{ points.length }} 天还没有记录学习时长。</p>
    <p v-else class="spark-note">峰值 {{ maxValue }} {{ unit }} · 合计 {{ total }} {{ unit }}</p>
  </div>
</template>

<style scoped>
.spark-chart {
  display: grid;
  gap: 6px;
}

.spark-canvas {
  width: 100%;
  height: auto;
  min-height: 120px;
  overflow: visible;
}

.spark-grid {
  stroke: var(--rule);
  stroke-dasharray: 3 5;
  stroke-width: 1;
}

.spark-line {
  fill: none;
  stroke: var(--primary);
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 2.2;
  animation: spark-draw 620ms ease-out;
}

.spark-area {
  fill: var(--primary);
  opacity: 0.12;
  animation: spark-fade 620ms ease-out;
}

.spark-dot {
  fill: var(--primary);
  stroke: var(--white);
  stroke-width: 1.6;
}

.spark-axis {
  display: grid;
  grid-auto-flow: column;
  grid-auto-columns: 1fr;
  color: var(--ink-soft);
  font-size: 11px;
  text-align: center;
}

.spark-note {
  margin: 0;
  color: var(--ink-soft);
  font-size: 12px;
}

@keyframes spark-draw {
  from {
    opacity: 0;
  }
}

@keyframes spark-fade {
  from {
    opacity: 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .spark-line,
  .spark-area {
    animation: none;
  }
}
</style>

