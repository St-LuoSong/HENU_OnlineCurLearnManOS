<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import {
  completeGoal,
  completeTask,
  createGoal,
  createTask,
  deleteGoal,
  deleteTask,
  generateGoals,
  listGoals,
  listTasks,
  reopenTask,
  updateGoal,
  updateTask,
  type Goal,
  type GoalSuggestion,
  type GoalSaveRequest,
  type Task,
  type TaskSaveRequest
} from '@/api/learning'
import { listSubjects, type Subject } from '@/api/content'

const loading = ref(false)
const savingGoal = ref(false)
const savingTask = ref(false)
const today = new Date().toLocaleDateString('zh-CN')
const goals = ref<Goal[]>([])
const tasks = ref<Task[]>([])
const subjects = ref<Subject[]>([])
const goalDialogVisible = ref(false)
const taskDialogVisible = ref(false)
const aiDialogVisible = ref(false)
const aiLoading = ref(false)
const aiRequirement = ref('')
const suggestions = ref<GoalSuggestion[]>([])
const editingGoalId = ref<number | null>(null)
const editingTaskId = ref<number | null>(null)

const goalForm = reactive({
  subjectId: undefined as number | undefined,
  title: '',
  description: '',
  targetDate: '',
  priority: 'MEDIUM'
})

const taskForm = reactive({
  goalId: undefined as number | undefined,
  title: '',
  description: '',
  category: '',
  dueAt: '',
  priority: 'MEDIUM'
})

const activeGoals = computed(() => goals.value.filter((goal) => goal.status === 'ACTIVE'))
const completedGoals = computed(() => goals.value.filter((goal) => goal.status === 'COMPLETED'))
const openTasks = computed(() => tasks.value.filter((task) => task.status !== 'COMPLETED'))
const independentTasks = computed(() => tasks.value.filter((task) => !task.goalId))
const priorityLabels: Record<string, string> = {
  HIGH: '高',
  MEDIUM: '中',
  LOW: '低'
}
const taskStatusLabels: Record<string, string> = {
  TODO: '待开始',
  IN_PROGRESS: '进行中',
  COMPLETED: '已完成',
  CANCELLED: '已取消'
}
const weeklyTasks = computed(() => {
  const end = new Date()
  end.setDate(end.getDate() + 7)
  return openTasks.value
    .filter((task) => task.dueAt && new Date(task.dueAt) <= end)
    .sort((left, right) => String(left.dueAt).localeCompare(String(right.dueAt)))
})

onMounted(async () => {
  await loadAll()
})

async function loadAll(): Promise<void> {
  loading.value = true
  try {
    const [subjectList, goalList, taskList] = await Promise.all([
      listSubjects(),
      listGoals(),
      listTasks({})
    ])
    subjects.value = subjectList
    goals.value = goalList
    tasks.value = taskList
  } catch {
    ElMessage.error('学习账本加载失败')
  } finally {
    loading.value = false
  }
}

async function reloadGoals(): Promise<void> {
  goals.value = await listGoals()
}

async function reloadTasks(): Promise<void> {
  tasks.value = await listTasks({})
}

function openCreateGoal(): void {
  editingGoalId.value = null
  goalForm.subjectId = undefined
  goalForm.title = ''
  goalForm.description = ''
  goalForm.targetDate = ''
  goalForm.priority = 'MEDIUM'
  goalDialogVisible.value = true
}

function openAiDialog(): void {
  aiRequirement.value = ''
  suggestions.value = []
  aiDialogVisible.value = true
}

async function generateGoalSuggestions(): Promise<void> {
  if (!aiRequirement.value.trim()) {
    ElMessage.warning('请描述你的学习需求')
    return
  }
  aiLoading.value = true
  try {
    suggestions.value = await generateGoals(aiRequirement.value.trim())
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'AI 生成目标失败')
  } finally {
    aiLoading.value = false
  }
}

async function adoptSuggestion(suggestion: GoalSuggestion): Promise<void> {
  try {
    await createGoal({
      title: suggestion.title,
      description: suggestion.description,
      targetDate: suggestion.targetDate,
      priority: suggestion.priority
    })
    ElMessage.success('目标已加入清单')
    suggestions.value = suggestions.value.filter((item) => item.title !== suggestion.title)
    await reloadGoals()
  } catch {
    ElMessage.error('目标保存失败')
  }
}

function openEditGoal(goal: Goal): void {
  editingGoalId.value = goal.id
  goalForm.subjectId = goal.subjectId
  goalForm.title = goal.title
  goalForm.description = goal.description ?? ''
  goalForm.targetDate = goal.targetDate ?? ''
  goalForm.priority = goal.priority
  goalDialogVisible.value = true
}

