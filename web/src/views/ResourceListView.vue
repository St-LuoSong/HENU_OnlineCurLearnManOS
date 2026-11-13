<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'

import {
  createResource,
  deleteResource,
  listKnowledgePoints,
  listResources,
  listSubjects,
  updateResource,
  updateResourceStatus,
  type KnowledgePoint,
  type LearningResource,
  type Subject
} from '@/api/content'
import {
  attachToResource,
  detachResourceAttachment,
  listResourceAttachments,
  type FileAttachment
} from '@/api/files'
import FileAttachmentPanel from '@/components/FileAttachmentPanel.vue'
import { useAuthStore } from '@/stores/auth'
import { formatDateTime as formatDateTimeText } from '@/utils/date'

const auth = useAuthStore()
const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const subjects = ref<Subject[]>([])
const resources = ref<LearningResource[]>([])
const total = ref(0)
const page = ref(1)
const size = 12
const knowledgePoints = ref<KnowledgePoint[]>([])
const filterSubjectId = ref<number | undefined>()
const filterKnowledgePointId = ref<number | undefined>()
const filterKnowledgePoints = ref<KnowledgePoint[]>([])
const keyword = ref('')
const suppressKnowledgePointReset = ref(false)
const attachmentDialogVisible = ref(false)
const attachmentResource = ref<LearningResource | null>(null)
const attachments = ref<FileAttachment[]>([])
const attachmentLoading = ref(false)

const form = reactive({
  subjectId: undefined as number | undefined,
  title: '',
  description: '',
  resourceType: 'TEXT',
  content: '',
  externalUrl: '',
  knowledgePointIds: [] as number[]
})

const canManage = computed(() => auth.user?.roles.includes('ADMIN') === true)

const resourceTypeOptions = [
  { label: '文本', value: 'TEXT' },
  { label: '文档', value: 'DOCUMENT' },
  { label: '视频', value: 'VIDEO' },
  { label: '链接', value: 'LINK' }
]

onMounted(async () => {
  await loadSubjects()
  await loadResources()
})

watch(filterSubjectId, () => {
  filterKnowledgePointId.value = undefined
  void loadFilterKnowledgePoints()
  void loadResources()
})

watch(filterKnowledgePointId, () => {
  page.value = 1
  void loadResources()
})

watch(
  () => form.subjectId,
  async (subjectId) => {
    if (!suppressKnowledgePointReset.value) {
      form.knowledgePointIds = []
    }
    knowledgePoints.value = subjectId ? await listKnowledgePoints(subjectId) : []
    suppressKnowledgePointReset.value = false
  }
)

async function loadSubjects(): Promise<void> {
  try {
    subjects.value = await listSubjects()
  } catch {
    ElMessage.error('学科列表加载失败')
  }
}

async function loadResources(): Promise<void> {
  loading.value = true
  try {
    const result = await listResources({
      page: page.value,
      size,
      subjectId: filterSubjectId.value,
      knowledgePointId: filterKnowledgePointId.value,
      keyword: keyword.value.trim() || undefined
    })
    const lastPage = Math.max(1, Math.ceil(result.total / size))
    if (page.value > lastPage) {
      page.value = lastPage
      return loadResources()
    }
    resources.value = result.records
    total.value = result.total
  } catch {
    ElMessage.error('学习资源加载失败')
  } finally {
    loading.value = false
  }
}

async function loadFilterKnowledgePoints(): Promise<void> {
  try {
    filterKnowledgePoints.value = filterSubjectId.value
      ? await listKnowledgePoints(filterSubjectId.value)
      : []
  } catch {
    filterKnowledgePoints.value = []
  }
}

function search(): void {
  page.value = 1
  void loadResources()
}

function handlePageChange(value: number): void {
  page.value = value
  void loadResources()
}

function openDetail(resource: LearningResource): void {
  void router.push({ name: 'resource-detail', params: { id: resource.id } })
}

async function toggleStatus(resource: LearningResource): Promise<void> {
  const next = resource.status === 'PUBLISHED' ? 'DRAFT' : 'PUBLISHED'
  try {
    const updated = await updateResourceStatus(resource.id, next)
    const index = resources.value.findIndex((item) => item.id === resource.id)
    if (index >= 0) {
      resources.value[index] = updated
    }
    ElMessage.success(next === 'PUBLISHED' ? '资源已发布' : '资源已下架')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '资源状态更新失败')
  }
}

