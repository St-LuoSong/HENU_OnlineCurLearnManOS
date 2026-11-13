<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import {
  deleteCareerAnalysis,
  convertCareerInsight,
  getCareerComparison,
  generateCareerAnalysis,
  getCareerDataPreview,
  getCareerProfile,
  listCareerAnalyses,
  saveCareerProfile,
  updateCareerConsent,
  type CareerComparison,
  type CareerAnalysis,
  type CareerDataPreview,
  type CareerInsight,
  type CareerProfile
} from '@/api/career'
import { formatDateTime as formatDateTimeText } from '@/utils/date'

const CATEGORY_LABELS: Record<string, string> = {
  DIRECTION: '发展方向',
  INTEREST: '兴趣特点',
  ABILITY: '能力特点',
  SUGGESTION: '发展建议',
  PATH: '学习路径',
  GROWTH: '阶段成长建议',
  ACTION: '下一阶段行动'
}

const loading = ref(true)
const generating = ref(false)
const savingProfile = ref(false)
const savingConsent = ref(false)
const analyses = ref<CareerAnalysis[]>([])
const selectedId = ref<number | null>(null)
const profile = ref<CareerProfile | null>(null)
const preview = ref<CareerDataPreview | null>(null)
const comparison = ref<CareerComparison | null>(null)
const drawerVisible = ref(false)
const generateVisible = ref(false)
const focus = ref('')

const form = reactive({
  major: '',
  gradeLevel: '',
  weeklyHours: undefined as number | undefined,
  interestTags: [] as string[],
  strengthNotes: '',
  careerGoal: ''
})

const consent = reactive({
  granted: false,
  includeLearningRecords: true,
  includeGoals: true,
  includeGrades: true
})

const selectedAnalysis = computed(() => {
  return analyses.value.find((analysis) => analysis.id === selectedId.value) ?? null
})

const privacyNotice = computed(() => preview.value?.privacyNotice ?? '')

const collectedSources = computed(() => preview.value?.sources ?? [])

onMounted(async () => {
  await load()
})

watch(selectedId, () => {
  void loadComparison()
})

async function loadComparison(): Promise<void> {
  const analysis = selectedAnalysis.value
  if (!analysis) {
    comparison.value = null
    return
  }
  try {
    comparison.value = await getCareerComparison(analysis.id)
  } catch {
    comparison.value = null
  }
}

async function convertInsight(
  insight: CareerInsight,
  target: 'TASK' | 'GOAL'
): Promise<void> {
  try {
    await convertCareerInsight(insight.id, target)
    const analysisId = selectedId.value
    analyses.value = await listCareerAnalyses()
    if (analysisId !== null) {
      selectedId.value = analysisId
    }
    ElMessage.success(target === 'TASK' ? '已转为学习任务' : '已转为学习目标')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '建议转换失败')
  }
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const [profileData, previewData, analysisList] = await Promise.all([
      getCareerProfile(),
      getCareerDataPreview(),
      listCareerAnalyses()
    ])
    profile.value = profileData
    preview.value = previewData
    analyses.value = analysisList
    applyProfile(profileData)
    if (analysisList.length > 0 && selectedId.value === null) {
      selectedId.value = analysisList[0].id
    }
  } catch {
    ElMessage.error('生涯分析数据加载失败')
  } finally {
    loading.value = false
  }
}

function applyProfile(data: CareerProfile): void {
  form.major = data.major ?? ''
  form.gradeLevel = data.gradeLevel ?? ''
  form.weeklyHours = data.weeklyHours ?? undefined
  form.interestTags = [...(data.interestTags ?? [])]
  form.strengthNotes = data.strengthNotes ?? ''
  form.careerGoal = data.careerGoal ?? ''
  consent.granted = data.consentGranted
  consent.includeLearningRecords = data.includeLearningRecords
  consent.includeGoals = data.includeGoals
  consent.includeGrades = data.includeGrades
}

async function refreshPreview(): Promise<void> {
  try {
    preview.value = await getCareerDataPreview()
  } catch {
    ElMessage.error('数据范围预览刷新失败')
  }
}

async function saveConsent(): Promise<void> {
  savingConsent.value = true
  try {
    const updated = await updateCareerConsent({ ...consent })
    profile.value = updated
    applyProfile(updated)
    await refreshPreview()
    ElMessage.success(updated.consentGranted ? '数据授权已保存' : '已取消数据授权')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '数据授权保存失败')
  } finally {
    savingConsent.value = false
  }
}

