<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import {
  convertLearningPlanItem,
  deleteLearningPlan,
  generateLearningPlan,
  listLearningPlans,
  moveLearningPlanItem,
  updateLearningPlanItem,
  updateLearningPlanItemStatus,
  updateLearningPlanStatus,
  type LearningPlan,
  type LearningPlanItem,
  type LearningPlanStatus
} from '@/api/learningPlan'

const PLAN_STATUS_LABELS: Record<string, string> = {
  DRAFT: '草稿',
  ACTIVE: '进行中',
  COMPLETED: '已完成',
  ARCHIVED: '已归档'
}

const loading = ref(true)
const generating = ref(false)
const savingItem = ref(false)
const plans = ref<LearningPlan[]>([])
const selectedId = ref<number | null>(null)
const dialogVisible = ref(false)
const itemDialogVisible = ref(false)
const requirement = ref('')
const editingItemId = ref<number | null>(null)

const itemForm = reactive({
  title: '',
  description: '',
  startDate: '',
  endDate: ''
})

const selectedPlan = computed(() => {
  return plans.value.find((plan) => plan.id === selectedId.value) ?? null
})

onMounted(async () => {
  await load()
})

async function load(): Promise<void> {
  loading.value = true
  try {
    plans.value = await listLearningPlans()
    const stillExists = plans.value.some((plan) => plan.id === selectedId.value)
    if (!stillExists) {
      selectedId.value = plans.value.length > 0 ? plans.value[0].id : null
    }
  } catch {
    ElMessage.error('学习计划加载失败')
  } finally {
    loading.value = false
  }
}

function applyPlan(plan: LearningPlan): void {
  const index = plans.value.findIndex((item) => item.id === plan.id)
  if (index >= 0) {
    plans.value[index] = plan
  } else {
    plans.value = [plan, ...plans.value]
  }
  selectedId.value = plan.id
}

async function generate(): Promise<void> {
  if (!requirement.value.trim()) {
    ElMessage.warning('请描述你的学习目标和时间安排')
    return
  }
  generating.value = true
  try {
    const plan = await generateLearningPlan(requirement.value.trim())
    dialogVisible.value = false
    requirement.value = ''
    await load()
    applyPlan(plan)
    ElMessage.success('学习计划已生成')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '学习计划生成失败')
  } finally {
    generating.value = false
  }
}

async function changePlanStatus(status: LearningPlanStatus): Promise<void> {
  const plan = selectedPlan.value
  if (!plan) {
    return
  }
  try {
    applyPlan(await updateLearningPlanStatus(plan.id, status))
    ElMessage.success(`计划状态已更新为${PLAN_STATUS_LABELS[status] ?? status}`)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '计划状态更新失败')
  }
}

async function toggleItem(item: LearningPlanItem): Promise<void> {
  const plan = selectedPlan.value
  if (!plan) {
    return
  }
  const nextStatus = item.status === 'DONE' ? 'TODO' : 'DONE'
  try {
    applyPlan(await updateLearningPlanItemStatus(plan.id, item.id, nextStatus))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '条目状态更新失败')
  }
}

function openItemEdit(item: LearningPlanItem): void {
  editingItemId.value = item.id
  itemForm.title = item.title
  itemForm.description = item.description ?? ''
  itemForm.startDate = item.startDate ?? ''
  itemForm.endDate = item.endDate ?? ''
  itemDialogVisible.value = true
}

async function submitItem(): Promise<void> {
  const plan = selectedPlan.value
  if (!plan || !editingItemId.value) {
    return
  }
  if (!itemForm.title.trim()) {
    ElMessage.warning('请输入条目标题')
    return
  }
  savingItem.value = true
  try {
    applyPlan(await updateLearningPlanItem(plan.id, editingItemId.value, {
      title: itemForm.title.trim(),
      description: itemForm.description.trim() || undefined,
      startDate: itemForm.startDate || undefined,
      endDate: itemForm.endDate || undefined
    }))
    itemDialogVisible.value = false
    ElMessage.success('条目已更新')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '条目更新失败')
  } finally {
    savingItem.value = false
  }
}

async function convertItem(item: LearningPlanItem, target: 'TASK' | 'GOAL'): Promise<void> {
  const plan = selectedPlan.value
  if (!plan) {
    return
  }
  try {
    applyPlan(await convertLearningPlanItem(plan.id, item.id, target))
    ElMessage.success(target === 'TASK' ? '已转为学习任务' : '已转为学习目标')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '条目转换失败')
  }
}

async function moveItem(item: LearningPlanItem, direction: 'UP' | 'DOWN'): Promise<void> {
  const plan = selectedPlan.value
  if (!plan) {
    return
  }
  try {
    applyPlan(await moveLearningPlanItem(plan.id, item.id, direction))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '条目顺序调整失败')
  }
}

async function remove(plan: LearningPlan): Promise<void> {
  try {
    await ElMessageBox.confirm(`确认删除学习计划“${plan.title}”吗？`, '删除计划', {
      type: 'warning'
    })
    await deleteLearningPlan(plan.id)
    await load()
    ElMessage.success('学习计划已删除')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error('学习计划删除失败')
    }
  }
}

function planStatusLabel(status: string): string {
  return PLAN_STATUS_LABELS[status] ?? status
}

function formatDate(value?: string): string {
  return value || '未设置'
}
</script>