async function submitGoal(): Promise<void> {
  if (!goalForm.title.trim()) {
    ElMessage.warning('请输入目标标题')
    return
  }
  const payload: GoalSaveRequest = {
    subjectId: goalForm.subjectId,
    title: goalForm.title.trim(),
    description: goalForm.description.trim() || undefined,
    targetDate: goalForm.targetDate || undefined,
    priority: goalForm.priority
  }
  savingGoal.value = true
  try {
    if (editingGoalId.value) {
      await updateGoal(editingGoalId.value, payload)
    } else {
      await createGoal(payload)
    }
    ElMessage.success(editingGoalId.value ? '目标已更新' : '目标已建立')
    goalDialogVisible.value = false
    await reloadGoals()
  } catch {
    ElMessage.error('目标保存失败')
  } finally {
    savingGoal.value = false
  }
}

async function finishGoal(goal: Goal): Promise<void> {
  try {
    await completeGoal(goal.id)
    ElMessage.success('目标已完成')
    await loadAll()
  } catch {
    ElMessage.error('目标状态更新失败')
  }
}

async function removeGoal(goal: Goal): Promise<void> {
  try {
    await ElMessageBox.confirm(`确认删除目标“${goal.title}”及其任务吗？`, '删除目标', {
      type: 'warning'
    })
    await deleteGoal(goal.id)
    ElMessage.success('目标已删除')
    await loadAll()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error('目标删除失败')
    }
  }
}

function openCreateTask(goalId?: number): void {
  editingTaskId.value = null
  taskForm.goalId = goalId
  taskForm.title = ''
  taskForm.description = ''
  taskForm.category = ''
  taskForm.dueAt = ''
  taskForm.priority = 'MEDIUM'
  taskDialogVisible.value = true
}

function openEditTask(task: Task): void {
  editingTaskId.value = task.id
  taskForm.goalId = task.goalId
  taskForm.title = task.title
  taskForm.description = task.description ?? ''
  taskForm.category = task.category ?? ''
  taskForm.dueAt = task.dueAt ?? ''
  taskForm.priority = task.priority
  taskDialogVisible.value = true
}

async function submitTask(): Promise<void> {
  if (!taskForm.title.trim()) {
    ElMessage.warning('请输入任务标题')
    return
  }
  const payload: TaskSaveRequest = {
    goalId: taskForm.goalId,
    title: taskForm.title.trim(),
    description: taskForm.description.trim() || undefined,
    category: taskForm.category.trim() || undefined,
    dueAt: taskForm.dueAt || undefined,
    priority: taskForm.priority
  }
  savingTask.value = true
  try {
    if (editingTaskId.value) {
      await updateTask(editingTaskId.value, payload)
    } else {
      await createTask(payload)
    }
    ElMessage.success(editingTaskId.value ? '任务已更新' : '任务已加入账本')
    taskDialogVisible.value = false
    await reloadTasks()
    await reloadGoals()
  } catch {
    ElMessage.error('任务保存失败')
  } finally {
    savingTask.value = false
  }
}

async function toggleTask(task: Task): Promise<void> {
  try {
    if (task.status === 'COMPLETED') {
      await reopenTask(task.id)
    } else {
      await completeTask(task.id)
    }
    await reloadTasks()
    await reloadGoals()
  } catch {
    ElMessage.error('任务状态更新失败')
  }
}

async function removeTask(task: Task): Promise<void> {
  try {
    await ElMessageBox.confirm(`确认删除任务“${task.title}”吗？`, '删除任务', {
      type: 'warning'
    })
    await deleteTask(task.id)
    ElMessage.success('任务已删除')
    await reloadTasks()
    await reloadGoals()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error('任务删除失败')
    }
  }
}

function tasksOfGoal(goalId: number): Task[] {
  return tasks.value.filter((task) => task.goalId === goalId)
}

function priorityLabel(priority: string): string {
  return priorityLabels[priority] ?? priority
}

function taskStatusLabel(status: string): string {
  return taskStatusLabels[status] ?? status
}

function formatDate(value?: string): string {
  if (!value) {
    return '未设置'
  }
  return value.slice(0, 10)
}
</script>

