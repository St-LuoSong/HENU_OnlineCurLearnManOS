<script setup lang="ts">
import { ArrowRight, DocumentChecked, Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, ref } from 'vue'

import { getAiAccess, type AiAccess } from '@/api/aiAccess'
import { listKnowledgePoints, listSubjects, type KnowledgePoint, type Subject } from '@/api/content'
import {
  createPracticeSet,
  getPracticeSet,
  listPracticeSets,
  savePracticeAnswers,
  submitPracticeSet,
  type PracticeSet,
  type PracticeSetSummary
} from '@/api/practiceSet'
import MarkdownContent from '@/components/markdown/MarkdownContent.vue'
import { formatDateTime, formatMonthDayTime } from '@/utils/date'

type AiMode = 'STANDARD' | 'EXPERT'

const loading = ref(true)
const generating = ref(false)
const saving = ref(false)
const submitting = ref(false)
const subjects = ref<Subject[]>([])
const knowledgePoints = ref<KnowledgePoint[]>([])
const history = ref<PracticeSetSummary[]>([])
const current = ref<PracticeSet | null>(null)
const answers = ref<Record<number, string>>({})
const subjectId = ref<number | undefined>()
const knowledgePointId = ref<number | undefined>()
const mode = ref<AiMode>('STANDARD')
const historyFilter = ref<'all' | 'IN_PROGRESS' | 'SUBMITTED'>('all')
const aiAccess = ref<AiAccess>({
  standardUsed: 0,
  standardLimit: 50,
  standardRemaining: 50,
  expertUsed: 0,
  expertLimit: 3,
  expertRemaining: 3
})

const answeredCount = computed(() =>
  current.value
    ? current.value.items.filter((item) => (answers.value[item.id] ?? '').trim()).length
    : 0
)
const progress = computed(() =>
  current.value && current.value.questionCount > 0
    ? Math.round(answeredCount.value * 100 / current.value.questionCount)
    : 0
)
const filteredHistory = computed(() =>
  historyFilter.value === 'all'
    ? history.value
    : history.value.filter((item) => item.status === historyFilter.value)
)
const isSubmitted = computed(() => current.value?.status === 'SUBMITTED')
const modeHint = computed(() =>
  mode.value === 'EXPERT'
    ? `专家模式 · 今日剩余 ${aiAccess.value.expertRemaining}/${aiAccess.value.expertLimit} 次 · 按专家模型价格扣除积分`
    : '普通模式 · 首套免费，重做按规则扣积分'
)

onMounted(async () => {
  await Promise.all([loadSubjects(), loadHistory(), loadAiAccess()])
  loading.value = false
})

async function loadSubjects(): Promise<void> {
  try {
    subjects.value = await listSubjects()
  } catch {
    ElMessage.error('课程列表加载失败')
  }
}

async function loadAiAccess(): Promise<void> {
  try {
    aiAccess.value = await getAiAccess()
  } catch {
    // 配额展示失败时后端仍会做原子校验。
  }
}

async function loadHistory(): Promise<void> {
  try {
    history.value = await listPracticeSets()
  } catch {
    ElMessage.error('练习历史加载失败')
  }
}

async function handleSubjectChange(value?: number): Promise<void> {
  knowledgePointId.value = undefined
  knowledgePoints.value = value ? await listKnowledgePoints(value) : []
}

async function generateSet(): Promise<void> {
  if (!subjectId.value) {
    ElMessage.warning('请先选择课程')
    return
  }
  if (mode.value === 'EXPERT' && aiAccess.value.expertRemaining <= 0) {
    ElMessage.warning('今日专家模式次数已用完')
    mode.value = 'STANDARD'
    return
  }
  if (current.value?.status === 'IN_PROGRESS') {
    try {
      await ElMessageBox.confirm(
        '当前这套题还没有交卷，生成新套题会自动归档它，未作答题目按错误记录。',
        '开始新套题',
        { type: 'warning' }
      )
    } catch {
      return
    }
  }
  generating.value = true
  try {
    const created = await createPracticeSet({
      subjectId: subjectId.value,
      knowledgePointId: knowledgePointId.value ?? null,
      mode: mode.value
    })
    current.value = created
    resetAnswers(created)
    await Promise.all([loadHistory(), loadAiAccess()])
    ElMessage.success('新套题已生成')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '题目生成失败')
  } finally {
    generating.value = false
  }
}

async function openSet(id: number): Promise<void> {
  loading.value = true
  try {
    const detail = await getPracticeSet(id)
    current.value = detail
    subjectId.value = detail.subjectId
    knowledgePointId.value = detail.knowledgePointId
    mode.value = detail.billingMode
    await handleSubjectChange(detail.subjectId)
    knowledgePointId.value = detail.knowledgePointId
    resetAnswers(detail)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '练习加载失败')
  } finally {
    loading.value = false
  }
}

