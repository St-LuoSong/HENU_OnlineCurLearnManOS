<script setup lang="ts">
import { computed } from 'vue'

import type { GrowthWorkspace } from '@/api/growth'
import type { GrowthStage } from '@/stores/growth'

const props = defineProps<{
  stage: GrowthStage
  workspace: GrowthWorkspace
  readOnly: boolean
}>()

const emit = defineEmits<{
  (event: 'select', stage: GrowthStage): void
}>()

const STAGES: Array<{ key: GrowthStage; label: string; hint: string }> = [
  { key: 'DIRECTION', label: '方向', hint: '先确认方向' },
  { key: 'GOALS', label: '目标', hint: '转化为结果' },
  { key: 'PLAN', label: '计划', hint: '拆分执行路径' },
  { key: 'ACTION', label: '行动', hint: '完成下一步' },
  { key: 'REVIEW', label: '复盘', hint: '留下证据' }
]

const completed = computed(() => ({
  DIRECTION: Boolean(props.workspace.direction),
  GOALS: props.workspace.goals.length > 0,
  PLAN: Boolean(props.workspace.currentPlan),
  ACTION: props.workspace.todayTasks.length === 0
    && props.workspace.currentPlan?.items.every((item) => item.status === 'DONE') === true,
  REVIEW: Boolean(props.workspace.currentReview?.note)
}))
</script>

<template>
  <nav class="growth-stage-rail" aria-label="成长阶段">
    <button
      v-for="item in STAGES"
      :key="item.key"
      type="button"
      class="growth-stage"
      :class="{
        active: stage === item.key,
        complete: completed[item.key]
      }"
      @click="emit('select', item.key)"
    >
      <span class="stage-marker">
        <span v-if="completed[item.key]">✓</span>
        <span v-else>{{ STAGES.findIndex((entry) => entry.key === item.key) + 1 }}</span>
      </span>
      <span class="stage-copy">
        <strong>{{ item.label }}</strong>
        <small>{{ item.hint }}</small>
      </span>
    </button>
  </nav>
</template>

<style scoped>
.growth-stage-rail {
  position: relative;
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  border-top: 1px solid var(--rule);
  border-bottom: 1px solid var(--rule);
}

.growth-stage-rail::before {
  position: absolute;
  top: 27px;
  right: 8%;
  left: 8%;
  height: 1px;
  content: '';
  background: var(--rule);
}

.growth-stage {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr);
  gap: 8px;
  align-items: center;
  padding: 12px 8px;
  text-align: left;
  background: var(--paper);
  border: 0;
  border-right: 1px solid var(--rule);
  cursor: pointer;
}

.growth-stage:last-child { border-right: 0; }

.growth-stage:hover,
.growth-stage.active {
  background: var(--white);
}

.stage-marker {
  display: grid;
  width: 24px;
  height: 24px;
  place-items: center;
  color: var(--ink-soft);
  font-family: var(--font-numeric);
  font-size: 11px;
  background: var(--paper-deep);
  border: 1px solid var(--rule-strong);
  border-radius: 50%;
}

.growth-stage.active .stage-marker {
  color: var(--white);
  background: var(--primary);
  border-color: var(--primary);
}

.growth-stage.complete .stage-marker {
  color: var(--white);
  background: var(--jade);
  border-color: var(--jade);
}

.stage-copy {
  display: grid;
  min-width: 0;
  gap: 3px;
}

.stage-copy strong {
  color: var(--ink);
  font-family: var(--font-serif);
  font-size: 15px;
}

.stage-copy small {
  overflow: hidden;
  color: var(--ink-soft);
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.growth-stage.disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

@media (max-width: 900px) {
  .growth-stage-rail {
    display: flex;
    overflow-x: auto;
  }

  .growth-stage {
    flex: 0 0 160px;
  }

  .growth-stage-rail::before { display: none; }
}
</style>
