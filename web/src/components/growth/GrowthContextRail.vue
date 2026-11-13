<script setup lang="ts">
import { computed } from 'vue'

import type { GrowthWorkspace } from '@/api/growth'
import { formatDate } from '@/utils/date'

const props = defineProps<{
  workspace: GrowthWorkspace
}>()

const openTasks = computed(() =>
  [...props.workspace.todayTasks, ...props.workspace.upcomingTasks]
)

const planProgress = computed(() => props.workspace.currentPlan?.progress ?? 0)
const review = computed(() => props.workspace.currentReview)
</script>

<template>
  <aside class="growth-context">
    <section class="context-block">
      <span class="context-label">周期进度</span>
      <strong>{{ formatDate(workspace.cycle.startDate) }} — {{ formatDate(workspace.cycle.endDate) }}</strong>
      <p>{{ workspace.summary.completedGoals }} 个目标已完成，{{ workspace.summary.openTasks }} 个行动待完成。</p>
    </section>

    <section class="context-block">
      <div class="context-head">
        <span class="context-label">当前计划</span>
        <span>{{ planProgress }}%</span>
      </div>
      <strong>{{ workspace.currentPlan?.title || '尚未建立当前计划' }}</strong>
      <div class="context-progress">
        <span :style="{ width: planProgress + '%' }" />
      </div>
      <p v-if="workspace.currentPlan">
        {{ workspace.currentPlan.completedItemCount }}/{{ workspace.currentPlan.totalItemCount }} 个条目完成
      </p>
      <p v-else>计划阶段会把方向拆成按周推进的步骤。</p>
    </section>

    <section class="context-block">
      <div class="context-head">
        <span class="context-label">本周证据</span>
        <span v-if="review">周 {{ review.weekStart.slice(5) }}</span>
      </div>
      <strong>{{ openTasks.length > 0 ? `${openTasks.length} 个行动待处理` : '当前行动已收敛' }}</strong>
      <p>{{ review?.note || '复盘阶段会自动汇总本周完成的目标、任务和学习记录。' }}</p>
    </section>
  </aside>
</template>

<style scoped>
.growth-context {
  display: grid;
  gap: 14px;
  padding-left: 20px;
  border-left: 1px solid var(--rule);
}

.context-block {
  display: grid;
  gap: 6px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--rule);
}

.context-block:last-child {
  padding-bottom: 0;
  border-bottom: 0;
}

.context-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.context-label,
.context-head > span:last-child {
  color: var(--ink-soft);
  font-size: 10px;
}

.context-block strong {
  overflow: hidden;
  color: var(--ink);
  font-family: var(--font-serif);
  font-size: 15px;
  font-weight: 600;
  line-height: 1.45;
  text-overflow: ellipsis;
}

.context-block p {
  margin: 0;
  color: var(--ink-soft);
  font-size: 11px;
  line-height: 1.7;
}

.context-progress {
  height: 4px;
  overflow: hidden;
  background: var(--paper-deep);
}

.context-progress span {
  display: block;
  height: 4px;
  background: var(--primary);
  transition: width 360ms ease;
}

@media (max-width: 900px) {
  .growth-context {
    grid-template-columns: repeat(3, minmax(0, 1fr));
    padding: 14px 0 0;
    border-top: 1px solid var(--rule);
    border-left: 0;
  }
}

@media (max-width: 620px) {
  .growth-context { grid-template-columns: 1fr; }
}
</style>
