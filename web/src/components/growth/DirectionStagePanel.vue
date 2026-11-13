<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, reactive, ref } from 'vue'

import type { GrowthWorkspace } from '@/api/growth'
import {
  generateCareerAnalysis,
  getCareerProfile,
  listCareerAnalyses,
  saveCareerProfile,
  updateCareerConsent,
  type CareerAnalysis,
  type CareerProfile
} from '@/api/career'

const props = defineProps<{
  workspace: GrowthWorkspace
  readOnly: boolean
}>()

const emit = defineEmits<{
  (event: 'refresh'): void
}>()

const latestAnalysis = computed(() => props.workspace.recentAnalyses[0])
const drawerVisible = ref(false)
const loading = ref(false)
const saving = ref(false)
const generating = ref(false)
const analyses = ref<CareerAnalysis[]>([])
const editForm = reactive({
  major: '',
  gradeLevel: '',
  weeklyHours: undefined as number | undefined,
  interestText: '',
  strengthNotes: '',
  careerGoal: ''
})
const consent = reactive({
  granted: false,
  includeLearningRecords: true,
  includeGoals: true,
  includeGrades: true
})

async function openEditor(): Promise<void> {
  drawerVisible.value = true
  loading.value = true
  try {
    const [profile, list] = await Promise.all([getCareerProfile(), listCareerAnalyses()])
    applyProfile(profile)
    analyses.value = list
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '生涯画像加载失败')
  } finally {
    loading.value = false
  }
}

function applyProfile(profile: CareerProfile): void {
  editForm.major = profile.major ?? ''
  editForm.gradeLevel = profile.gradeLevel ?? ''
  editForm.weeklyHours = profile.weeklyHours ?? undefined
  editForm.interestText = (profile.interestTags ?? []).join('、')
  editForm.strengthNotes = profile.strengthNotes ?? ''
  editForm.careerGoal = profile.careerGoal ?? ''
  consent.granted = profile.consentGranted
  consent.includeLearningRecords = profile.includeLearningRecords
  consent.includeGoals = profile.includeGoals
  consent.includeGrades = profile.includeGrades
}

async function saveProfile(): Promise<void> {
  saving.value = true
  try {
    const profile = await saveCareerProfile({
      major: editForm.major,
      gradeLevel: editForm.gradeLevel,
      weeklyHours: editForm.weeklyHours ?? null,
      interestTags: editForm.interestText.split(/[、,，]/).map((item) => item.trim()).filter(Boolean),
      strengthNotes: editForm.strengthNotes,
      careerGoal: editForm.careerGoal
    })
    applyProfile(profile)
    ElMessage.success('方向画像已保存')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '方向画像保存失败')
  } finally {
    saving.value = false
  }
}

async function saveConsent(): Promise<void> {
  try {
    applyProfile(await updateCareerConsent({ ...consent }))
    ElMessage.success('数据授权已更新')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '数据授权更新失败')
  }
}

async function generateAnalysis(): Promise<void> {
  if (!consent.granted) {
    ElMessage.warning('请先授权生涯分析所需的数据范围')
    return
  }
  try {
    await ElMessageBox.confirm(
      '本次 AI 分析会按个人中心当前模型扣除积分，结果保存为方向分析记录。',
      '生成方向分析',
      { type: 'warning' }
    )
  } catch {
    return
  }
  generating.value = true
  try {
    await generateCareerAnalysis(editForm.careerGoal || undefined)
    analyses.value = await listCareerAnalyses()
    ElMessage.success('方向分析已生成')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '方向分析生成失败')
  } finally {
    generating.value = false
  }
}

function applyDirection(): void {
  drawerVisible.value = false
  emit('refresh')
}
</script>

