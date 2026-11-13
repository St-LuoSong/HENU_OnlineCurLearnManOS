<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import ActionStagePanel from '@/components/growth/ActionStagePanel.vue'
import DirectionStagePanel from '@/components/growth/DirectionStagePanel.vue'
import GoalsStagePanel from '@/components/growth/GoalsStagePanel.vue'
import GrowthContextRail from '@/components/growth/GrowthContextRail.vue'
import GrowthStageRail from '@/components/growth/GrowthStageRail.vue'
import PlanStagePanel from '@/components/growth/PlanStagePanel.vue'
import ReviewStagePanel from '@/components/growth/ReviewStagePanel.vue'
import type { LearningPlan } from '@/api/learningPlan'
import { useGrowthStore, type GrowthStage } from '@/stores/growth'
import { addDays, formatDate, toLocalDateKey } from '@/utils/date'

const store = useGrowthStore()
const route = useRoute()
const router = useRouter()
const cycleDialogVisible = ref(false)
const creating = ref(false)
const selectedCarryGoals = ref<number[]>([])
const selectedCarryTasks = ref<number[]>([])
const form = reactive({
  title: '下一成长周期',
  startDate: toLocalDateKey(new Date()),
  endDate: toLocalDateKey(addDays(new Date(), 150))
})

const workspace = computed(() => store.workspace)
const cycle = computed(() => store.cycle)
const readOnly = computed(() => store.isReadOnly)
const openGoals = computed(() =>
  workspace.value?.goals.filter((goal) => goal.status === 'ACTIVE') ?? []
)
const openTasks = computed(() => [
  ...(workspace.value?.todayTasks ?? []),
  ...(workspace.value?.upcomingTasks ?? [])
])
const canCreateCycle = computed(() => !workspace.value?.cycles.some((item) => item.status === 'ACTIVE'))
const stageLabel = computed(() => ({
  DIRECTION: '确认方向',
  GOALS: '建立目标',
  PLAN: '制定计划',
  ACTION: '推进行动',
  REVIEW: '每周复盘'
}[store.stage]))

onMounted(async () => {
  try {
    await store.load()
    applyRouteStage()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '成长规划加载失败')
  }
})

watch(
  () => store.stage,
  (value) => {
    const query = { ...route.query, stage: value.toLowerCase() }
    void router.replace({ query })
  }
)

function applyRouteStage(): void {
  const requested = String(route.query.stage ?? '').toUpperCase()
  if (['DIRECTION', 'GOALS', 'PLAN', 'ACTION', 'REVIEW'].includes(requested)) {
    store.setStage(requested as GrowthStage)
  }
}

async function loadCycle(id: number): Promise<void> {
  try {
    await store.load(id)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '周期加载失败')
  }
}

function selectStage(stage: GrowthStage): void {
  store.setStage(stage)
}

async function completeCycle(): Promise<void> {
  if (!cycle.value) return
  try {
    await ElMessageBox.confirm(
      '结束后本周期将冻结为历史记录，未完成目标和任务可在下一周期选择带入。',
      '结束成长周期',
      { type: 'warning', confirmButtonText: '结束周期' }
    )
    await store.completeCycle()
    ElMessage.success('周期已结束，历史内容已保留')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error instanceof Error ? error.message : '周期结束失败')
    }
  }
}

function openNextCycle(): void {
  form.title = '下一成长周期'
  form.startDate = toLocalDateKey(new Date())
  form.endDate = toLocalDateKey(addDays(new Date(), 150))
  selectedCarryGoals.value = openGoals.value.map((goal) => goal.id)
  selectedCarryTasks.value = openTasks.value.map((task) => task.id)
  cycleDialogVisible.value = true
}

async function createCycle(): Promise<void> {
  if (!form.title.trim()) {
    ElMessage.warning('请输入周期名称')
    return
  }
  if (form.endDate <= form.startDate) {
    ElMessage.warning('结束日期必须晚于开始日期')
    return
  }
  creating.value = true
  try {
    await store.createCycle({
      title: form.title.trim(),
      startDate: form.startDate,
      endDate: form.endDate,
      carryOverGoalIds: selectedCarryGoals.value,
      carryOverTaskIds: selectedCarryTasks.value
    })
    cycleDialogVisible.value = false
    ElMessage.success('新周期已开始')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '周期创建失败')
  } finally {
    creating.value = false
  }
}

async function archiveCycle(): Promise<void> {
  if (!cycle.value) return
  try {
    await ElMessageBox.confirm('归档后将从默认周期列表中收起，历史内容仍可查看。', '归档周期', {
      type: 'warning'
    })
    await store.archiveCycle(cycle.value.id)
    ElMessage.success('周期已归档')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error instanceof Error ? error.message : '周期归档失败')
    }
  }
}