function resetAnswers(set: PracticeSet): void {
  answers.value = Object.fromEntries(
    set.items.map((item) => [item.id, item.studentAnswer ?? ''])
  )
}

async function saveDraft(showMessage = true): Promise<void> {
  if (!current.value || current.value.status !== 'IN_PROGRESS') {
    return
  }
  if (answeredCount.value === 0) {
    if (showMessage) {
      ElMessage.warning('还没有填写答案')
    }
    return
  }
  const payload = current.value.items.map((item) => ({
    itemId: item.id,
    answer: (answers.value[item.id] ?? '').trim()
  }))
  saving.value = true
  try {
    const updated = await savePracticeAnswers(current.value.id, payload)
    current.value = updated
    if (showMessage) {
      ElMessage.success('答案已保存')
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '答案保存失败')
    throw error
  } finally {
    saving.value = false
  }
}

async function submit(): Promise<void> {
  if (!current.value || current.value.status === 'SUBMITTED') {
    return
  }
  if (answeredCount.value === 0) {
    ElMessage.warning('至少完成一道题再交卷')
    return
  }
  if (answeredCount.value < current.value.questionCount) {
    try {
      await ElMessageBox.confirm(
        `还有 ${current.value.questionCount - answeredCount.value} 道题未作答，确认交卷吗？`,
        '确认交卷',
        { type: 'warning' }
      )
    } catch {
      return
    }
  }
  submitting.value = true
  try {
    await saveDraft(false)
    current.value = await submitPracticeSet(current.value.id)
    await loadHistory()
    ElMessage.success('交卷完成')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '交卷失败')
  } finally {
    submitting.value = false
  }
}

function optionLabel(option: string, index: number): string {
  if (/^[A-H][.、:：]\s*/.test(option)) {
    return option
  }
  return `${String.fromCharCode(65 + index)}. ${option}`
}

function optionValue(index: number): string {
  return String.fromCharCode(65 + index)
}

function statusLabel(status: string): string {
  return status === 'SUBMITTED' ? '已交卷' : '进行中'
}

function durationLabel(seconds?: number): string {
  if (seconds == null) {
    return '未完成'
  }
  const minutes = Math.floor(seconds / 60)
  return minutes > 0 ? `${minutes} 分钟` : `${seconds} 秒`
}

function selectMode(value: string | number | boolean | undefined): void {
  const next = value === 'EXPERT' ? 'EXPERT' : 'STANDARD'
  if (next === 'EXPERT' && aiAccess.value.expertRemaining <= 0) {
    ElMessage.warning('今日专家模式次数已用完')
    mode.value = 'STANDARD'
    return
  }
  mode.value = next
}
</script>