function openCreate(): void {
  editingId.value = null
  suppressKnowledgePointReset.value = false
  form.subjectId = filterSubjectId.value ?? subjects.value[0]?.id
  form.title = ''
  form.description = ''
  form.resourceType = 'TEXT'
  form.content = ''
  form.externalUrl = ''
  form.knowledgePointIds = []
  dialogVisible.value = true
}

function openEdit(resource: LearningResource): void {
  editingId.value = resource.id
  suppressKnowledgePointReset.value = form.subjectId !== resource.subjectId
  form.subjectId = resource.subjectId
  form.title = resource.title
  form.description = resource.description ?? ''
  form.resourceType = resource.resourceType
  form.content = resource.content ?? ''
  form.externalUrl = resource.externalUrl ?? ''
  form.knowledgePointIds = [...resource.knowledgePointIds]
  dialogVisible.value = true
}

async function submit(): Promise<void> {
  if (!form.subjectId) {
    ElMessage.warning('请选择学科')
    return
  }
  if (!form.title.trim()) {
    ElMessage.warning('请输入资源标题')
    return
  }

  const subjectId = form.subjectId
  saving.value = true
  try {
    const payload = {
      subjectId,
      title: form.title.trim(),
      description: form.description.trim() || undefined,
      resourceType: form.resourceType,
      content: form.content.trim() || undefined,
      externalUrl: form.externalUrl.trim() || undefined,
      knowledgePointIds: form.knowledgePointIds
    }
    if (editingId.value) {
      await updateResource(editingId.value, payload)
    } else {
      await createResource(payload)
    }
    ElMessage.success(editingId.value ? '资源已更新' : '资源已创建')
    dialogVisible.value = false
    await loadResources()
  } catch {
    ElMessage.error('资源保存失败')
  } finally {
    saving.value = false
  }
}

async function remove(resource: LearningResource): Promise<void> {
  try {
    await ElMessageBox.confirm(`确认删除资源“${resource.title}”吗？`, '删除确认', {
      type: 'warning'
    })
    await deleteResource(resource.id)
    ElMessage.success('资源已删除')
    await loadResources()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error('资源删除失败')
    }
  }
}

function asResource(row: unknown): LearningResource {
  return row as LearningResource
}

async function openAttachments(resource: LearningResource): Promise<void> {
  attachmentResource.value = resource
  attachmentDialogVisible.value = true
  await loadAttachments()
}

async function loadAttachments(): Promise<void> {
  if (!attachmentResource.value) {
    return
  }
  attachmentLoading.value = true
  try {
    attachments.value = await listResourceAttachments(attachmentResource.value.id)
  } catch {
    ElMessage.error('资源附件加载失败')
  } finally {
    attachmentLoading.value = false
  }
}

async function onAttachmentUploaded(fileId: number): Promise<void> {
  if (!attachmentResource.value) {
    return
  }
  try {
    await attachToResource(attachmentResource.value.id, fileId)
    await loadAttachments()
  } catch (error) {
    ElMessage.error(error instanceof Error && error.message ? error.message : '附件关联失败')
  }
}

async function onAttachmentRemove(attachment: FileAttachment): Promise<void> {
  if (!attachmentResource.value) {
    return
  }
  try {
    await detachResourceAttachment(attachmentResource.value.id, attachment.id)
    await loadAttachments()
    ElMessage.success('附件已移除')
  } catch (error) {
    ElMessage.error(error instanceof Error && error.message ? error.message : '附件移除失败')
  }
}

function formatDateTime(value?: string): string {
  return formatDateTimeText(value, '未记录')
}
</script>

