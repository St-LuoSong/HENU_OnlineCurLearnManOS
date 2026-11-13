<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { computed, ref, watch } from 'vue'

import type { GrowthReview, GrowthWorkspace } from '@/api/growth'

const props = defineProps<{
  workspace: GrowthWorkspace
  readOnly: boolean
}>()

const emit = defineEmits<{
  (event: 'save', review: GrowthReview, note: string): void
}>()

const selected = ref<GrowthReview | null>(
  props.workspace.currentReview ?? props.workspace.reviews[0] ?? null
)
const note = ref(selected.value?.note ?? '')
const saving = ref(false)

watch(
  () => props.workspace.currentReview,
  (value) => {
    if (!selected.value && value) {
      selected.value = value
      note.value = value.note ?? ''
    }
  },
  { immediate: true }
)

const evidence = computed(() => {
  if (!selected.value?.evidenceJson) return null
  try {
    return JSON.parse(selected.value.evidenceJson) as {
      weekStart?: string
      weekEnd?: string
      completedGoals?: string[]
      completedTasks?: string[]
      completedPlanItems?: string[]
      learningRecordCount?: number
      scoredRecordCount?: number
      averageScore?: number
    }
  } catch {
    return null
  }
})

function pick(review: GrowthReview): void {
  selected.value = review
  note.value = review.note ?? ''
}

async function save(): Promise<void> {
  if (!selected.value || props.readOnly) return
  saving.value = true
  try {
    emit('save', selected.value, note.value.trim())
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <section class="review-stage">
    <header class="stage-head">
      <div>
        <h2>留下证据，再做判断</h2>
        <p>每周自动汇总完成目标、任务和学习记录，复盘文字由你自己补充。</p>
      </div>
      <span class="review-week" v-if="selected">{{ selected.weekStart }} — {{ selected.weekEnd }}</span>
    </header>

    <div class="review-layout">
      <nav class="review-list">
        <button
          v-for="item in workspace.reviews"
          :key="item.id"
          type="button"
          class="review-entry"
          :class="{ active: selected?.id === item.id }"
          @click="pick(item)"
        >
          <strong>{{ item.weekStart.slice(5) }} — {{ item.weekEnd.slice(5) }}</strong>
          <span>{{ item.note || '未填写备注' }}</span>
        </button>
        <div v-if="workspace.reviews.length === 0" class="review-list-empty">还没有复盘记录。</div>
      </nav>

      <div class="review-detail">
        <div v-if="evidence" class="evidence-block">
          <span class="review-label">自动证据</span>
          <div class="evidence-grid">
            <div><strong>{{ evidence.completedGoals?.length ?? 0 }}</strong><span>完成目标</span></div>
            <div><strong>{{ evidence.completedTasks?.length ?? 0 }}</strong><span>完成任务</span></div>
            <div><strong>{{ evidence.completedPlanItems?.length ?? 0 }}</strong><span>计划条目</span></div>
            <div><strong>{{ evidence.learningRecordCount ?? 0 }}</strong><span>学习记录</span></div>
            <div><strong>{{ Math.round(evidence.averageScore ?? 0) }}</strong><span>平均分</span></div>
          </div>
          <div v-if="evidence.completedTasks?.length" class="evidence-list">
            <span>任务：</span>{{ evidence.completedTasks.join('、') }}
          </div>
        </div>
        <div v-else class="stage-empty">本周还没有可汇总的证据。</div>

        <label class="review-label" for="growth-review-note">本周复盘</label>
        <el-input
          id="growth-review-note"
          v-model="note"
          type="textarea"
          :rows="6"
          maxlength="2000"
          show-word-limit
          :disabled="readOnly || !selected"
          placeholder="写下本周最重要的推进、阻碍和下一步。"
        />
        <div class="review-actions">
          <span v-if="readOnly">历史周期复盘只读</span>
          <el-button v-else type="primary" :loading="saving" :disabled="!selected" @click="save">
            保存本周复盘
          </el-button>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.review-stage { display: grid; gap: 18px; padding-top: 18px; }
.stage-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; padding-bottom: 12px; border-bottom: 1px solid var(--rule); }
.stage-head h2 { margin: 0; font-family: var(--font-serif); font-size: 22px; }
.stage-head p { margin: 6px 0 0; color: var(--ink-soft); font-size: 12px; }
.review-week { color: var(--ink-soft); font-family: var(--font-numeric); font-size: 10px; }
.review-layout { display: grid; grid-template-columns: 180px minmax(0, 1fr); gap: 22px; }
.review-list { display: grid; align-content: start; gap: 2px; border-right: 1px solid var(--rule); }
.review-entry { display: grid; gap: 4px; padding: 10px 10px 10px 0; text-align: left; background: transparent; border: 0; border-bottom: 1px solid var(--rule); cursor: pointer; }
.review-entry:hover, .review-entry.active { background: var(--paper); }
.review-entry strong { color: var(--ink); font-family: var(--font-numeric); font-size: 12px; }
.review-entry span { overflow: hidden; color: var(--ink-soft); font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
.review-list-empty, .stage-empty { color: var(--ink-soft); font-size: 12px; line-height: 1.7; }
.review-detail { display: grid; gap: 12px; }
.review-label { color: var(--primary-deep); font-size: 10px; font-weight: 700; }
.evidence-block { display: grid; gap: 10px; padding-bottom: 14px; border-bottom: 1px solid var(--rule); }
.evidence-grid { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 10px; }
.evidence-grid div { display: grid; gap: 3px; }
.evidence-grid strong { color: var(--ink); font-family: var(--font-numeric); font-size: 21px; }
.evidence-grid span, .evidence-list { color: var(--ink-soft); font-size: 10px; }
.evidence-list { line-height: 1.6; }
.review-actions { display: flex; align-items: center; justify-content: flex-end; gap: 12px; color: var(--ink-soft); font-size: 10px; }
@media (max-width: 760px) { .stage-head { flex-direction: column; } .review-layout { grid-template-columns: 1fr; } .review-list { grid-template-columns: repeat(2, minmax(0, 1fr)); border-right: 0; border-bottom: 1px solid var(--rule); } .evidence-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
</style>