<template>
  <div v-loading="loading" class="ledger-page practice-page">
    <header class="ledger-header">
      <div>
        <div class="ledger-kicker">AI 学习助手 · 练习场</div>
        <h1 class="ledger-title">一套题，一次完整的检验</h1>
        <p class="ledger-subtitle">
          选择课程生成选择题、填空题与简答题，交卷后查看逐题解析；每次生成都会留下练习时间与成绩。
        </p>
      </div>
      <div class="ledger-date">
        <el-tag type="success">普通模式免费</el-tag>
        <el-tag type="warning" style="margin-left: 6px">专家模式每日 3 次</el-tag>
      </div>
    </header>

    <div class="practice-layout">
      <aside class="practice-history">
        <div class="practice-history-head">
          <div>
            <div class="chat-rail-title">练习记录</div>
            <p>时间、成绩与用时</p>
          </div>
          <el-button type="primary" size="small" :icon="Plus" @click="current = null">
            新练习
          </el-button>
        </div>
        <el-radio-group v-model="historyFilter" size="small" class="practice-history-filter">
          <el-radio-button label="all">全部</el-radio-button>
          <el-radio-button label="IN_PROGRESS">进行中</el-radio-button>
          <el-radio-button label="SUBMITTED">已交卷</el-radio-button>
        </el-radio-group>
        <div v-if="filteredHistory.length === 0" class="empty-ledger">还没有练习记录。</div>
        <button
          v-for="item in filteredHistory"
          :key="item.id"
          type="button"
          class="practice-history-item"
          :class="{ active: current?.id === item.id }"
          @click="openSet(item.id)"
        >
          <span class="practice-history-title">{{ item.title }}</span>
          <span class="practice-history-meta">
            {{ statusLabel(item.status) }}
            <template v-if="item.score != null"> · {{ item.score }} 分</template>
            · {{ formatMonthDayTime(item.startedAt, '') }}
          </span>
        </button>
      </aside>

      <section class="practice-main">
        <template v-if="!current">
          <div class="practice-start">
            <div class="practice-start-title">开始一套新练习</div>
            <el-form label-position="top" class="practice-start-form">
              <el-form-item label="课程">
                <el-select
                  v-model="subjectId"
                  placeholder="选择课程"
                  style="width: 100%"
                  @change="handleSubjectChange"
                >
                  <el-option
                    v-for="subject in subjects"
                    :key="subject.id"
                    :label="subject.name"
                    :value="subject.id"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="知识点（可选）">
                <el-select
                  v-model="knowledgePointId"
                  clearable
                  :disabled="!subjectId"
                  placeholder="不选则在课程范围内出题"
                  style="width: 100%"
                >
                  <el-option
                    v-for="point in knowledgePoints"
                    :key="point.id"
                    :label="point.name"
                    :value="point.id"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="模式">
                <el-radio-group :model-value="mode" @update:model-value="selectMode">
                  <el-radio-button label="STANDARD">普通</el-radio-button>
                  <el-radio-button label="EXPERT">专家</el-radio-button>
                </el-radio-group>
                <div class="practice-mode-hint">{{ modeHint }}</div>
              </el-form-item>
            </el-form>
            <el-button
              type="primary"
              size="large"
              :icon="ArrowRight"
              :loading="generating"
              @click="generateSet"
            >
              生成 6 道题
            </el-button>
          </div>
        </template>

        <template v-else>
          <div class="practice-set-header">
            <div>
              <div class="practice-meta">
                {{ current.subjectName ?? '课程' }}
                <template v-if="current.knowledgePointName"> · {{ current.knowledgePointName }}</template>
                · {{ statusLabel(current.status) }}
                · {{ current.billingMode === 'EXPERT' ? '专家模式' : '普通模式' }}
              </div>
              <h2>{{ current.title }}</h2>
            </div>
            <div class="practice-score" v-if="isSubmitted">
              <span>{{ current.score ?? 0 }}</span>
              <small>分</small>
            </div>
            <div v-else class="practice-progress">
              <span>{{ answeredCount }}/{{ current.questionCount }}</span>
              <small>已作答</small>
            </div>
          </div>

          <div class="practice-progress-track">
            <div class="practice-progress-fill" :style="{ width: `${isSubmitted ? (current.score ?? 0) : progress}%` }" />
          </div>

          <div class="practice-questions">
            <article
              v-for="(item, index) in current.items"
              :key="item.id"
              class="practice-question"
              :class="{
                correct: item.correct === true,
                incorrect: item.correct === false
              }"
            >
              <div class="practice-question-head">
                <span class="practice-question-no">{{ index + 1 }}</span>
                <el-tag size="small" effect="plain">
                  {{ item.questionType === 'single_choice' ? '单选' : item.questionType === 'fill_blank' ? '填空' : '简答' }}
                </el-tag>
                <span class="practice-question-score">{{ item.maxScore }} 分</span>
              </div>
              <div class="practice-stem">
                <MarkdownContent :content="item.stem" />
              </div>

              <el-radio-group
                v-if="item.questionType === 'single_choice' && !isSubmitted"
                v-model="answers[item.id]"
                class="practice-options"
              >
                <el-radio
                  v-for="(option, optionIndex) in item.options"
                  :key="optionIndex"
                  :label="optionValue(optionIndex)"
                  class="practice-option"
                >
                  <MarkdownContent :content="optionLabel(option, optionIndex)" />
                </el-radio>
              </el-radio-group>
              <el-input
                v-else-if="item.questionType === 'fill_blank' && !isSubmitted"
                v-model="answers[item.id]"
                placeholder="填写答案"
              />
              <el-input
                v-else-if="item.questionType === 'short_answer' && !isSubmitted"
                v-model="answers[item.id]"
                type="textarea"
                :rows="5"
                maxlength="20000"
                placeholder="写出你的思路与结论"
              />

              <div v-else-if="isSubmitted" class="practice-review">
                <div class="practice-answer-line">
                  你的答案：{{ item.studentAnswer || '未作答' }}
                </div>
                <div class="practice-answer-line">
                  参考答案：{{ item.correctAnswer || '无' }}
                </div>
                <div v-if="item.feedback" class="practice-feedback">
                  {{ item.feedback }}
                </div>
                <div v-if="item.explanation" class="practice-explanation">
                  <MarkdownContent :content="item.explanation" />
                </div>
              </div>
            </article>
          </div>

          <div class="practice-actions">
            <template v-if="!isSubmitted">
              <el-button :loading="saving" @click="saveDraft()">保存答案</el-button>
              <el-button type="primary" :icon="DocumentChecked" :loading="submitting" @click="submit">
                交卷
              </el-button>
            </template>
            <template v-else>
              <span class="practice-complete-meta">
                正确 {{ current.correctCount }}/{{ current.questionCount }} · 用时 {{ durationLabel(current.durationSeconds) }}
                · {{ formatDateTime(current.submittedAt) }}
              </span>
              <el-button type="primary" :icon="Refresh" :loading="generating" @click="generateSet">
                再来一套
              </el-button>
            </template>
          </div>
        </template>
      </section>
    </div>
  </div>
