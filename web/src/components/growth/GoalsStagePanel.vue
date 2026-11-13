<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'

import { listSubjects, type Subject } from '@/api/content'
import {
  completeGoal,
  createGoal,
  deleteGoal,
  generateGoals,
  updateGoal,
  type Goal,
  type GoalSuggestion
} from '@/api/learning'
import type { GrowthWorkspace } from '@/api/growth'

const props = defineProps<{
  workspace: GrowthWorkspace
  readOnly: boolean
}>()

const emit = defineEmits<{
  (event: 'changed'): void
}>()

const subjects = ref<Subject[]>([])
const drawerVisible = ref(false)
const aiVisible = ref(false)
const saving = ref(false)
const generating = ref(false)
const aiRequirement = ref('')
const suggestions = ref<GoalSuggestion[]>([])
const editingId = ref<number | null>(null)
const form = reactive({
  subjectId: undefined as number | undefined,
  title: '',
  description: '',
  targetDate: '',
  priority: 'MEDIUM'
})

const goals = computed(() => props.workspace.goals)
const activeGoals = computed(() => goals.value.filter((goal) => goal.status === 'ACTIVE'))

onMounted(async () => {
  try {
    subjects.value = await listSubjects()
  } catch {
    subjects.value = []
  }
})

function openCreate(): void {
  editingId.value = null
  Object.assign(form, {
    subjectId: undefined,
    title: '',
    description: '',
    targetDate: '',
    priority: 'MEDIUM'
  })
  drawerVisible.value = true
}

function openEdit(goal: Goal): void {
  if (props.readOnly) return
  editingId.value = goal.id
  Object.assign(form, {
    subjectId: goal.subjectId,
    title: goal.title,
    description: goal.description ?? '',
    targetDate: goal.targetDate ?? '',
    priority: goal.priority
  })
  drawerVisible.value = true
}

async function saveGoal(): Promise<void> {
  if (!form.title.trim()) {
    ElMessage.warning('请输入目标标题')
    return
  }
  saving.value = true
  try {
    const payload = {
      subjectId: form.subjectId,
      title: form.title.trim(),
      description: form.description.trim() || undefined,
      targetDate: form.targetDate || undefined,
      priority: form.priority
    }
    if (editingId.value) {
      await updateGoal(editingId.value, payload)
    } else {
      await createGoal(payload)
    }
    drawerVisible.value = false
    ElMessage.success(editingId.value ? '目标已更新' : '目标已创建')
    emit('changed')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '目标保存失败')
  } finally {
    saving.value = false
  }
}

async function finishGoal(goal: Goal): Promise<void> {
  try {
    await ElMessageBox.confirm(`确认将目标“${goal.title}”标记为完成吗？`, '完成目标', {
      type: 'warning'
    })
    await completeGoal(goal.id)
    ElMessage.success('目标已完成')
    emit('changed')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error instanceof Error ? error.message : '目标状态更新失败')
    }
  }
}

async function removeGoal(goal: Goal): Promise<void> {
  try {
    await ElMessageBox.confirm(`确认删除目标“${goal.title}”吗？`, '删除目标', { type: 'warning' })
    await deleteGoal(goal.id)
    ElMessage.success('目标已删除')
    emit('changed')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error instanceof Error ? error.message : '目标删除失败')
    }
  }
}

async function generateSuggestions(): Promise<void> {
  if (!aiRequirement.value.trim()) {
    ElMessage.warning('请描述你的学习需求')
    return
  }
  try {
    await ElMessageBox.confirm(
      '本次 AI 目标建议会按个人中心当前模型扣除积分，生成的建议不会自动创建目标。',
      '生成目标建议',
      { type: 'warning' }
    )
  } catch {
    return
  }
  generating.value = true
  try {
    suggestions.value = await generateGoals(aiRequirement.value.trim())
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'AI 目标生成失败')
  } finally {
    generating.value = false
  }
}

async function adopt(suggestion: GoalSuggestion): Promise<void> {
  try {
    await createGoal({
      title: suggestion.title,
      description: suggestion.description,
      targetDate: suggestion.targetDate,
      priority: suggestion.priority
    })
    suggestions.value = suggestions.value.filter((item) => item.title !== suggestion.title)
    ElMessage.success('目标已采纳')
    emit('changed')
  } catch {
    ElMessage.error('目标采纳失败')
  }
}

function priorityLabel(priority: string): string {
  return { HIGH: '高优先级', MEDIUM: '中优先级', LOW: '低优先级' }[priority] ?? priority
}
</script>

