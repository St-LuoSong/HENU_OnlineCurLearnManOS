<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, ref } from 'vue'

import type { GrowthWorkspace } from '@/api/growth'
import {
  convertLearningPlanItem,
  generateLearningPlan,
  moveLearningPlanItem,
  updateLearningPlanItem,
  updateLearningPlanItemStatus,
  type LearningPlan,
  type LearningPlanItem
} from '@/api/learningPlan'

const props = defineProps<{
  workspace: GrowthWorkspace
  readOnly: boolean
}>()

const emit = defineEmits<{
  (event: 'changed'): void
  (event: 'activate', plan: LearningPlan): void
}>()

const drawerVisible = ref(false)
const generating = ref(false)
const requirement = ref('')
const preview = ref<LearningPlan | null>(null)
const editVisible = ref(false)
const editingItem = ref<LearningPlanItem | null>(null)
const itemForm = ref({ title: '', description: '', startDate: '', endDate: '' })
const currentPlan = computed(() => props.workspace.currentPlan)
const openItems = computed(() => currentPlan.value?.items.filter((item) => item.status !== 'DONE') ?? [])

async function generate(): Promise<void> {
  if (!requirement.value.trim()) {
    ElMessage.warning('请描述学习目标和时间安排')
    return
  }
  try {
    await ElMessageBox.confirm(
      '本次 AI 生成会按个人中心当前模型扣除积分，生成结果会先作为草稿预览，确认采纳后才成为当前计划。',
      '生成学习计划',
      { type: 'warning' }
    )
  } catch {
    return
  }
  generating.value = true
  try {
    preview.value = await generateLearningPlan(requirement.value.trim())
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '学习计划生成失败')
  } finally {
    generating.value = false
  }
}

async function toggle(item: LearningPlanItem): Promise<void> {
  if (!currentPlan.value || props.readOnly) return
  try {
    await updateLearningPlanItemStatus(
      currentPlan.value.id,
      item.id,
      item.status === 'DONE' ? 'TODO' : 'DONE'
    )
    emit('changed')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '计划条目更新失败')
  }
}

async function convert(item: LearningPlanItem, target: 'TASK' | 'GOAL'): Promise<void> {
  if (!currentPlan.value || props.readOnly) return
  try {
    await convertLearningPlanItem(currentPlan.value.id, item.id, target)
    ElMessage.success(target === 'TASK' ? '已转为学习任务' : '已转为学习目标')
    emit('changed')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '条目转换失败')
  }
}

function openItemEdit(item: LearningPlanItem): void {
  editingItem.value = item
  itemForm.value = {
    title: item.title,
    description: item.description ?? '',
    startDate: item.startDate ?? '',
    endDate: item.endDate ?? ''
  }
  editVisible.value = true
}

async function saveItem(): Promise<void> {
  if (!currentPlan.value || !editingItem.value || !itemForm.value.title.trim()) return
  try {
    await updateLearningPlanItem(currentPlan.value.id, editingItem.value.id, {
      title: itemForm.value.title.trim(),
      description: itemForm.value.description.trim() || undefined,
      startDate: itemForm.value.startDate || undefined,
      endDate: itemForm.value.endDate || undefined
    })
    editVisible.value = false
    ElMessage.success('计划条目已更新')
    emit('changed')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '条目更新失败')
  }
}

async function move(item: LearningPlanItem, direction: 'UP' | 'DOWN'): Promise<void> {
  if (!currentPlan.value || props.readOnly) return
  try {
    await moveLearningPlanItem(currentPlan.value.id, item.id, direction)
    emit('changed')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '条目排序失败')
  }
}

function adoptPreview(): void {
  if (!preview.value) return
  emit('activate', preview.value)
  preview.value = null
  drawerVisible.value = false
}
</script>