</template>

<style scoped>
.practice-page {
  display: flex;
  height: 100%;
  min-height: 0;
  flex-direction: column;
}

.practice-layout {
  display: grid;
  flex: 1 1 auto;
  grid-template-columns: minmax(250px, 0.28fr) minmax(0, 1fr);
  min-height: 0;
  border-bottom: 1px solid var(--rule);
}

.practice-history {
  display: flex;
  min-height: 0;
  flex-direction: column;
  padding: 18px 16px 18px 0;
  border-right: 1px solid var(--rule);
}

.practice-history-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.practice-history-head p {
  margin: 6px 0 0;
  color: var(--ink-soft);
  font-size: 12px;
}

.practice-history-filter {
  margin: 14px 0 10px;
}

.practice-history-item {
  display: flex;
  flex-direction: column;
  gap: 5px;
  padding: 11px 10px;
  text-align: left;
  background: transparent;
  border: 0;
  border-bottom: 1px solid var(--rule);
  cursor: pointer;
}

.practice-history-item:hover,
.practice-history-item.active {
  background: var(--paper-deep);
}

.practice-history-title {
  overflow: hidden;
  color: var(--ink);
  font-size: 14px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.practice-history-meta {
  color: var(--ink-soft);
  font-size: 12px;
}

.practice-main {
  min-width: 0;
  min-height: 0;
  padding-left: 24px;
  overflow-y: auto;
}

.practice-start {
  max-width: 520px;
  padding: 32px 0;
}

.practice-start-title {
  font-family: var(--font-display);
  font-size: 24px;
  font-weight: 600;
}

.practice-start-form {
  margin-top: 22px;
}

.practice-mode-hint {
  margin-top: 6px;
  color: var(--ink-soft);
  font-size: 12px;
}

.practice-set-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 20px 0 12px;
}

.practice-meta {
  color: var(--ink-soft);
  font-size: 12px;
}

.practice-set-header h2 {
  margin: 6px 0 0;
  font-family: var(--font-display);
  font-size: 22px;
}

.practice-score,
.practice-progress {
  display: flex;
  align-items: baseline;
  gap: 3px;
  color: var(--primary);
  font-family: var(--font-numeric);
  font-size: 28px;
  font-weight: 700;
}

.practice-score small,
.practice-progress small {
  color: var(--ink-soft);
  font-size: 12px;
  font-weight: 400;
}

.practice-progress-track {
  height: 3px;
  background: var(--rule);
}

.practice-progress-fill {
  height: 3px;
  background: var(--primary);
  transition: width 0.25s ease;
}

.practice-questions {
  padding: 8px 0 30px;
}

.practice-question {
  padding: 20px 0;
  border-bottom: 1px solid var(--rule);
}

.practice-question-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.practice-question-no {
  display: inline-flex;
  width: 26px;
  height: 26px;
  align-items: center;
  justify-content: center;
  color: var(--primary);
  font-family: var(--font-numeric);
  border: 1px solid var(--primary);
}

.practice-question-score {
  margin-left: auto;
  color: var(--ink-soft);
  font-size: 12px;
}

.practice-stem {
  margin: 12px 0 14px;
}

.practice-options {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 8px;
}

.practice-option {
  height: auto;
  margin: 0;
  padding: 8px 10px;
  border: 1px solid var(--rule);
}

.practice-option :deep(.el-radio__label) {
  width: 100%;
}

.practice-review {
  padding: 12px 14px;
  background: var(--paper-deep);
}

.practice-answer-line {
  margin: 3px 0;
  font-size: 13px;
}

.practice-feedback {
  margin-top: 8px;
  color: var(--ink-soft);
  font-size: 13px;
}

.practice-explanation {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid var(--rule);
}

.practice-question.correct {
  border-left: 3px solid var(--jade);
  padding-left: 12px;
}

.practice-question.incorrect {
  border-left: 3px solid var(--cinnabar);
  padding-left: 12px;
}

.practice-actions {
  position: sticky;
  bottom: 0;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  padding: 14px 0;
  background: var(--paper);
  border-top: 1px solid var(--rule);
}

.practice-complete-meta {
  margin-right: auto;
  color: var(--ink-soft);
  font-size: 12px;
}

@media (max-width: 900px) {
  .practice-layout {
    grid-template-columns: 1fr;
  }

  .practice-history {
    max-height: 220px;
    border-right: 0;
    border-bottom: 1px solid var(--rule);
  }

  .practice-main {
    padding-left: 0;
  }
}
</style>