async function refreshDirection(): Promise<void> {
  try {
    await ElMessageBox.confirm(
      '将使用当前生涯画像和最近一次分析更新本周期方向快照，历史周期不会改变。',
      '更新方向快照',
      { type: 'warning' }
    )
    await store.refreshDirection()
    ElMessage.success('方向快照已更新')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error instanceof Error ? error.message : '方向更新失败')
    }
  }
}

async function reloadWorkspace(): Promise<void> {
  if (!cycle.value) return
  await store.load(cycle.value.id)
}

async function activatePlan(plan: LearningPlan): Promise<void> {
  try {
    await store.activatePlan(plan)
    ElMessage.success('学习计划已设为当前计划')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '计划采纳失败')
  }
}

async function saveReview(review: Parameters<typeof store.saveReview>[0], note: string): Promise<void> {
  try {
    await store.saveReview(review, note)
    ElMessage.success('本周复盘已保存')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '复盘保存失败')
  }
}
</script>

<template>
  <div v-loading="store.loading" class="ledger-page growth-page">
    <header class="ledger-header growth-page-head">
      <div>
        <div class="ledger-kicker">成长规划</div>
        <h1 class="ledger-title">把方向走成一条可持续的路</h1>
        <p class="ledger-subtitle">
          一个周期，一条阶段轨道。方向、目标、计划、行动和复盘共享同一份进度。
        </p>
      </div>
      <div class="growth-cycle-controls">
        <el-select
          v-if="workspace"
          :model-value="workspace.cycle.id"
          size="small"
          class="cycle-switcher"
          @change="loadCycle"
        >
          <el-option
            v-for="item in workspace.cycles"
            :key="item.id"
            :label="`${item.title} · ${item.status === 'ACTIVE' ? '进行中' : item.status === 'COMPLETED' ? '已结束' : '已归档'}`"
            :value="item.id"
          />
        </el-select>
        <el-button
          v-if="cycle?.status === 'ACTIVE'"
          size="small"
          @click="completeCycle"
        >
          结束周期
        </el-button>
        <el-button
          v-if="cycle?.status === 'COMPLETED'"
          type="primary"
          size="small"
          @click="openNextCycle"
        >
          开始下一周期
        </el-button>
        <el-button
          v-if="cycle?.status === 'COMPLETED'"
          size="small"
          @click="archiveCycle"
        >
          归档
        </el-button>
        <el-button
          v-if="canCreateCycle && !cycle"
          type="primary"
          size="small"
          @click="openNextCycle"
        >
          创建周期
        </el-button>
      </div>
    </header>

    <template v-if="workspace && cycle">
      <section class="growth-cycle-banner">
        <div class="growth-cycle-title">
          <span class="cycle-status" :class="cycle.status.toLowerCase()">
            {{ cycle.status === 'ACTIVE' ? '进行中' : cycle.status === 'COMPLETED' ? '已结束' : '已归档' }}
          </span>
          <h2>{{ cycle.title }}</h2>
          <p>{{ formatDate(cycle.startDate) }} — {{ formatDate(cycle.endDate) }}</p>
        </div>
        <div class="growth-cycle-pulse">
          <span class="cycle-pulse-label">当前建议阶段</span>
          <strong>{{ stageLabel }}</strong>
          <small v-if="readOnly">历史周期仅可查看和带入，不可修改</small>
          <small v-else>阶段可自由查看，主操作会跟随当前阶段变化</small>
        </div>
      </section>

      <GrowthStageRail
        :stage="store.stage"
        :workspace="workspace"
        :read-only="readOnly"
        @select="selectStage"
      />

      <div class="growth-map-body">
        <main class="growth-stage-content">
          <DirectionStagePanel
            v-if="store.stage === 'DIRECTION'"
            :workspace="workspace"
            :read-only="readOnly"
            @refresh="refreshDirection"
          />
          <GoalsStagePanel
            v-else-if="store.stage === 'GOALS'"
            :workspace="workspace"
            :read-only="readOnly"
            @changed="reloadWorkspace"
          />
          <PlanStagePanel
            v-else-if="store.stage === 'PLAN'"
            :workspace="workspace"
            :read-only="readOnly"
            @changed="reloadWorkspace"
            @activate="activatePlan"
          />
          <ActionStagePanel
            v-else-if="store.stage === 'ACTION'"
            :workspace="workspace"
            :read-only="readOnly"
            @changed="reloadWorkspace"
          />
          <ReviewStagePanel
            v-else
            :workspace="workspace"
            :read-only="readOnly"
            @save="saveReview"
          />
        </main>
        <GrowthContextRail :workspace="workspace" />
      </div>
    </template>

    <div v-else-if="!store.loading" class="empty-ledger growth-empty">
      当前还没有成长周期。创建一个周期，从确认方向开始。
    </div>

    <el-dialog v-model="cycleDialogVisible" title="开始下一成长周期" width="560px">
      <el-form label-position="top">
        <el-form-item label="周期名称" required>
          <el-input v-model="form.title" maxlength="128" />
        </el-form-item>
        <el-form-item label="开始日期" required>
          <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束日期" required>
          <el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
      </el-form>

      <div v-if="openGoals.length || openTasks.length" class="carry-over-block">
        <div class="carry-over-title">从当前历史周期带入未完成事项</div>
        <div class="carry-over-columns">
          <div>
            <span>目标</span>
            <el-checkbox-group v-model="selectedCarryGoals">
              <el-checkbox v-for="goal in openGoals" :key="goal.id" :label="goal.id">
                {{ goal.title }}
              </el-checkbox>
            </el-checkbox-group>
          </div>
          <div>
            <span>任务</span>
            <el-checkbox-group v-model="selectedCarryTasks">
              <el-checkbox v-for="task in openTasks" :key="task.id" :label="task.id">
                {{ task.title }}
              </el-checkbox>
            </el-checkbox-group>
          </div>
        </div>
      </div>

      <template #footer>
        <el-button @click="cycleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="createCycle">开始周期</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.growth-page {
  display: grid;
  gap: 16px;
}