async function saveSelfReport(): Promise<void> {
  savingProfile.value = true
  try {
    const updated = await saveCareerProfile({
      major: form.major,
      gradeLevel: form.gradeLevel,
      weeklyHours: form.weeklyHours ?? null,
      interestTags: form.interestTags,
      strengthNotes: form.strengthNotes,
      careerGoal: form.careerGoal
    })
    profile.value = updated
    applyProfile(updated)
    await refreshPreview()
    ElMessage.success('个人资料已保存')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '个人资料保存失败')
  } finally {
    savingProfile.value = false
  }
}

function openGenerate(): void {
  if (!profile.value?.consentGranted) {
    ElMessage.warning('请先在数据授权中同意使用范围')
    drawerVisible.value = true
    return
  }
  focus.value = ''
  generateVisible.value = true
}

async function generate(): Promise<void> {
  generating.value = true
  try {
    const analysis = await generateCareerAnalysis(focus.value)
    generateVisible.value = false
    analyses.value = await listCareerAnalyses()
    selectedId.value = analysis.id
    await refreshPreview()
    ElMessage.success('生涯分析已生成')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '生涯分析生成失败')
  } finally {
    generating.value = false
  }
}

async function remove(analysis: CareerAnalysis): Promise<void> {
  try {
    await ElMessageBox.confirm(`确认删除分析“${analysis.title}”吗？`, '删除分析', {
      type: 'warning'
    })
    await deleteCareerAnalysis(analysis.id)
    analyses.value = await listCareerAnalyses()
    if (selectedId.value === analysis.id) {
      selectedId.value = analyses.value.length > 0 ? analyses.value[0].id : null
    }
    ElMessage.success('分析记录已删除')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error('分析记录删除失败')
    }
  }
}

function categoryLabel(category: string): string {
  return CATEGORY_LABELS[category] ?? category
}

function scoreWidth(insight: CareerInsight): string {
  const score = insight.score ?? 0
  return `${Math.max(0, Math.min(100, score))}%`
}

function formatDateTime(value?: string): string {
  return formatDateTimeText(value, '未记录')
}
</script>

