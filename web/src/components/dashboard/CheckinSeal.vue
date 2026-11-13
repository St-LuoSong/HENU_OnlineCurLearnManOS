<script setup lang="ts">
import { Check, Medal } from '@element-plus/icons-vue'
import { computed, ref, watch } from 'vue'

import type { CheckinStatus } from '@/api/checkin'

const props = defineProps<{
  status: CheckinStatus
  checking?: boolean
}>()

const emit = defineEmits<{
  (event: 'checkin'): void
}>()

const stamped = ref(false)
const cycleDay = computed(() => {
  if (props.status.currentStreak <= 0) {
    return 0
  }
  return ((props.status.currentStreak - 1) % 7) + 1
})
const activeDays = computed(() =>
  props.status.checkedToday ? cycleDay.value : Math.max(0, cycleDay.value - 1)
)
const nextDay = computed(() =>
  props.status.checkedToday ? 0 : Math.min(7, activeDays.value + 1)
)

watch(
  () => props.status.checkedToday,
  (value, previous) => {
    if (value && !previous) {
      stamped.value = true
      window.setTimeout(() => { stamped.value = false }, 700)
    }
  }
)
</script>

<template>
  <aside class="checkin-seal" :class="{ stamped }" aria-label="每日签到">
    <div class="checkin-seal-head">
      <span class="checkin-eyebrow">
        <el-icon><Medal /></el-icon>
        今日签到
      </span>
      <span class="checkin-state">{{ status.checkedToday ? '已完成' : `待签到 +${status.todayReward}` }}</span>
    </div>

    <div class="seal-stage">
      <div class="seal-ring" aria-hidden="true">
        <span v-for="day in 7" :key="day" class="seal-tick" :class="{ active: day <= activeDays }" />
      </div>
      <div class="seal-core">
        <span class="seal-number">{{ status.currentStreak }}</span>
        <span class="seal-unit">连续签到 · 天</span>
      </div>
    </div>

    <div class="seal-days" aria-hidden="true">
      <span
        v-for="day in 7"
        :key="'day-' + day"
        class="seal-day"
        :class="{ active: day <= activeDays, next: day === nextDay }"
      >
        {{ day }}
      </span>
    </div>

    <div class="checkin-seal-foot">
      <span class="checkin-note">
        {{ status.checkedToday
          ? `明天签到可得 ${status.nextReward} 积分`
          : `连续第 7 天额外 +30，今天可得 ${status.todayReward}` }}
      </span>
      <el-button
        type="primary"
        :icon="status.checkedToday ? Check : Medal"
        :loading="checking"
        :disabled="status.checkedToday"
        @click="emit('checkin')"
      >
        {{ status.checkedToday ? '今日已签' : '立即签到' }}
      </el-button>
    </div>
  </aside>
</template>

<style scoped>
.checkin-seal {
  position: relative;
  display: grid;
  align-content: center;
  gap: 12px;
  height: 100%;
  min-height: 212px;
  padding: 24px 28px;
  background: var(--white);
  border-left: 1px solid var(--rule);
}

.checkin-seal::before {
  position: absolute;
  inset: 16px;
  pointer-events: none;
  content: '';
  border: 1px solid var(--rule);
}

.checkin-seal-head,
.checkin-seal-foot {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.checkin-eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--primary-deep);
  font-size: 12px;
  font-weight: 700;
}

.checkin-state {
  color: var(--ink-soft);
  font-size: 12px;
}

.seal-stage {
  position: relative;
  display: grid;
  width: 116px;
  height: 116px;
  margin: 2px auto;
  place-items: center;
}

.seal-ring {
  position: absolute;
  inset: 0;
  border: 1px solid var(--rule-strong);
  border-radius: 50%;
}

.seal-tick {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 6px;
  height: 6px;
  background: var(--rule-strong);
  border-radius: 50%;
  transform: rotate(calc((var(--n, 1) - 1) * 51.43deg)) translateY(-52px);
}

.seal-tick:nth-child(1) { --n: 1; }
.seal-tick:nth-child(2) { --n: 2; }
.seal-tick:nth-child(3) { --n: 3; }
.seal-tick:nth-child(4) { --n: 4; }
.seal-tick:nth-child(5) { --n: 5; }
.seal-tick:nth-child(6) { --n: 6; }
.seal-tick:nth-child(7) { --n: 7; }

.seal-tick.active {
  background: var(--jade);
}

.seal-core {
  display: grid;
  width: 88px;
  height: 88px;
  place-items: center;
  align-content: center;
  background: var(--paper);
  border: 1px solid var(--primary-soft);
  border-radius: 50%;
}

.seal-number {
  color: var(--primary-deep);
  font-family: var(--font-numeric);
  font-size: 38px;
  font-weight: 800;
  line-height: 1;
}

.seal-unit {
  margin-top: 4px;
  color: var(--ink-soft);
  font-size: 10px;
}

.seal-days {
  position: relative;
  z-index: 1;
  display: flex;
  justify-content: center;
  gap: 6px;
}

.seal-day {
  display: inline-grid;
  width: 22px;
  height: 22px;
  place-items: center;
  color: var(--ink-soft);
  font-family: var(--font-numeric);
  font-size: 10px;
  background: var(--paper);
  border: 1px solid var(--rule);
  border-radius: 50%;
}

.seal-day.active {
  color: var(--white);
  background: var(--jade);
  border-color: var(--jade);
}

.seal-day.next {
  color: var(--primary-deep);
  border-color: var(--primary);
}

.checkin-note {
  color: var(--ink-soft);
  font-size: 11px;
  line-height: 1.5;
}

.checkin-seal-foot :deep(.el-button) {
  flex: 0 0 auto;
}

.stamped .seal-core {
  animation: seal-stamp 680ms cubic-bezier(0.2, 0.9, 0.3, 1.2);
}

@keyframes seal-stamp {
  0% { transform: scale(1.18); box-shadow: 0 0 0 0 rgba(34, 160, 107, 0.28); }
  55% { transform: scale(0.96); }
  100% { transform: scale(1); box-shadow: 0 0 0 16px rgba(34, 160, 107, 0); }
}

@media (max-width: 900px) {
  .checkin-seal {
    border-top: 1px solid var(--rule);
    border-left: 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .stamped .seal-core { animation: none; }
}
</style>
