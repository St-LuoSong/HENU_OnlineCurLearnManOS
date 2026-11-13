<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import {
  createQuestion,
  deleteQuestion,
  listKnowledgePoints,
  listQuestions,
  listSubjects,
  updateQuestion,
  type KnowledgePoint,
  type Question,
  type QuestionOption,
  type QuestionSaveRequest,
  type Subject
} from '@/api/content'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const subjects = ref<Subject[]>([])
const questions = ref<Question[]>([])
const filterKnowledgePoints = ref<KnowledgePoint[]>([])
const formKnowledgePoints = ref<KnowledgePoint[]>([])
const filterSubjectId = ref<number | undefined>()
const filterKnowledgePointId = ref<number | undefined>()
const filterQuestionType = ref<string | undefined>()
const suppressKnowledgePointReset = ref(false)

const form = reactive({
  subjectId: undefined as number | undefined,
  knowledgePointId: undefined as number | undefined,
  questionType: 'CHOICE',
  difficulty: 2,
  content: '',
  correctAnswer: '',
  explanation: '',
  transfer: false,
  options: createDefaultOptions()
})

const canManage = computed(() => {
  return auth.user?.roles.some((role) => role === 'TEACHER' || role === 'ADMIN') ?? false
})

const questionTypeOptions = [
  { label: '选择题', value: 'CHOICE' },
  { label: '判断题', value: 'JUDGMENT' },
  { label: '问答题', value: 'QA' },
  { label: '填空题', value: 'FILL' },
  { label: '应用题', value: 'APPLICATION' }
]

const difficultyOptions = [1, 2, 3, 4, 5]

onMounted(async () => {
  if (!auth.user) {
    try {
      await auth.loadCurrentUser()
    } catch {
      return
    }
  }
  await loadSubjects()
  if (canManage.value) {
    await loadQuestions()
  }
})

watch(filterSubjectId, async (subjectId) => {
  filterKnowledgePointId.value = undefined
  filterKnowledgePoints.value = subjectId ? await listKnowledgePoints(subjectId) : []
  await loadQuestions()
})

watch(filterKnowledgePointId, () => {
  void loadQuestions()
})

watch(filterQuestionType, () => {
  void loadQuestions()
})

watch(
  () => form.subjectId,
  async (subjectId) => {
    if (!suppressKnowledgePointReset.value) {
      form.knowledgePointId = undefined
    }
    formKnowledgePoints.value = subjectId ? await listKnowledgePoints(subjectId) : []
    suppressKnowledgePointReset.value = false
  }
)

watch(
  () => form.questionType,
  (questionType) => {
    if (questionType !== 'CHOICE') {
      form.options = []
    } else if (form.options.length === 0) {
      form.options = createDefaultOptions()
    }
  }
)

async function loadSubjects(): Promise<void> {
  try {
    subjects.value = await listSubjects()
  } catch {
    ElMessage.error('学科列表加载失败')
  }
}

async function loadQuestions(): Promise<void> {
  if (!canManage.value) {
    return
  }
  loading.value = true
  try {
    questions.value = await listQuestions({
      subjectId: filterSubjectId.value,
      knowledgePointId: filterKnowledgePointId.value,
      questionType: filterQuestionType.value
    })
  } catch {
    ElMessage.error('题库加载失败')
  } finally {
    loading.value = false
  }
}

function openCreate(): void {
  editingId.value = null
  suppressKnowledgePointReset.value = false
  form.subjectId = filterSubjectId.value ?? subjects.value[0]?.id
  form.knowledgePointId = undefined
  form.questionType = 'CHOICE'
  form.difficulty = 2
  form.content = ''
  form.correctAnswer = ''
  form.explanation = ''
  form.transfer = false
  form.options = createDefaultOptions()
  dialogVisible.value = true
}

function openEdit(question: Question): void {
  editingId.value = question.id
  suppressKnowledgePointReset.value = form.subjectId !== question.subjectId
  form.subjectId = question.subjectId
  form.knowledgePointId = question.knowledgePointId
  form.questionType = question.questionType
  form.difficulty = question.difficulty
  form.content = question.content
  form.correctAnswer = question.correctAnswer
  form.explanation = question.explanation ?? ''
  form.transfer = question.transfer
  form.options = question.options.length > 0
    ? question.options.map((option) => ({ ...option }))
    : createDefaultOptions()
  dialogVisible.value = true
}

function addOption(): void {
  const nextKey = String.fromCharCode(65 + form.options.length)
  form.options.push({ key: nextKey, text: '' })
}

function removeOption(index: number): void {
  form.options.splice(index, 1)
}

async function submit(): Promise<void> {
  if (!form.subjectId) {
    ElMessage.warning('请选择学科')
    return
  }
  if (!form.knowledgePointId) {
    ElMessage.warning('请选择知识点')
    return
  }
  if (!form.content.trim() || !form.correctAnswer.trim()) {
    ElMessage.warning('请填写题目内容和正确答案')
    return
  }

  const options = form.questionType === 'CHOICE'
    ? form.options
        .filter((option) => option.text.trim())
        .map((option) => ({
          key: option.key.trim().toUpperCase(),
          text: option.text.trim()
        }))
    : []

  if (form.questionType === 'CHOICE' && options.length < 2) {
    ElMessage.warning('选择题至少需要两个选项')
    return
  }

  const subjectId = form.subjectId
  const knowledgePointId = form.knowledgePointId
  const payload: QuestionSaveRequest = {
    subjectId,
    knowledgePointId,
    questionType: form.questionType,
    difficulty: form.difficulty,
    content: form.content.trim(),
    correctAnswer: form.correctAnswer.trim(),
    explanation: form.explanation.trim() || undefined,
    transfer: form.transfer,
    options
  }

  saving.value = true
  try {
    if (editingId.value) {
      await updateQuestion(editingId.value, payload)
    } else {
      await createQuestion(payload)
    }
    ElMessage.success(editingId.value ? '题目已更新' : '题目已创建')
    dialogVisible.value = false
    await loadQuestions()
  } catch {
    ElMessage.error('题目保存失败')
  } finally {
    saving.value = false
  }
}

