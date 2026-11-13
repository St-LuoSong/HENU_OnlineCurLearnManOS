<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { computed, reactive, ref } from 'vue'

import type { GrowthWorkspace } from '@/api/growth'
import { completeTask, createTask, reopenTask, type Task } from '@/api/learning'
import { formatDateTime } from '@/utils/date'

const props = defineProps<{
  workspace: GrowthWorkspace
  readOnly: boolean
}>()

const emit = defineEmits<{
  (event: 'changed'): void
}>()

const drawerVisible = ref(false)
const saving = ref(false)
const form = reactive({
  goalId: undefined as number | undefined,
  title: '',
  description: '',
  dueAt: '',
  priority: 'MEDIUM'
})

const tasks = computed(() => [
  ...props.workspace.todayTasks,
  ...props.workspace.upcomingTasks.filter((task) => !props.workspace.todayTasks.some((item) => item.id === task.id))
])

function openCreate(): void {
  Object.assign(form, { goalId: undefined, title: '', description: '', dueAt: '', priority: 'MEDIUM' })
  drawerVisible.value = true
}

async function toggle(task: Task): Promise<void> {
  if (props.readOnly) return
  try {
    if (task.status === 'COMPLETED') {
      await reopenTask(task.id)
    } else {
      await completeTask(task.id)
    }
    emit('changed')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '任务状态更新失败')
  }
}

async function save(): Promise<void> {
  if (!form.title.trim()) {
    ElMessage.warning('请输入任务标题')
    return
  }
  saving.value = true
  try {
    await createTask({
      goalId: form.goalId,
      title: form.title.trim(),
      description: form.description.trim() || undefined,
      dueAt: form.dueAt || undefined,
      priority: form.priority
    })
    drawerVisible.value = false
    ElMessage.success('任务已创建')
    emit('changed')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '任务创建失败')
  } finally {
    saving.value = false
  }
}

function isOverdue(task: Task): boolean {
  return task.status !== 'COMPLETED' && Boolean(task.dueAt) && new Date(task.dueAt as string).getTime() < Date.now()
}
</script>

<template>
  <section class="action-stage">
    <header class="stage-head">
      <div>
        <h2>今天只推进下一步</h2>
        <p>完成任务会进入本周证据，并自动反映到右侧周期进度。</p>
      </div>
      <el-button v-if="!readOnly" type="primary" @click="openCreate">新建任务</el-button>
    </header>

    <div class="action-summary">
      <span>待完成 {{ workspace.summary.openTasks }} 个</span>
      <span class="danger">逾期 {{ workspace.summary.overdueTasks }} 个</span>
      <span>未来 7 天到期 {{ workspace.summary.dueThisWeek }} 个</span>
    </div>

    <div v-if="tasks.length === 0" class="stage-empty">
      <strong>当前没有待行动项</strong>
      <span>从目标拆出一个 30 分钟内能开始的动作，或者去计划阶段生成一份执行路径。</span>
    </div>
    <article v-for="task in tasks" :key="task.id" class="action-row" :class="{ done: task.status === 'COMPLETED' }">
      <button type="button" class="action-check" :disabled="readOnly" @click="toggle(task)">
        {{ task.status === 'COMPLETED' ? '✓' : '' }}
      </button>
      <div class="action-copy">
        <div class="action-title">{{ task.title }}</div>
        <div class="action-meta">
          {{ task.goalTitle || '周期行动' }} · {{ formatDateTime(task.dueAt) }}
          <span v-if="isOverdue(task)" class="danger"> · 已逾期</span>
        </div>
      </div>
      <span class="action-priority">{{ task.priority === 'HIGH' ? '高' : task.priority === 'LOW' ? '低' : '中' }}</span>
    </article>

    <el-drawer v-model="drawerVisible" title="新建行动任务" size="420px">
      <el-form label-position="top">
        <el-form-item label="关联目标（可选）">
          <el-select v-model="form.goalId" clearable placeholder="独立任务" style="width: 100%">
            <el-option v-for="goal in workspace.goals" :key="goal.id" :label="goal.title" :value="goal.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="任务标题" required>
          <el-input v-model="form.title" maxlength="255" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="1000" />
        </el-form-item>
        <el-form-item label="截止时间">
          <el-date-picker v-model="form.dueAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width: 100%" />
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
        <el-button type="primary" :loading="saving" @click="save">创建任务</el-button>
      </template>
    </el-drawer>
  </section>
</template>

<style scoped>
.action-stage { display: grid; gap: 16px; padding-top: 18px; }
.stage-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; padding-bottom: 12px; border-bottom: 1px solid var(--rule); }
.stage-head h2 { margin: 0; font-family: var(--font-serif); font-size: 22px; }
.stage-head p { margin: 6px 0 0; color: var(--ink-soft); font-size: 12px; }
.action-summary { display: flex; flex-wrap: wrap; gap: 12px 20px; color: var(--ink-soft); font-size: 10px; }
.danger { color: var(--cinnabar); }
.action-row { display: grid; grid-template-columns: 24px minmax(0, 1fr) 28px; gap: 10px; align-items: center; padding: 13px 0; border-bottom: 1px solid var(--rule); }
.action-row.done { opacity: 0.55; }
.action-check { display: grid; width: 20px; height: 20px; place-items: center; color: var(--white); background: transparent; border: 1px solid var(--rule-strong); border-radius: 50%; cursor: pointer; }
.action-row.done .action-check { background: var(--jade); border-color: var(--jade); }
.action-copy { display: grid; gap: 4px; }
.action-title { color: var(--ink); font-size: 13px; font-weight: 700; }
.action-meta { color: var(--ink-soft); font-size: 10px; }
.action-priority { color: var(--ink-soft); font-size: 10px; text-align: right; }
.stage-empty { display: grid; gap: 5px; padding: 26px 0; color: var(--ink-soft); font-size: 12px; }
.stage-empty strong { color: var(--ink); font-family: var(--font-serif); font-size: 16px; }
@media (max-width: 620px) { .stage-head { flex-direction: column; } }
</style>
