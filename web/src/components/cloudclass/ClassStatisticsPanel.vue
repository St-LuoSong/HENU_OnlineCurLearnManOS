<script setup lang="ts">
import { computed } from 'vue'

import type { CloudClassRosterMember, CloudClassStatistics } from '@/api/cloudClass'

const props = defineProps<{
  statistics: CloudClassStatistics | null
  members: CloudClassRosterMember[]
}>()

const pending = computed(() =>
  props.statistics
    ? Math.max(0, props.statistics.submissionCount - props.statistics.gradedCount)
    : 0
)
const gradedPercent = computed(() => {
  const stats = props.statistics
  if (!stats || stats.submissionCount === 0) return 0
  return Math.round(stats.gradedCount * 100 / stats.submissionCount)
})

const scoreBins = computed(() => {
  const bins = [
    { label: '90-100', min: 90, max: 100, count: 0 },
    { label: '80-89', min: 80, max: 89.99, count: 0 },
    { label: '70-79', min: 70, max: 79.99, count: 0 },
    { label: '60-69', min: 60, max: 69.99, count: 0 },
    { label: '0-59', min: 0, max: 59.99, count: 0 }
  ]
  for (const member of props.members) {
    const score = member.averageScore ?? 0
    const bin = bins.find((item) => score >= item.min && score <= item.max)
    if (bin) bin.count++
  }
  const max = Math.max(1, ...bins.map((item) => item.count))
  return bins.map((item) => ({ ...item, percent: Math.round(item.count / max * 100) }))
})
</script>

<template>
  <div class="class-statistics">
    <div class="stats-summary">
      <div>
        <strong>{{ statistics?.memberCount ?? 0 }}</strong>
        <span>班级成员</span>
      </div>
      <div>
        <strong>{{ statistics?.taskCount ?? 0 }}</strong>
        <span>课堂任务</span>
      </div>
      <div>
        <strong>{{ statistics?.submissionCount ?? 0 }}</strong>
        <span>累计提交</span>
      </div>
      <div>
        <strong>{{ pending }}</strong>
        <span>待批改</span>
      </div>
      <div>
        <strong>{{ statistics?.averageScore ?? 0 }}</strong>
        <span>平均分</span>
      </div>
    </div>

    <div class="stats-grid">
      <section class="stats-section">
        <header>
          <h3>批改进度</h3>
          <span>{{ gradedPercent }}% 已处理</span>
        </header>
        <div class="graded-track">
          <div :style="{ width: gradedPercent + '%' }" />
        </div>
        <p>
          已批改 {{ statistics?.gradedCount ?? 0 }} 份，待批改 {{ pending }} 份。
        </p>
      </section>

      <section class="stats-section">
        <header>
          <h3>成员成绩分布</h3>
          <span>按平均分</span>
        </header>
        <div v-if="members.length === 0" class="stats-empty">暂无成员成绩数据。</div>
        <div v-for="bin in scoreBins" :key="bin.label" class="score-bin">
          <span>{{ bin.label }}</span>
          <div class="score-bin-track"><i :style="{ width: Math.max(3, bin.percent) + '%' }" /></div>
          <b>{{ bin.count }}</b>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.class-statistics {
  display: grid;
  gap: 20px;
  padding-top: 18px;
}

.stats-summary {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 0;
  border-top: 1px solid var(--rule);
  border-bottom: 1px solid var(--rule);
}

.stats-summary div {
  display: grid;
  gap: 3px;
  padding: 15px 12px;
  border-right: 1px solid var(--rule);
}

.stats-summary div:last-child { border-right: 0; }

.stats-summary strong {
  color: var(--ink);
  font-family: var(--font-numeric);
  font-size: 24px;
}

.stats-summary span,
.stats-section p {
  color: var(--ink-soft);
  font-size: 11px;
}

.stats-grid {
  display: grid;
  grid-template-columns: minmax(0, 0.8fr) minmax(0, 1.2fr);
  gap: 28px;
}

.stats-section header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--rule);
}

.stats-section h3 {
  margin: 0;
  color: var(--ink);
  font-family: var(--font-serif);
  font-size: 17px;
}

.stats-section header span {
  color: var(--ink-soft);
  font-size: 10px;
}

.graded-track {
  height: 8px;
  margin: 18px 0 10px;
  overflow: hidden;
  background: var(--paper-deep);
}

.graded-track div {
  height: 8px;
  background: var(--jade);
  transition: width 480ms ease;
}

.stats-section p {
  margin: 0;
  line-height: 1.7;
}

.score-bin {
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr) 20px;
  gap: 9px;
  align-items: center;
  padding: 8px 0;
  color: var(--ink-soft);
  font-size: 10px;
}

.score-bin-track {
  height: 6px;
  overflow: hidden;
  background: var(--paper-deep);
}

.score-bin-track i {
  display: block;
  height: 6px;
  background: var(--primary);
  transition: width 480ms ease;
}

.score-bin b {
  color: var(--ink);
  font-family: var(--font-numeric);
  text-align: right;
}

.stats-empty {
  padding: 18px 0;
  color: var(--ink-soft);
  font-size: 12px;
}

@media (max-width: 900px) {
  .stats-summary { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .stats-summary div:nth-child(2n) { border-right: 0; }
  .stats-grid { grid-template-columns: 1fr; }
}

@media (prefers-reduced-motion: reduce) {
  .graded-track div,
  .score-bin-track i { transition: none; }
}
</style>