async function remove(question: Question): Promise<void> {
  try {
    await ElMessageBox.confirm('确认删除这道题目吗？', '删除确认', {
      type: 'warning'
    })
    await deleteQuestion(question.id)
    ElMessage.success('题目已删除')
    await loadQuestions()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error('题目删除失败')
    }
  }
}

function createDefaultOptions(): QuestionOption[] {
  return [
    { key: 'A', text: '' },
    { key: 'B', text: '' },
    { key: 'C', text: '' },
    { key: 'D', text: '' }
  ]
}

function asQuestion(row: unknown): Question {
  return row as Question
}
</script>

<template>
  <div class="content-page">
    <el-alert
      v-if="!canManage"
      title="题库管理仅对教师和管理员开放"
      type="warning"
      show-icon
      :closable="false"
    />

    <template v-else>
      <div class="page-toolbar">
        <div class="toolbar-filters">
          <el-select v-model="filterSubjectId" clearable placeholder="全部学科" style="width: 150px">
            <el-option
              v-for="subject in subjects"
              :key="subject.id"
              :label="subject.name"
              :value="subject.id"
            />
          </el-select>
          <el-select
            v-model="filterKnowledgePointId"
            clearable
            placeholder="全部知识点"
            style="width: 190px"
          >
            <el-option
              v-for="point in filterKnowledgePoints"
              :key="point.id"
              :label="point.name"
              :value="point.id"
            />
          </el-select>
          <el-select
            v-model="filterQuestionType"
            clearable
            placeholder="全部题型"
            style="width: 140px"
          >
            <el-option
              v-for="option in questionTypeOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
          <el-button @click="loadQuestions">刷新</el-button>
        </div>
        <el-button type="primary" @click="openCreate">新建题目</el-button>
      </div>

      <el-table v-loading="loading" :data="questions" border>
        <el-table-column prop="content" label="题目" min-width="320" show-overflow-tooltip />
        <el-table-column prop="subjectName" label="学科" width="100" />
        <el-table-column prop="knowledgePointName" label="知识点" width="140" />
        <el-table-column prop="questionType" label="题型" width="120" />
        <el-table-column prop="difficulty" label="难度" width="80" />
        <el-table-column label="迁移题" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.transfer" type="warning">是</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(asQuestion(row))">编辑</el-button>
            <el-button link type="danger" @click="remove(asQuestion(row))">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-dialog
        v-model="dialogVisible"
        :title="editingId ? '编辑题目' : '新建题目'"
        width="760px"
      >
        <el-form label-width="100px">
          <el-form-item label="学科" required>
            <el-select v-model="form.subjectId" placeholder="请选择学科" style="width: 100%">
              <el-option
                v-for="subject in subjects"
                :key="subject.id"
                :label="subject.name"
                :value="subject.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="知识点" required>
            <el-select v-model="form.knowledgePointId" placeholder="请选择知识点" style="width: 100%">
              <el-option
                v-for="point in formKnowledgePoints"
                :key="point.id"
                :label="point.name"
                :value="point.id"
              />
            </el-select>
          </el-form-item>
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="题型" required>
                <el-select v-model="form.questionType" style="width: 100%">
                  <el-option
                    v-for="option in questionTypeOptions"
                    :key="option.value"
                    :label="option.label"
                    :value="option.value"
                  />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="难度" required>
                <el-select v-model="form.difficulty" style="width: 100%">
                  <el-option
                    v-for="level in difficultyOptions"
                    :key="level"
                    :label="`${level} 级`"
                    :value="level"
                  />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="题目内容" required>
            <el-input v-model="form.content" type="textarea" :rows="4" />
          </el-form-item>
          <el-form-item label="正确答案" required>
            <el-input v-model="form.correctAnswer" />
          </el-form-item>
          <el-form-item label="解析">
            <el-input v-model="form.explanation" type="textarea" :rows="3" />
          </el-form-item>
          <el-form-item label="迁移测试">
            <el-switch v-model="form.transfer" />
          </el-form-item>
          <el-form-item v-if="form.questionType === 'CHOICE'" label="选项">
            <div class="option-editor">
              <div v-for="(option, index) in form.options" :key="index" class="option-row">
                <el-input v-model="option.key" class="option-key" maxlength="8" />
                <el-input v-model="option.text" />
                <el-button link type="danger" @click="removeOption(index)">删除</el-button>
              </div>
              <el-button link type="primary" @click="addOption">添加选项</el-button>
            </div>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
        </template>
      </el-dialog>
    </template>
  </div>
</template>

<style scoped>
.option-editor {
  width: 100%;
}

.option-row {
  display: grid;
  grid-template-columns: 70px minmax(0, 1fr) 60px;
  gap: 10px;
  margin-bottom: 10px;
}

.option-key {
  width: 70px;
}
</style>