<template>
  <div v-loading="loading" class="ledger-page">
    <header class="ledger-header">
      <div>
        <div class="ledger-kicker">学习账本</div>
        <h1 class="ledger-title">目标清单</h1>
        <p class="ledger-subtitle">
          把长期目标拆成可以执行的任务，每天只处理眼前这一行。
        </p>
      </div>
      <div class="ledger-date">
        今日 {{ today }}
      </div>
    </header>

    <div class="summary-strip">
      <div class="summary-item">
        <span class="summary-value">{{ activeGoals.length }}</span>
        <span class="summary-label">进行中的目标</span>
      </div>
      <div class="summary-item">
        <span class="summary-value">{{ completedGoals.length }}</span>
        <span class="summary-label">已完成的目标</span>
      </div>
      <div class="summary-item">
        <span class="summary-value">{{ openTasks.length }}</span>
        <span class="summary-label">待完成任务</span>
      </div>
      <div class="summary-item">
        <span class="summary-value">{{ weeklyTasks.length }}</span>
        <span class="summary-label">未来七天到期</span>
      </div>
      <div class="summary-item summary-actions">
        <el-button type="primary" @click="openCreateGoal">新建目标</el-button>
        <el-button @click="openAiDialog">AI 生成目标</el-button>
        <el-button @click="openCreateTask()">新建任务</el-button>
      </div>
    </div>

    <div class="ledger-grid">
      <section class="ledger-section">
        <h2 class="ledger-section-title">
          目标进度
          <small>按目标聚合任务完成情况</small>
        </h2>

        <div v-if="goals.length === 0" class="empty-ledger">
          还没有学习目标。先建立一个目标，再把第一步写成任务。
        </div>

        <article v-for="goal in goals" :key="goal.id" class="goal-entry">
          <div class="goal-entry-header">
            <div>
              <div class="goal-entry-title">{{ goal.title }}</div>
              <div class="goal-entry-meta">
                {{ goal.subjectName ?? '通用目标' }}
                · {{ formatDate(goal.targetDate) }}
                · 优先级 {{ priorityLabel(goal.priority) }}
              </div>
            </div>
            <div class="goal-actions">
              <el-button link type="primary" @click="openEditGoal(goal)">编辑</el-button>
              <el-button
                v-if="goal.status !== 'COMPLETED'"
                link
                type="success"
                @click="finishGoal(goal)"
              >
                完成
              </el-button>
              <el-button link type="danger" @click="removeGoal(goal)">删除</el-button>
            </div>
          </div>

          <div class="goal-progress">
            <div class="goal-progress-track">
              <div class="goal-progress-value" :style="{ width: `${goal.progress}%` }" />
            </div>
            <div class="goal-progress-text">{{ goal.progress }}%</div>
          </div>

          <div class="task-list">
            <div v-if="tasksOfGoal(goal.id).length === 0" class="empty-ledger">
              这个目标还没有任务。
            </div>
            <div v-for="task in tasksOfGoal(goal.id)" :key="task.id" class="task-line">
              <button
                class="task-check"
                :class="{ pending: task.status !== 'COMPLETED' }"
                :aria-label="task.status === 'COMPLETED' ? '重新打开任务' : '标记任务完成'"
                @click="toggleTask(task)"
              >
                {{ task.status === 'COMPLETED' ? '✓' : '' }}
              </button>
              <div>
                <div class="task-title" :class="{ done: task.status === 'COMPLETED' }">
                  {{ task.title }}
                </div>
                <div class="task-meta">
                  {{ taskStatusLabel(task.status) }}
                  · 截止 {{ formatDate(task.dueAt) }}
                  · 优先级 {{ priorityLabel(task.priority) }}
                </div>
              </div>
              <div class="task-actions">
                <el-button link type="primary" @click="openEditTask(task)">编辑</el-button>
                <el-button link type="danger" @click="removeTask(task)">删除</el-button>
              </div>
            </div>
            <el-button link type="primary" @click="openCreateTask(goal.id)">添加任务</el-button>
          </div>
        </article>
      </section>

      <aside class="ledger-section">
        <h2 class="ledger-section-title">
          未来七日
          <small>按截止时间排序</small>
        </h2>

        <div v-if="weeklyTasks.length === 0" class="empty-ledger">
          未来七天没有到期任务。可以从目标中拆出一个最小行动。
        </div>
        <div v-else class="week-rail">
          <div v-for="task in weeklyTasks" :key="task.id" class="week-row">
            <div class="week-day">{{ formatDate(task.dueAt).slice(5) }}</div>
            <div>
              <div class="task-title">{{ task.title }}</div>
              <div class="task-meta">{{ task.goalTitle ?? '独立任务' }}</div>
            </div>
            <div class="week-value">{{ priorityLabel(task.priority) }}</div>
          </div>
        </div>

        <h2 class="ledger-section-title" style="margin-top: 32px">
          独立任务
          <small>不归属于目标</small>
        </h2>
        <div v-if="independentTasks.length === 0" class="empty-ledger">
          没有独立任务。
        </div>
        <div v-for="task in independentTasks" :key="task.id" class="task-line">
          <button
            class="task-check"
            :class="{ pending: task.status !== 'COMPLETED' }"
            @click="toggleTask(task)"
          >
            {{ task.status === 'COMPLETED' ? '✓' : '' }}
          </button>
          <div>
            <div class="task-title" :class="{ done: task.status === 'COMPLETED' }">
              {{ task.title }}
            </div>
            <div class="task-meta">截止 {{ formatDate(task.dueAt) }}</div>
          </div>
          <div class="task-actions">
            <el-button link type="primary" @click="openEditTask(task)">编辑</el-button>
            <el-button link type="danger" @click="removeTask(task)">删除</el-button>
          </div>
        </div>
      </aside>
    </div>

    <el-dialog
      v-model="goalDialogVisible"
      :title="editingGoalId ? '编辑目标' : '新建目标'"
      width="560px"
    >
      <el-form label-width="92px">
        <el-form-item label="目标标题" required>
          <el-input v-model="goalForm.title" maxlength="255" />
        </el-form-item>
        <el-form-item label="关联学科">
          <el-select v-model="goalForm.subjectId" clearable style="width: 100%">
            <el-option
              v-for="subject in subjects"
              :key="subject.id"
              :label="subject.name"
              :value="subject.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="目标日期">
          <el-date-picker
            v-model="goalForm.targetDate"
            type="date"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="goalForm.priority" style="width: 100%">
            <el-option label="高" value="HIGH" />
            <el-option label="中" value="MEDIUM" />
            <el-option label="低" value="LOW" />
          </el-select>
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="goalForm.description" type="textarea" :rows="4" maxlength="1000" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="goalDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingGoal" @click="submitGoal">保存目标</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="aiDialogVisible" title="AI 生成学习目标" width="680px">
      <el-form label-position="top">
        <el-form-item label="描述你的学习需求">
          <el-input
            v-model="aiRequirement"
            type="textarea"
            :rows="4"
            placeholder="例如：我想在一个月内补齐高等数学极限和导数基础，并完成 20 道练习题"
          />
        </el-form-item>
      </el-form>
      <el-button type="primary" :loading="aiLoading" @click="generateGoalSuggestions">
        生成建议
      </el-button>
      <div v-if="suggestions.length > 0" class="ai-suggestion-list">
        <div v-for="suggestion in suggestions" :key="suggestion.title" class="ai-suggestion">
          <div>
            <div class="task-title">{{ suggestion.title }}</div>
            <div class="task-meta">
              {{ suggestion.description || '暂无说明' }} ·
              截止 {{ suggestion.targetDate || '未设置' }} ·
              {{ suggestion.priority }}
            </div>
          </div>
          <el-button link type="primary" @click="adoptSuggestion(suggestion)">采纳</el-button>
        </div>
      </div>
    </el-dialog>

    <el-dialog
      v-model="taskDialogVisible"
      :title="editingTaskId ? '编辑任务' : '新建任务'"
      width="560px"
    >
      <el-form label-width="92px">
        <el-form-item label="任务标题" required>
          <el-input v-model="taskForm.title" maxlength="255" />
        </el-form-item>
        <el-form-item label="所属目标">
          <el-select v-model="taskForm.goalId" clearable style="width: 100%">
            <el-option
              v-for="goal in activeGoals"
              :key="goal.id"
              :label="goal.title"
              :value="goal.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="任务分类">
          <el-input v-model="taskForm.category" maxlength="32" />
        </el-form-item>
        <el-form-item label="截止时间">
          <el-date-picker
            v-model="taskForm.dueAt"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="taskForm.priority" style="width: 100%">
            <el-option label="高" value="HIGH" />
            <el-option label="中" value="MEDIUM" />
            <el-option label="低" value="LOW" />
          </el-select>
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="taskForm.description" type="textarea" :rows="4" maxlength="1000" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="taskDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingTask" @click="submitTask">保存任务</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.ai-suggestion-list {
  display: grid;
  gap: 0;
  margin-top: 18px;
  border-top: 1px solid var(--rule-strong);
}

.ai-suggestion {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 14px;
  align-items: center;
  padding: 14px 0;
  border-bottom: 1px solid var(--rule);
}
</style>
