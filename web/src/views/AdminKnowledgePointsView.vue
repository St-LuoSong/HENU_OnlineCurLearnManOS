<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import {
  createKnowledgePoint,
  deleteKnowledgePoint,
  listKnowledgePoints,
  listSubjects,
  updateKnowledgePoint,
  type KnowledgePoint,
  type KnowledgePointSaveRequest,
  type Subject
} from '@/api/content'

const loading = ref(false)
const saving = ref(false)
const subjects = ref<Subject[]>([])
const selectedSubjectId = ref<number | undefined>()
const keyword = ref('')
const points = ref<KnowledgePoint[]>([])
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)

const form = reactive({
  code: '',
  name: '',
  title: '',
  content: '',
  sourceType: 'TEXT',
  sourceUrl: '',
  keyPoints: [] as string[],
  commonMistakes: [] as string[],
  intuitionPumps: [] as string[]
})

onMounted(async () => {
  await loadSubjects()
})

watch(selectedSubjectId, () => {
  void loadPoints()
})

async function loadSubjects(): Promise<void> {
  try {
    subjects.value = await listSubjects()
    if (!selectedSubjectId.value && subjects.value.length > 0) {
      selectedSubjectId.value = subjects.value[0].id
    }
  } catch {
    ElMessage.error('课程列表加载失败')
  }
}

async function loadPoints(): Promise<void> {
  if (!selectedSubjectId.value) {
    points.value = []
    return
  }
  loading.value = true
  try {
    points.value = await listKnowledgePoints(
      selectedSubjectId.value,
      keyword.value.trim() || undefined
    )
  } catch {
    ElMessage.error('知识点加载失败')
  } finally {
    loading.value = false
  }
}

function openCreate(): void {
  if (!selectedSubjectId.value) {
    ElMessage.warning('请先选择课程')
    return
  }
  editingId.value = null
  form.code = ''
  form.name = ''
  form.title = ''
  form.content = ''
  form.sourceType = 'TEXT'
  form.sourceUrl = ''
  form.keyPoints = []
  form.commonMistakes = []
  form.intuitionPumps = []
  dialogVisible.value = true
}

function openEdit(point: KnowledgePoint): void {
  editingId.value = point.id
  form.code = point.code
  form.name = point.name
  form.title = point.title
  form.content = point.content
  form.sourceType = point.sourceType || 'TEXT'
  form.sourceUrl = point.sourceUrl ?? ''
  form.keyPoints = [...(point.keyPoints ?? [])]
  form.commonMistakes = [...(point.commonMistakes ?? [])]
  form.intuitionPumps = [...(point.intuitionPumps ?? [])]
  dialogVisible.value = true
}

async function submit(): Promise<void> {
  if (!selectedSubjectId.value) {
    ElMessage.warning('请先选择课程')
    return
  }
  if (
    !form.code.trim() ||
    !form.name.trim() ||
    !form.title.trim() ||
    !form.content.trim()
  ) {
    ElMessage.warning('请填写知识点代码、名称、标题与内容')
    return
  }

  const payload: KnowledgePointSaveRequest = {
    subjectId: selectedSubjectId.value,
    code: form.code.trim(),
    name: form.name.trim(),
    title: form.title.trim(),
    content: form.content.trim(),
    sourceType: form.sourceType,
    sourceUrl: form.sourceUrl.trim() || undefined,
    keyPoints: form.keyPoints,
    commonMistakes: form.commonMistakes,
    intuitionPumps: form.intuitionPumps
  }

  saving.value = true
  try {
    if (editingId.value) {
      await updateKnowledgePoint(editingId.value, payload)
    } else {
      await createKnowledgePoint(payload)
    }
    dialogVisible.value = false
    await loadPoints()
    ElMessage.success(editingId.value ? '知识点已更新' : '知识点已创建')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '知识点保存失败')
  } finally {
    saving.value = false
  }
}

async function remove(point: KnowledgePoint): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确认删除知识点“${point.name}”吗？已关联的题目与资源不会被自动删除。`,
      '删除知识点',
      { type: 'warning' }
    )
    await deleteKnowledgePoint(point.id)
    await loadPoints()
    ElMessage.success('知识点已删除')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error instanceof Error ? error.message : '知识点删除失败')
    }
  }
}
</script>

<template>
  <div v-loading="loading" class="ledger-page">
    <header class="ledger-header">
      <div>
        <div class="ledger-kicker">管理后台</div>
        <h1 class="ledger-title">知识点维护</h1>
        <p class="ledger-subtitle">
          知识点是学习资源、题库、练习评估与生涯分析共同依赖的课程骨架，由管理员统一维护。
        </p>
      </div>
      <div class="ledger-date">共 {{ points.length }} 个知识点</div>
    </header>

    <div class="page-toolbar">
      <div class="toolbar-filters">
        <el-select
          v-model="selectedSubjectId"
          placeholder="选择课程"
          style="width: 200px"
        >
          <el-option
            v-for="subject in subjects"
            :key="subject.id"
            :label="subject.name"
            :value="subject.id"
          />
        </el-select>
        <el-input
          v-model="keyword"
          clearable
          placeholder="搜索知识点名称或内容"
          style="width: 240px"
          @keyup.enter="loadPoints"
        />
        <el-button @click="loadPoints">查询</el-button>
      </div>
      <el-button type="primary" @click="openCreate">新建知识点</el-button>
    </div>

    <div v-if="points.length === 0" class="empty-ledger">
      该课程下还没有知识点。先创建知识点，再补充学习资源与题目。
    </div>

    <el-table v-else :data="points" border>
      <el-table-column prop="name" label="知识点" min-width="160" />
      <el-table-column prop="code" label="代码" width="180" />
      <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
      <el-table-column label="关键要点" width="100">
        <template #default="{ row }">{{ (row.keyPoints ?? []).length }}</template>
      </el-table-column>
      <el-table-column label="常见误区" width="100">
        <template #default="{ row }">{{ (row.commonMistakes ?? []).length }}</template>
      </el-table-column>
      <el-table-column label="直觉提示" width="100">
        <template #default="{ row }">{{ (row.intuitionPumps ?? []).length }}</template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row as KnowledgePoint)">编辑</el-button>
          <el-button link type="danger" @click="remove(row as KnowledgePoint)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑知识点' : '新建知识点'"
      width="720px"
    >
      <el-form label-width="96px">
        <el-form-item label="知识点代码" required>
          <el-input
            v-model="form.code"
            maxlength="64"
            placeholder="例如 calculus_limits，同一课程下唯一"
          />
        </el-form-item>
        <el-form-item label="知识点名称" required>
          <el-input v-model="form.name" maxlength="128" placeholder="例如 极限与连续" />
        </el-form-item>
        <el-form-item label="标题" required>
          <el-input v-model="form.title" maxlength="255" placeholder="例如 极限与连续基础" />
        </el-form-item>
        <el-form-item label="内容" required>
          <el-input v-model="form.content" type="textarea" :rows="6" />
        </el-form-item>
        <el-form-item label="关键要点">
          <el-select
            v-model="form.keyPoints"
            multiple
            filterable
            allow-create
            default-first-option
            :reserve-keyword="false"
            placeholder="输入后回车添加"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="常见误区">
          <el-select
            v-model="form.commonMistakes"
            multiple
            filterable
            allow-create
            default-first-option
            :reserve-keyword="false"
            placeholder="输入后回车添加"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="直觉提示">
          <el-select
            v-model="form.intuitionPumps"
            multiple
            filterable
            allow-create
            default-first-option
            :reserve-keyword="false"
            placeholder="输入后回车添加"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