<template>
  <div v-loading="loading" class="ledger-page">
    <header class="ledger-header">
      <div>
        <div class="ledger-kicker">AI 生涯分析</div>
        <h1 class="ledger-title">把课程、兴趣和成绩放在一起看</h1>
        <p class="ledger-subtitle">
          结合你的学习记录、学习目标、云班级成绩和自述信息，给出方向性的辅助建议。
          结论只作参考，数据是否参与由你在授权面板中决定。
        </p>
      </div>
      <div class="ledger-date">
        <el-button @click="drawerVisible = true">数据授权与资料</el-button>
        <el-button type="primary" :disabled="!profile?.consentGranted" @click="openGenerate">
          生成分析
        </el-button>
      </div>
    </header>

    <div class="summary-strip">
      <div class="summary-item">
        <span class="summary-value">{{ profile?.consentGranted ? '已授权' : '未授权' }}</span>
        <span class="summary-label">数据采集授权 · {{ preview?.consentVersion ?? 'v1' }}</span>
      </div>
      <div class="summary-item">
        <span class="summary-value">{{ preview?.learningRecordCount ?? 0 }}</span>
        <span class="summary-label">学习记录 · {{ Math.round((preview?.learningMinutes ?? 0) / 60) }} 小时</span>
      </div>
      <div class="summary-item">
        <span class="summary-value">{{ preview?.goalCount ?? 0 }}</span>
        <span class="summary-label">学习目标 · 任务完成 {{ preview?.completedTaskCount ?? 0 }}/{{ preview?.taskCount ?? 0 }}</span>
      </div>
      <div class="summary-item">
        <span class="summary-value">{{ preview?.gradedSubmissionCount ?? 0 }}</span>
        <span class="summary-label">
          已批改成绩 · 平均分 {{ preview?.averageScore ?? '暂无' }}
        </span>
      </div>
      <div class="summary-item">
        <span class="summary-value">{{ analyses.length }}</span>
        <span class="summary-label">历史分析记录</span>
      </div>
    </div>

    <div class="plan-layout">
      <aside class="plan-rail">
        <div class="chat-rail-title">分析记录</div>
        <div v-if="analyses.length === 0" class="empty-ledger">
          还没有分析记录。完成数据授权后生成第一份分析。
        </div>
        <button
          v-for="analysis in analyses"
          :key="analysis.id"
          class="chat-session-item"
          :class="{ active: analysis.id === selectedId }"
          @click="selectedId = analysis.id"
        >
          <span class="chat-session-title">{{ analysis.title }}</span>
          <span class="chat-session-preview">
            {{ formatDateTime(analysis.createdAt) }} · {{ analysis.insights.length }} 条建议
          </span>
        </button>
      </aside>

      <section v-if="selectedAnalysis" class="plan-main">
        <div class="plan-heading">
          <div>
            <h2>{{ selectedAnalysis.title }}</h2>
            <p>{{ selectedAnalysis.summary || '暂无整体摘要' }}</p>
            <div class="task-meta">
              生成模型：{{ selectedAnalysis.providerCode || '未知' }} /
              {{ selectedAnalysis.model || '未知' }} · {{ formatDateTime(selectedAnalysis.createdAt) }}
            </div>
          </div>
          <el-button link type="danger" @click="remove(selectedAnalysis)">删除分析</el-button>
        </div>

        <div class="plan-items">
          <article
            v-for="insight in selectedAnalysis.insights"
            :key="insight.id"
            class="plan-item"
          >
            <div class="plan-step">{{ insight.sortOrder }}</div>
            <div>
              <span class="career-category">{{ categoryLabel(insight.category) }}</span>
              <div class="task-title">{{ insight.title }}</div>
              <div v-if="insight.content" class="plan-description">{{ insight.content }}</div>
              <div v-if="insight.reason" class="plan-description">
                依据：{{ insight.reason }}
              </div>
              <div v-if="insight.targetDate" class="task-meta">
                建议完成日期：{{ insight.targetDate }}
              </div>
              <div v-if="insight.score !== null && insight.score !== undefined" class="career-score-row">
                <div class="career-score-track">
                  <div class="career-score-fill" :style="{ width: scoreWidth(insight) }"></div>
                </div>
                <div class="career-score-value">{{ insight.score }}</div>
              </div>
              <div class="task-actions">
                <el-button
                  v-if="!insight.convertedTaskId"
                  link
                  type="primary"
                  @click="convertInsight(insight, 'TASK')"
                >
                  转为学习任务
                </el-button>
                <el-button
                  v-if="!insight.convertedGoalId"
                  link
                  type="primary"
                  @click="convertInsight(insight, 'GOAL')"
                >
                  转为学习目标
                </el-button>
                <span v-if="insight.convertedTaskId" class="task-meta">已生成学习任务</span>
                <span v-if="insight.convertedGoalId" class="task-meta">已生成学习目标</span>
              </div>
            </div>
          </article>
        </div>

        <section v-if="comparison" class="career-notice">
          <div class="chat-rail-title">与本次分析时点的对比</div>
          <div v-for="note in comparison.notes" :key="note">{{ note }}</div>
        </section>

        <div class="career-notice">
          本次使用的数据范围：{{ selectedAnalysis.dataSources.length > 0 ? selectedAnalysis.dataSources.join('、') : '仅自述资料' }}。
          生涯分析是学习辅助建议，不是职业测评结论，也不构成升学或就业决策依据。
          请结合课程要求、个人判断和必要的现实信息使用。
        </div>
      </section>

      <div v-else class="chat-empty chat-empty-large">
        选择一份分析记录，或完成数据授权后生成新的分析。
      </div>
    </div>

    <el-drawer
      v-model="drawerVisible"
      title="数据授权与个人资料"
      size="560px"
      direction="rtl"
    >
      <section class="ledger-section">
        <h2 class="ledger-section-title">
          数据采集授权
          <small>{{ consent.granted ? '已授权' : '未授权' }}</small>
        </h2>
        <div class="career-notice">{{ privacyNotice }}</div>
        <el-form label-position="top" style="margin-top: 18px">
          <el-form-item label="允许生涯分析使用以下数据">
            <el-switch v-model="consent.granted" active-text="同意" inactive-text="不同意" />
          </el-form-item>
          <el-form-item>
            <el-checkbox v-model="consent.includeLearningRecords">学习记录与知识点掌握</el-checkbox>
          </el-form-item>
          <el-form-item>
            <el-checkbox v-model="consent.includeGoals">学习目标与学习任务</el-checkbox>
          </el-form-item>
          <el-form-item>
            <el-checkbox v-model="consent.includeGrades">云班级已批改成绩</el-checkbox>
          </el-form-item>
        </el-form>
        <el-button type="primary" :loading="savingConsent" @click="saveConsent">
          保存授权
        </el-button>
      </section>

      <section class="ledger-section">
        <h2 class="ledger-section-title">
          个人资料
          <small>用于补充数据中没有的信息</small>
        </h2>
        <el-form label-position="top">
          <el-form-item label="专业">
            <el-input v-model="form.major" maxlength="128" placeholder="例如：计算机科学与技术" />
          </el-form-item>
          <el-form-item label="年级">
            <el-input v-model="form.gradeLevel" maxlength="32" placeholder="例如：大二" />
          </el-form-item>
          <el-form-item label="每周可投入学习时间（小时）">
            <el-input-number v-model="form.weeklyHours" :min="0" :max="168" />
          </el-form-item>
          <el-form-item label="兴趣标签">
            <el-select
              v-model="form.interestTags"
              multiple
              filterable
              allow-create
              default-first-option
              :reserve-keyword="false"
              placeholder="输入后回车添加，例如：算法、数据分析、考研"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="自我评价的优势">
            <el-input
              v-model="form.strengthNotes"
              type="textarea"
              :rows="3"
              maxlength="1000"
              placeholder="例如：推导过程耐心，愿意反复练习同一类题目"
            />
          </el-form-item>
          <el-form-item label="期望发展方向">
            <el-input
              v-model="form.careerGoal"
              type="textarea"
              :rows="3"
              maxlength="500"
              placeholder="例如：希望往数据分析方向发展，同时保留读研可能"
            />
          </el-form-item>
        </el-form>
        <el-button type="primary" :loading="savingProfile" @click="saveSelfReport">
          保存资料
        </el-button>
      </section>

      <section class="ledger-section">
        <h2 class="ledger-section-title">
          将被使用的数据
          <small>实时统计</small>
        </h2>
        <div class="career-source-list">
          <div class="career-source-line">
            <span>学习记录（近 200 条）</span>
            <span class="career-source-value">{{ preview?.learningRecordCount ?? 0 }} 条</span>
          </div>
          <div class="career-source-line">
            <span>累计学习时长</span>
            <span class="career-source-value">{{ preview?.learningMinutes ?? 0 }} 分钟</span>
          </div>
          <div class="career-source-line">
            <span>已掌握知识点</span>
            <span class="career-source-value">{{ preview?.masteredPointCount ?? 0 }} 个</span>
          </div>
          <div class="career-source-line">
            <span>学习目标 / 任务</span>
            <span class="career-source-value">
              {{ preview?.goalCount ?? 0 }} / {{ preview?.taskCount ?? 0 }}
            </span>
          </div>
          <div class="career-source-line">
            <span>已批改成绩</span>
            <span class="career-source-value">{{ preview?.gradedSubmissionCount ?? 0 }} 条</span>
          </div>
          <div class="career-source-line">
            <span>涉及课程</span>
            <span class="career-source-value">
              {{ (preview?.subjects ?? []).length > 0 ? (preview?.subjects ?? []).join('、') : '暂无' }}
            </span>
          </div>
        </div>
        <div class="career-tags">
          <span v-for="source in collectedSources" :key="source" class="career-tag">{{ source }}</span>
          <span v-if="collectedSources.length === 0" class="career-tag">暂无可授权数据</span>
        </div>
        <el-button link @click="refreshPreview">刷新统计</el-button>
      </section>
    </el-drawer>

    <el-dialog v-model="generateVisible" title="生成 AI 生涯分析" width="680px">
      <el-form label-position="top">
        <el-form-item label="本次补充关注点（可选）">
          <el-input
            v-model="focus"
            type="textarea"
            :rows="5"
            maxlength="2000"
            placeholder="例如：我打算考研，也考虑直接就业，想比较两条路径在大三这一年分别要准备什么"
          />
        </el-form-item>
      </el-form>
      <div class="career-notice">
        本次将使用：{{ collectedSources.length > 0 ? collectedSources.join('、') : '仅自述资料' }}。
        生成会消耗一次模型调用积分，失败时自动退回。
      </div>
      <template #footer>
        <el-button @click="generateVisible = false">取消</el-button>
        <el-button type="primary" :loading="generating" @click="generate">生成分析</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.career-score-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 44px;
  gap: 12px;
  align-items: center;
  margin-top: 12px;
}

.career-score-track {
  height: 4px;
  background: var(--paper-deep);
  border: 1px solid var(--rule);
}

.career-score-fill {
  height: 100%;
  background: var(--jade);
}

.career-score-value {
  color: var(--ink-soft);
  font-family: var(--font-display);
  font-size: 14px;
  font-weight: 700;
  text-align: right;
}

.career-source-list {
  display: grid;
  gap: 8px;
}

.career-source-line {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 8px;
  color: var(--ink-soft);
  font-size: 13px;
  border-bottom: 1px dashed var(--rule);
}

.career-source-value {
  color: var(--ink);
  font-weight: 700;
  text-align: right;
}

.career-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 14px;
}

.career-tag {
  padding: 3px 9px;
  color: var(--ink-soft);
  font-size: 12px;
  background: var(--white);
  border: 1px solid var(--rule);
}
</style>