<template>
  <section class="direction-stage">
    <header class="stage-head">
      <div>
        <h2>先确认方向，再开始推进</h2>
        <p>方向来自当前生涯画像。每个周期保存一份快照，后续变化不会改写历史。</p>
      </div>
      <el-button v-if="!readOnly" type="primary" plain @click="openEditor">
        {{ workspace.direction ? '管理方向画像' : '确认当前方向' }}
      </el-button>
    </header>

    <div v-if="workspace.direction" class="direction-snapshot">
      <span class="direction-kicker">当前周期方向</span>
      <p>{{ workspace.direction.summary }}</p>
      <div class="direction-meta">
        <span>来源分析：{{ workspace.direction.sourceAnalysisId ? `#${workspace.direction.sourceAnalysisId}` : '方向画像' }}</span>
        <span>最近更新：{{ workspace.direction.updatedAt.slice(0, 16).replace('T', ' ') }}</span>
      </div>
    </div>
    <div v-else class="stage-empty">
      <strong>还没有方向快照</strong>
      <span>先填写个人中心里的生涯画像，或完成一次生涯分析，再把方向固定到本周期。</span>
    </div>

    <div class="direction-context">
      <div>
        <span>生涯数据授权</span>
        <strong>{{ workspace.summary.careerConsentGranted ? '已授权' : '未授权' }}</strong>
      </div>
      <div>
        <span>分析记录</span>
        <strong>{{ workspace.summary.careerAnalysisCount }} 份</strong>
      </div>
      <div>
        <span>最近一次方向分析</span>
        <strong>{{ latestAnalysis ? latestAnalysis.title : '暂无' }}</strong>
      </div>
    </div>

    <el-drawer v-model="drawerVisible" title="方向画像" size="520px">
      <div v-loading="loading" class="direction-editor">
        <el-form label-position="top">
          <el-form-item label="生涯目标">
            <el-input v-model="editForm.careerGoal" type="textarea" :rows="3" maxlength="500" placeholder="例如：进入互联网公司从事后端开发" />
          </el-form-item>
          <div class="editor-row">
            <el-form-item label="专业">
              <el-input v-model="editForm.major" maxlength="128" />
            </el-form-item>
            <el-form-item label="年级">
              <el-input v-model="editForm.gradeLevel" maxlength="32" />
            </el-form-item>
          </div>
          <el-form-item label="每周可投入时间">
            <el-input-number v-model="editForm.weeklyHours" :min="1" :max="168" />
          </el-form-item>
          <el-form-item label="兴趣方向">
            <el-input v-model="editForm.interestText" placeholder="用逗号或顿号分隔" />
          </el-form-item>
          <el-form-item label="优势与补充">
            <el-input v-model="editForm.strengthNotes" type="textarea" :rows="3" maxlength="1000" />
          </el-form-item>
        </el-form>

        <div class="consent-block">
          <div class="consent-title">数据授权</div>
          <el-switch v-model="consent.granted" active-text="允许生成方向分析" />
          <div class="consent-options">
            <el-checkbox v-model="consent.includeLearningRecords">学习记录</el-checkbox>
            <el-checkbox v-model="consent.includeGoals">学习目标</el-checkbox>
            <el-checkbox v-model="consent.includeGrades">成绩</el-checkbox>
          </div>
        </div>

        <div class="editor-actions">
          <el-button @click="saveConsent">保存授权</el-button>
          <el-button :loading="saving" @click="saveProfile">保存画像</el-button>
          <el-button type="primary" :loading="generating" @click="generateAnalysis">生成方向分析</el-button>
        </div>

        <div v-if="analyses.length" class="analysis-preview">
          <div class="consent-title">最近分析</div>
          <strong>{{ analyses[0].title }}</strong>
          <p>{{ analyses[0].summary || '这份分析没有摘要。' }}</p>
          <span>{{ analyses[0].createdAt.slice(0, 16).replace('T', ' ') }}</span>
        </div>

        <el-button class="apply-direction-button" type="primary" plain @click="applyDirection">
          使用当前方向更新本周期快照
        </el-button>
      </div>
    </el-drawer>
  </section>
</template>

<style scoped>
.direction-stage {
  display: grid;
  gap: 20px;
  padding-top: 18px;
}

.stage-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--rule);
}

.stage-head h2 {
  margin: 0;
  color: var(--ink);
  font-family: var(--font-serif);
  font-size: 22px;
}

.stage-head p,
.direction-snapshot p {
  max-width: 680px;
  margin: 7px 0 0;
  color: var(--ink-soft);
  font-size: 12px;
  line-height: 1.8;
}

.direction-snapshot {
  display: grid;
  gap: 7px;
  padding: 18px 0;
  border-bottom: 1px solid var(--rule);
}

.direction-kicker {
  color: var(--primary-deep);
  font-size: 11px;
  font-weight: 700;
}

.direction-snapshot p {
  color: var(--ink);
  font-family: var(--font-serif);
  font-size: 18px;
}

.direction-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  color: var(--ink-soft);
  font-size: 10px;
}

.direction-context {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  padding-top: 4px;
}

.direction-context div {
  display: grid;
  gap: 4px;
  padding-left: 12px;
  border-left: 2px solid var(--rule-strong);
}

.direction-context span {
  color: var(--ink-soft);
  font-size: 10px;
}

.direction-context strong {
  color: var(--ink);
  font-size: 13px;
}

.stage-empty {
  display: grid;
  gap: 6px;
  padding: 24px 0;
  color: var(--ink-soft);
  font-size: 12px;
  line-height: 1.7;
}

.stage-empty strong {
  color: var(--ink);
  font-family: var(--font-serif);
  font-size: 17px;
}

.direction-editor {
  display: grid;
  gap: 16px;
}

.editor-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.consent-block,
.analysis-preview {
  display: grid;
  gap: 8px;
  padding: 14px 0;
  border-top: 1px solid var(--rule);
  border-bottom: 1px solid var(--rule);
}

.consent-title {
  color: var(--ink);
  font-size: 13px;
  font-weight: 700;
}

.consent-options {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 14px;
}

.editor-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.analysis-preview strong {
  color: var(--ink);
  font-family: var(--font-serif);
  font-size: 17px;
}

.analysis-preview p {
  margin: 0;
  color: var(--ink-soft);
  font-size: 12px;
  line-height: 1.7;
}

.analysis-preview span {
  color: var(--ink-soft);
  font-size: 10px;
}

.apply-direction-button {
  width: 100%;
}

@media (max-width: 620px) {
  .stage-head { flex-direction: column; }
  .direction-context { grid-template-columns: 1fr; }
}
</style>