.growth-page-head {
  align-items: flex-start;
}

.growth-cycle-controls {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 7px;
}

.cycle-switcher {
  width: 210px;
}

.growth-cycle-banner {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 220px;
  gap: 24px;
  align-items: center;
  padding: 18px 22px;
  background: var(--white);
  border: 1px solid var(--rule);
  border-left: 4px solid var(--primary);
}

.growth-cycle-title h2 {
  margin: 5px 0 0;
  color: var(--ink);
  font-family: var(--font-serif);
  font-size: 28px;
}

.growth-cycle-title p {
  margin: 4px 0 0;
  color: var(--ink-soft);
  font-family: var(--font-numeric);
  font-size: 11px;
}

.cycle-status {
  display: inline-block;
  padding: 2px 7px;
  color: var(--jade);
  font-size: 10px;
  background: var(--jade-soft);
}

.cycle-status.completed { color: var(--ochre); background: var(--ochre-soft); }
.cycle-status.archived { color: var(--ink-soft); background: var(--paper-deep); }

.growth-cycle-pulse {
  display: grid;
  gap: 4px;
  padding-left: 18px;
  border-left: 1px solid var(--rule);
}

.cycle-pulse-label,
.growth-cycle-pulse small {
  color: var(--ink-soft);
  font-size: 10px;
}

.growth-cycle-pulse strong {
  color: var(--primary-deep);
  font-family: var(--font-serif);
  font-size: 20px;
}

.growth-map-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 240px;
  gap: 20px;
  align-items: start;
}

.growth-stage-content {
  min-width: 0;
  padding-bottom: 28px;
}

.growth-stage-content > * {
  animation: growth-stage-in 0.24s ease-out;
}

.growth-empty {
  padding: 30px 0;
}

.carry-over-block {
  padding-top: 14px;
  border-top: 1px solid var(--rule);
}

.carry-over-title {
  margin-bottom: 10px;
  color: var(--ink);
  font-size: 13px;
  font-weight: 700;
}

.carry-over-columns {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}

.carry-over-columns > div {
  display: grid;
  gap: 6px;
  align-content: start;
}

.carry-over-columns > div > span {
  color: var(--ink-soft);
  font-size: 10px;
}

.carry-over-columns :deep(.el-checkbox) {
  height: auto;
  margin-right: 0;
}

@keyframes growth-stage-in {
  from { opacity: 0; transform: translateY(4px); }
  to { opacity: 1; transform: translateY(0); }
}

@media (max-width: 1100px) {
  .growth-map-body { grid-template-columns: 1fr; }
  .growth-map-body :deep(.growth-context) { border-top: 1px solid var(--rule); }
}

@media (max-width: 620px) {
  .growth-page-head { flex-direction: column; }
  .growth-cycle-controls { justify-content: flex-start; }
  .cycle-switcher { width: 100%; }
  .growth-cycle-banner { grid-template-columns: 1fr; }
  .growth-cycle-pulse { padding: 12px 0 0; border-top: 1px solid var(--rule); border-left: 0; }
  .carry-over-columns { grid-template-columns: 1fr; }
}

@media (prefers-reduced-motion: reduce) {
  .growth-stage-content > * { animation: none; }
}
</style>