<template>
  <section class="goals-stage">
    <header class="stage-head">
      <div>
        <h2>把方向变成可衡量的目标</h2>
        <p>目标负责定义结果，任务负责下一步行动。一个周期可以有多个目标。</p>
      </div>
      <div v-if="!readOnly" class="stage-actions">
        <el-button @click="aiVisible = true">AI 目标建议</el-button>
        <el-button type="primary" @click="openCreate">新建目标</el-button>
      </div>
    </header>

    <div class="goal-summary">
      <span>{{ activeGoals.length }} 个进行中目标</span>
      <span>{{ goals.reduce((sum, goal) => sum + goal.taskCount, 0) }} 个关联任务</span>
      <span>{{ goals.reduce((sum, goal) => sum + goal.completedTaskCount, 0) }} 个已完成</span>
    </div>

    <div v-if="goals.length === 0" class="stage-empty">
      <strong>当前周期还没有目标</strong>
      <span>先建立一个能在本周期结束前验收的目标，再拆成任务。</span>
    </div>
    <button
      v-for="goal in goals"
      :key="goal.id"
      type="button"
      class="goal-row"
      :class="{ completed: goal.status === 'COMPLETED' }"
      @click="openEdit(goal)"
    >
      <span class="goal-status" :class="goal.status.toLowerCase()" />
      <span class="goal-main">
        <span class="goal-title-row">
          <strong>{{ goal.title }}</strong>
          <span>{{ goal.status === 'COMPLETED' ? '已完成' : priorityLabel(goal.priority) }}</span>
        </span>
        <span class="goal-meta">
          {{ goal.subjectName || '通用' }} · {{ goal.completedTaskCount }}/{{ goal.taskCount }} 个任务
          <template v-if="goal.targetDate"> · 截止 {{ goal.targetDate }}</template>
        </span>
        <span class="goal-track"><i :style="{ width: goal.progress + '%' }" /></span>
      </span>
      <span class="goal-progress">{{ goal.progress }}%</span>
      <span v-if="!readOnly" class="goal-actions">
        <el-button
          v-if="goal.status === 'ACTIVE'"
          link
          type="primary"
          @click.stop="finishGoal(goal)"
        >
          完成
        </el-button>
        <el-button link type="danger" @click.stop="removeGoal(goal)">删除</el-button>
      </span>
    </button>

    <el-drawer v-model="drawerVisible" :title="editingId ? '编辑目标' : '新建目标'" size="420px">
      <el-form label-position="top">
        <el-form-item label="课程（可选）">
          <el-select v-model="form.subjectId" clearable placeholder="选择课程" style="width: 100%">
            <el-option v-for="subject in subjects" :key="subject.id" :label="subject.name" :value="subject.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标标题" required>
          <el-input v-model="form.title" maxlength="255" />
        </el-form-item>
        <el-form-item label="完成标准">
          <el-input v-model="form.description" type="textarea" :rows="4" maxlength="1000" />
        </el-form-item>
        <el-form-item label="目标日期">
          <el-date-picker v-model="form.targetDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="优先级">
          <el-radio-group v-model="form.priority">
            <el-radio-button label="HIGH">高</el-radio-button>
            <el-radio-button label="MEDIUM">中</el-radio-button>
            <el-radio-button label="LOW">低</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="drawerVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveGoal">保存目标</el-button>
      </template>
    </el-drawer>

    <el-drawer v-model="aiVisible" title="AI 目标建议" size="460px">
      <p class="drawer-note">AI 只生成建议，采纳后才会创建目标。调用会按个人中心当前模型扣除积分。</p>
      <el-input v-model="aiRequirement" type="textarea" :rows="5" maxlength="2000" placeholder="例如：三个月内通过线性代数补考，每周投入 5 小时" />
      <el-button class="drawer-primary" type="primary" :loading="generating" @click="generateSuggestions">
        生成建议
      </el-button>
      <div v-for="suggestion in suggestions" :key="suggestion.title" class="suggestion-row">
        <div>
          <strong>{{ suggestion.title }}</strong>
          <p>{{ suggestion.description }}</p>
        </div>
        <el-button link type="primary" @click="adopt(suggestion)">采纳</el-button>
      </div>
    </el-drawer>
  </section>
</template>

<style scoped>
.goals-stage { display: grid; gap: 16px; padding-top: 18px; }
.stage-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; padding-bottom: 12px; border-bottom: 1px solid var(--rule); }
.stage-head h2 { margin: 0; font-family: var(--font-serif); font-size: 22px; }
.stage-head p { margin: 6px 0 0; color: var(--ink-soft); font-size: 12px; }
.stage-actions { display: flex; gap: 8px; }
.goal-summary { display: flex; flex-wrap: wrap; gap: 10px 20px; color: var(--ink-soft); font-size: 10px; }
.goal-row { display: grid; grid-template-columns: 5px minmax(0, 1fr) 42px auto; gap: 12px; align-items: center; width: 100%; padding: 13px 0; text-align: left; background: transparent; border: 0; border-bottom: 1px solid var(--rule); cursor: pointer; }
.goal-row:hover .goal-title-row strong { color: var(--primary-deep); }
.goal-status { width: 5px; height: 100%; min-height: 42px; background: var(--jade); }
.goal-status.completed { background: var(--ink-faint); }
.goal-main { display: grid; min-width: 0; gap: 4px; }
.goal-title-row { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; }
.goal-title-row strong { overflow: hidden; color: var(--ink); font-family: var(--font-serif); font-size: 16px; text-overflow: ellipsis; white-space: nowrap; }
.goal-title-row span, .goal-meta { color: var(--ink-soft); font-size: 10px; }
.goal-track { display: block; height: 4px; overflow: hidden; background: var(--paper-deep); }
.goal-track i { display: block; height: 4px; background: var(--primary); transition: width 360ms ease; }
.goal-progress { color: var(--primary-deep); font-family: var(--font-numeric); font-size: 16px; text-align: right; }
.goal-actions { display: flex; gap: 2px; }
.goal-row.completed { opacity: 0.68; }
.stage-empty { display: grid; gap: 5px; padding: 26px 0; color: var(--ink-soft); font-size: 12px; }
.stage-empty strong { color: var(--ink); font-family: var(--font-serif); font-size: 16px; }
.drawer-note { color: var(--ink-soft); font-size: 12px; line-height: 1.7; }
.drawer-primary { width: 100%; margin: 12px 0; }
.suggestion-row { display: flex; align-items: flex-start; justify-content: space-between; gap: 10px; padding: 12px 0; border-bottom: 1px solid var(--rule); }
.suggestion-row strong { color: var(--ink); font-size: 13px; }
.suggestion-row p { margin: 4px 0 0; color: var(--ink-soft); font-size: 11px; line-height: 1.6; }
@media (max-width: 620px) { .stage-head { flex-direction: column; } .goal-row { grid-template-columns: 5px minmax(0, 1fr) 36px; } .goal-row .el-button { grid-column: 2; justify-self: start; } }
</style>