<template>
  <div v-loading="loading" class="ledger-page">
    <header class="ledger-header">
      <div>
        <div class="ledger-kicker">AI 学习规划</div>
        <h1 class="ledger-title">把长期目标拆成一周一步</h1>
        <p class="ledger-subtitle">
          计划生成后会保存到账本。逐条勾选完成进度，也可以把具体条目转成学习任务或学习目标。
        </p>
      </div>
      <div class="ledger-date">
        <el-button type="primary" @click="dialogVisible = true">生成学习计划</el-button>
      </div>
    </header>

    <div class="plan-layout">
      <aside class="plan-rail">
        <div class="chat-rail-title">我的计划</div>
        <div v-if="plans.length === 0" class="empty-ledger">
          还没有学习计划。描述目标后让 AI 生成第一版。
        </div>
        <button
          v-for="plan in plans"
          :key="plan.id"
          class="chat-session-item"
          :class="{ active: plan.id === selectedId }"
          @click="selectedId = plan.id"
        >
          <span class="chat-session-title">{{ plan.title }}</span>
          <span class="chat-session-preview">
            {{ planStatusLabel(plan.status) }} · {{ plan.completedItemCount }}/{{ plan.totalItemCount }} ·
            {{ plan.progress }}%
          </span>
        </button>
      </aside>

      <section v-if="selectedPlan" class="plan-main">
        <div class="plan-heading">
          <div>
            <h2>{{ selectedPlan.title }}</h2>
            <p>{{ selectedPlan.summary || '暂无计划摘要' }}</p>
            <div class="task-meta">
              状态：{{ planStatusLabel(selectedPlan.status) }} ·
              完成 {{ selectedPlan.completedItemCount }}/{{ selectedPlan.totalItemCount }} ·
              生成模型：{{ selectedPlan.providerCode || '未知' }} /
              {{ selectedPlan.model || '未知' }}
            </div>
          </div>
          <div class="task-actions">
            <el-button
              v-if="selectedPlan.status !== 'ACTIVE'"
              link
              type="primary"
              @click="changePlanStatus('ACTIVE')"
            >
              激活计划
            </el-button>
            <el-button
              v-if="selectedPlan.status === 'ACTIVE'"
              link
              type="primary"
              @click="changePlanStatus('ARCHIVED')"
            >
              归档
            </el-button>
            <el-button link type="danger" @click="remove(selectedPlan)">删除计划</el-button>
          </div>
        </div>

        <div class="goal-progress">
          <div class="goal-progress-track">
            <div class="goal-progress-value" :style="{ width: `${selectedPlan.progress}%` }"></div>
          </div>
          <div class="goal-progress-text">{{ selectedPlan.progress }}%</div>
        </div>

        <div class="plan-items">
          <article v-for="item in selectedPlan.items" :key="item.id" class="plan-item">
            <div class="plan-step">{{ item.sortOrder }}</div>
            <div>
              <div class="task-title" :class="{ done: item.status === 'DONE' }">
                {{ item.title }}
              </div>
              <div class="task-meta">
                {{ formatDate(item.startDate) }} - {{ formatDate(item.endDate) }} ·
                {{ item.status === 'DONE' ? '已完成' : '待完成' }}
                <template v-if="item.taskId"> · 已生成学习任务</template>
                <template v-if="item.goalId"> · 已生成学习目标</template>
              </div>
              <div v-if="item.description" class="plan-description">
                {{ item.description }}
              </div>
              <div class="task-actions">
                <el-button link type="primary" @click="toggleItem(item)">
                  {{ item.status === 'DONE' ? '重新打开' : '标记完成' }}
                </el-button>
                <el-button link @click="openItemEdit(item)">编辑</el-button>
                <el-button link @click="moveItem(item, 'UP')">上移</el-button>
                <el-button link @click="moveItem(item, 'DOWN')">下移</el-button>
                <el-button
                  v-if="!item.taskId"
                  link
                  type="primary"
                  @click="convertItem(item, 'TASK')"
                >
                  转为任务
                </el-button>
                <el-button
                  v-if="!item.goalId"
                  link
                  type="primary"
                  @click="convertItem(item, 'GOAL')"
                >
                  转为目标
                </el-button>
              </div>
            </div>
          </article>
        </div>
      </section>

      <div v-else class="chat-empty chat-empty-large">
        选择一份学习计划，或生成新的计划。
      </div>
    </div>

    <el-dialog v-model="dialogVisible" title="生成 AI 学习计划" width="680px">
      <el-form label-position="top">
        <el-form-item label="描述你的学习目标">
          <el-input
            v-model="requirement"
            type="textarea"
            :rows="6"
            placeholder="例如：我想在四周内复习高等数学极限、导数和线性代数矩阵基础，每周可学习 6 小时"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="generating" @click="generate">生成计划</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="itemDialogVisible" title="编辑计划条目" width="560px">
      <el-form label-position="top">
        <el-form-item label="条目标题" required>
          <el-input v-model="itemForm.title" maxlength="255" />
        </el-form-item>
        <el-form-item label="完成标准">
          <el-input v-model="itemForm.description" type="textarea" :rows="4" maxlength="1000" />
        </el-form-item>
        <el-form-item label="开始日期">
          <el-date-picker
            v-model="itemForm.startDate"
            type="date"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="结束日期">
          <el-date-picker
            v-model="itemForm.endDate"
            type="date"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="itemDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingItem" @click="submitItem">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
