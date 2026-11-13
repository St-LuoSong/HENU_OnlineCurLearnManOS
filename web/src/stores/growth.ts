import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import {
  archiveGrowthCycle,
  completeGrowthCycle,
  createGrowthCycle,
  getGrowthWorkspace,
  refreshGrowthDirection,
  saveGrowthReview,
  type GrowthCycle,
  type GrowthReview,
  type GrowthWorkspace
} from '@/api/growth'
import { activateLearningPlan, type LearningPlan } from '@/api/learningPlan'

export type GrowthStage = 'DIRECTION' | 'GOALS' | 'PLAN' | 'ACTION' | 'REVIEW'

export const useGrowthStore = defineStore('growth', () => {
  const workspace = ref<GrowthWorkspace | null>(null)
  const loading = ref(true)
  const stage = ref<GrowthStage>('DIRECTION')
  const error = ref('')

  const cycle = computed(() => workspace.value?.cycle ?? null)
  const isReadOnly = computed(() => cycle.value?.status !== 'ACTIVE')
  const currentPlan = computed(() => workspace.value?.currentPlan ?? null)
  const reviews = computed(() => workspace.value?.reviews ?? [])

  async function load(cycleId?: number): Promise<void> {
    loading.value = true
    error.value = ''
    try {
      const data = await getGrowthWorkspace(cycleId)
      workspace.value = data
      stage.value = data.currentStage
    } catch (cause) {
      error.value = cause instanceof Error ? cause.message : '成长规划加载失败'
      throw cause
    } finally {
      loading.value = false
    }
  }

  async function createCycle(payload: {
    title: string
    startDate: string
    endDate: string
    carryOverGoalIds?: number[]
    carryOverTaskIds?: number[]
  }): Promise<GrowthCycle> {
    const created = await createGrowthCycle(payload)
    await load(created.id)
    return created
  }

  async function completeCycle(): Promise<void> {
    if (!cycle.value) return
    await completeGrowthCycle(cycle.value.id)
    await load(cycle.value.id)
  }

  async function archiveCycle(id: number): Promise<void> {
    await archiveGrowthCycle(id)
    await load()
  }

  async function refreshDirection(): Promise<void> {
    if (!cycle.value) return
    await refreshGrowthDirection(cycle.value.id)
    await load(cycle.value.id)
  }

  async function saveReview(review: GrowthReview, note: string): Promise<void> {
    if (!cycle.value) return
    await saveGrowthReview(cycle.value.id, review.weekStart, note)
    await load(cycle.value.id)
  }

  async function activatePlan(plan: LearningPlan): Promise<void> {
    if (!cycle.value) return
    await activateLearningPlan(plan.id)
    await load(cycle.value.id)
  }

  function setStage(next: GrowthStage): void {
    stage.value = next
  }

  return {
    workspace,
    loading,
    stage,
    error,
    cycle,
    isReadOnly,
    currentPlan,
    reviews,
    load,
    createCycle,
    completeCycle,
    archiveCycle,
    refreshDirection,
    saveReview,
    activatePlan,
    setStage
  }
})