<template>
  <section class="plan-stage">
    <header class="stage-head">
      <div>
        <h2>把目标拆成一条执行路径</h2>
        <p>一个周期只保留一份当前计划，新计划采纳后旧计划会自动归档。</p>
      </div>
      <el-button v-if="!readOnly" type="primary" @click="drawerVisible = true">AI 生成计划</el-button>
    </header>

    <template v-if="currentPlan">
      <div class="plan-summary">
        <div>
          <span class="plan-kicker">当前计划</span>
          <strong>{{ currentPlan.title }}</strong>
          <p>{{ currentPlan.summary || '这份计划还没有摘要。' }}</p>
        </div>
        <div class="plan-progress">
          <strong>{{ currentPlan.progress }}%</strong>
          <span>{{ currentPlan.completedItemCount }}/{{ currentPlan.totalItemCount }} 完成</span>
        </div>
      </div>
      <div class="plan-item-list">
        <article v-for="item in currentPlan.items" :key="item.id" class="plan-item" :class="{ done: item.status === 'DONE' }">
          <button type="button" class="plan-check" :disabled="readOnly" @click="toggle(item)">
            {{ item.status === 'DONE' ? '✓' : '' }}
          </button>
          <div class="plan-item-copy">
            <div class="plan-item-title">{{ item.title }}</div>
            <div class="plan-item-meta">
              {{ item.startDate || '未设置开始' }} — {{ item.endDate || '未设置结束' }}
              <template v-if="item.goalId"> · 已关联目标</template>
            </div>
            <p v-if="item.description">{{ item.description }}</p>
          </div>
          <div v-if="!readOnly" class="plan-item-actions">
            <el-button link @click="openItemEdit(item)">编辑</el-button>
            <el-button link @click="convert(item, 'TASK')">转为任务</el-button>
            <el-button link @click="convert(item, 'GOAL')">转为目标</el-button>
            <el-button link @click="move(item, 'UP')">上移</el-button>
            <el-button link @click="move(item, 'DOWN')">下移</el-button>
          </div>
        </article>
        <div v-if="currentPlan.items.length === 0" class="stage-empty">计划还没有条目。</div>
      </div>
    </template>

    <div v-else class="stage-empty plan-empty">
      <strong>当前周期还没有计划</strong>
      <span>先明确目标和时间，再生成一份按周推进的计划；也可以手动把计划条目转成任务。</span>
      <el-button v-if="!readOnly" type="primary" plain @click="drawerVisible = true">生成第一份计划</el-button>
    </div>

    <div class="plan-stats">
      <div><strong>{{ openItems.length }}</strong><span>待完成条目</span></div>
      <div><strong>{{ workspace.summary.activePlans }}</strong><span>进行中计划</span></div>
      <div><strong>{{ workspace.summary.completedPlanItems }}</strong><span>累计完成条目</span></div>
    </div>

    <el-drawer v-model="drawerVisible" title="生成学习计划" size="500px">
      <p class="drawer-note">AI 会先返回一份草稿计划。你可以预览后再采纳，草稿不会自动成为当前计划。</p>
      <el-input v-model="requirement" type="textarea" :rows="5" maxlength="2000" placeholder="例如：六周内复习高数上册，重点掌握导数和积分，每周 6 小时" />
      <el-button class="drawer-primary" type="primary" :loading="generating" @click="generate">
        生成计划草稿
      </el-button>
      <div v-if="preview" class="preview-plan">
        <h4>{{ preview.title }}</h4>
        <p>{{ preview.summary || '暂无摘要' }}</p>
        <div v-for="item in preview.items" :key="item.id" class="preview-item">
          <strong>{{ item.title }}</strong>
          <span>{{ item.startDate || '未设置开始' }} — {{ item.endDate || '未设置结束' }}</span>
        </div>
        <el-button type="primary" @click="adoptPreview">采纳为当前计划</el-button>
      </div>
    </el-drawer>

    <el-drawer v-model="editVisible" title="编辑计划条目" size="420px">
      <el-form label-position="top">
        <el-form-item label="条目标题" required>
          <el-input v-model="itemForm.title" maxlength="255" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="itemForm.description" type="textarea" :rows="4" maxlength="1000" />
        </el-form-item>
        <el-form-item label="开始日期">
          <el-date-picker v-model="itemForm.startDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束日期">
          <el-date-picker v-model="itemForm.endDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" @click="saveItem">保存条目</el-button>
      </template>
    </el-drawer>
  </section>