<template>
  <div class="content-page">
    <el-alert
      v-if="!canManage"
      title="课程资源由管理员统一维护，当前为查看模式"
      type="info"
      show-icon
      :closable="false"
      class="content-notice"
    />

    <div class="page-toolbar">
      <div class="toolbar-filters">
        <el-select v-model="filterSubjectId" clearable placeholder="全部课程" style="width: 160px">
          <el-option
            v-for="subject in subjects"
            :key="subject.id"
            :label="subject.name"
            :value="subject.id"
          />
        </el-select>
        <el-select v-model="filterKnowledgePointId" clearable placeholder="全部知识点" style="width: 180px">
          <el-option
            v-for="point in filterKnowledgePoints"
            :key="point.id"
            :label="point.name"
            :value="point.id"
          />
        </el-select>
        <el-input
          v-model="keyword"
          clearable
          placeholder="搜索标题或说明"
          style="width: 220px"
          @keyup.enter="search"
        />
        <el-button @click="search">搜索</el-button>
      </div>
      <el-button v-if="canManage" type="primary" @click="openCreate">新建资源</el-button>
    </div>

    <el-table v-loading="loading" :data="resources" border @row-click="openDetail">
      <el-table-column label="资源标题" min-width="260">
        <template #default="{ row }">
          <button class="resource-title-link" @click.stop="openDetail(asResource(row))">
            {{ row.title }}
          </button>
          <div class="resource-summary">{{ row.description || '暂无说明' }}</div>
        </template>
      </el-table-column>
      <el-table-column prop="subjectName" label="课程" width="120" />
      <el-table-column label="知识点" min-width="180">
        <template #default="{ row }">
          <span v-if="(row.knowledgePointNames ?? []).length === 0">-</span>
          <el-tag v-for="name in row.knowledgePointNames" :key="name" class="tag-gap">
            {{ name }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="附件" width="180">
        <template #default="{ row }">
          <span v-if="row.attachmentCount === 0">暂无附件</span>
          <span v-else>
            {{ row.attachmentCount }} 个 · {{ row.primaryAttachment?.extension?.toUpperCase() }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="更新时间" width="170">
        <template #default="{ row }">
          {{ formatDateTime(row.updatedAt) }}
        </template>
      </el-table-column>
      <el-table-column v-if="canManage" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'PUBLISHED' ? 'success' : 'info'">
            {{ row.status === 'PUBLISHED' ? '已发布' : '草稿' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column v-if="canManage" label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click.stop="openEdit(asResource(row))">编辑</el-button>
          <el-button
            link
            type="primary"
            :disabled="row.status !== 'PUBLISHED' && row.attachmentCount === 0"
            @click.stop="toggleStatus(asResource(row))"
          >
            {{ row.status === 'PUBLISHED' ? '下架' : '发布' }}
          </el-button>
          <el-button link @click.stop="openAttachments(asResource(row))">附件管理</el-button>
          <el-button link type="danger" @click.stop="remove(asResource(row))">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-if="total > size"
      class="ledger-pagination"
      layout="total, prev, pager, next, jumper"
      :current-page="page"
      :page-size="size"
      :total="total"
      @current-change="handlePageChange"
    />

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑学习资源' : '新建学习资源'"
      width="680px"
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
        <el-form-item label="资源标题" required>
          <el-input v-model="form.title" maxlength="255" />
        </el-form-item>
        <el-form-item label="资源类型" required>
          <el-select v-model="form.resourceType" style="width: 100%">
            <el-option
              v-for="option in resourceTypeOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="关联知识点">
          <el-select
            v-model="form.knowledgePointIds"
            multiple
            collapse-tags
            placeholder="可多选"
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
        <el-form-item label="外部链接">
          <el-input v-model="form.externalUrl" maxlength="512" />
        </el-form-item>
        <el-form-item label="资源描述">
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="1000" />
        </el-form-item>
        <el-form-item label="资源内容">
          <el-input v-model="form.content" type="textarea" :rows="6" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="attachmentDialogVisible"
      :title="`资源附件：${attachmentResource?.title ?? ''}`"
      width="720px"
    >
      <FileAttachmentPanel
        :attachments="attachments"
        :loading="attachmentLoading"
        :uploadable="canManage"
        purpose="PLATFORM_RESOURCE"
        empty-text="该资源还没有上传教学文件。"
        hint="教师与管理员的资源附件对所有学习者开放下载与在线预览"
        @uploaded="onAttachmentUploaded"
        @remove="onAttachmentRemove"
      />
    </el-dialog>
  </div>
</template>

<style scoped>
.resource-title-link {
  padding: 0;
  color: var(--ink);
  font-family: var(--font-display);
  font-size: 16px;
  font-weight: 700;
  text-align: left;
  background: transparent;
  border: none;
  cursor: pointer;
}

.resource-title-link:hover {
  color: var(--jade);
}

.resource-summary {
  margin-top: 6px;
  color: var(--ink-soft);
  font-size: 13px;
  line-height: 1.7;
}
</style>