</template>

<style scoped>
.plan-stage { display: grid; gap: 16px; padding-top: 18px; }
.stage-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; padding-bottom: 12px; border-bottom: 1px solid var(--rule); }
.stage-head h2 { margin: 0; font-family: var(--font-serif); font-size: 22px; }
.stage-head p { margin: 6px 0 0; color: var(--ink-soft); font-size: 12px; }
.plan-summary { display: flex; justify-content: space-between; gap: 20px; padding: 16px 0; border-bottom: 1px solid var(--rule); }
.plan-kicker { color: var(--primary-deep); font-size: 10px; }
.plan-summary strong { display: block; margin-top: 5px; color: var(--ink); font-family: var(--font-serif); font-size: 19px; }
.plan-summary p { max-width: 620px; margin: 5px 0 0; color: var(--ink-soft); font-size: 11px; line-height: 1.7; }
.plan-progress { display: grid; align-content: center; justify-items: end; min-width: 100px; }
.plan-progress strong { color: var(--primary-deep); font-family: var(--font-numeric); font-size: 24px; }
.plan-progress span { color: var(--ink-soft); font-size: 10px; }
.plan-item-list { display: grid; }
.plan-item { display: grid; grid-template-columns: 24px minmax(0, 1fr) auto; gap: 10px; align-items: start; padding: 12px 0; border-bottom: 1px solid var(--rule); }
.plan-item.done { opacity: 0.58; }
.plan-check { display: grid; width: 20px; height: 20px; margin-top: 2px; place-items: center; color: var(--white); background: transparent; border: 1px solid var(--rule-strong); border-radius: 50%; cursor: pointer; }
.plan-item.done .plan-check { background: var(--jade); border-color: var(--jade); }
.plan-item-copy { display: grid; min-width: 0; gap: 3px; }
.plan-item-title { color: var(--ink); font-size: 13px; font-weight: 700; }
.plan-item-meta { color: var(--ink-soft); font-size: 10px; }
.plan-item-copy p { margin: 2px 0 0; color: var(--ink-soft); font-size: 11px; line-height: 1.6; }
.plan-item-actions { display: flex; flex-wrap: wrap; gap: 3px; }
.plan-empty { display: grid; gap: 8px; padding: 26px 0; justify-items: start; }
.plan-stats { display: flex; flex-wrap: wrap; gap: 24px; padding-top: 14px; border-top: 1px solid var(--rule); }
.plan-stats div { display: grid; gap: 3px; }
.plan-stats strong { color: var(--ink); font-family: var(--font-numeric); font-size: 20px; }
.plan-stats span { color: var(--ink-soft); font-size: 10px; }
.stage-empty { color: var(--ink-soft); font-size: 12px; line-height: 1.7; }
.stage-empty strong { color: var(--ink); font-family: var(--font-serif); font-size: 16px; }
.drawer-note { color: var(--ink-soft); font-size: 12px; line-height: 1.7; }
.drawer-primary { width: 100%; margin: 12px 0; }
.preview-plan { display: grid; gap: 8px; padding-top: 12px; border-top: 1px solid var(--rule); }
.preview-plan h4 { margin: 0; font-family: var(--font-serif); font-size: 18px; }
.preview-plan p { margin: 0; color: var(--ink-soft); font-size: 11px; }
.preview-item { display: flex; justify-content: space-between; gap: 10px; padding: 7px 0; border-bottom: 1px solid var(--rule); font-size: 11px; }
.preview-item span { color: var(--ink-soft); }
@media (max-width: 620px) { .stage-head { flex-direction: column; } .plan-summary { flex-direction: column; } .plan-progress { justify-items: start; } .plan-item { grid-template-columns: 24px minmax(0, 1fr); } .plan-item-actions { grid-column: 2; } }
</style>
