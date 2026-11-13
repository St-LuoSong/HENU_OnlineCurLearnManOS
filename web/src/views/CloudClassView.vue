<script setup lang="ts">
import { DocumentCopy } from '@element-plus/icons-vue'
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'

import {
  listKnowledgePoints,
  listSubjects,
  type KnowledgePoint,
  type Subject
} from '@/api/content'
import { useAuthStore } from '@/stores/auth'
import {
  attachToClassResource,
  attachToSubmission,
  detachClassResourceAttachment,
  detachSubmissionAttachment,
  downloadFile,
  isImageExtension,
  listClassResourceAttachments,
  listSubmissionAttachments,
  type FileAttachment,
  type FilePurpose,
  type MediaItem
} from '@/api/files'
import FileAttachmentPanel from '@/components/FileAttachmentPanel.vue'
import MediaGallery from '@/components/media/MediaGallery.vue'
import MediaUploader from '@/components/media/MediaUploader.vue'
import { formatDateTime as formatDateTimeText } from '@/utils/date'
import {
  createCloudClass,
  getCloudClassDetail,
  getCloudClassRoster,
  getCloudClassStatistics,
  gradeSubmission,
  joinCloudClass,
  listLearningClasses,
  listTaskSubmissions,
  listTeachingClasses,
  previewCloudClass,
  publishCloudResource,
  publishCloudTask,
  submitCloudTask,
  updateCloudClassStatus,
  updateCloudClassJoinEnabled,
  updateClassMemberStatus,
  updateCloudTaskStatus,
  type CloudClass,
  type CloudClassDetail,
  type CloudClassRosterMember,
  type CloudClassStatistics,
  type CloudClassSubmission,
  type CloudClassTask
} from '@/api/cloudClass'
import ClassOverviewPanel from '@/components/cloudclass/ClassOverviewPanel.vue'
import ClassStatisticsPanel from '@/components/cloudclass/ClassStatisticsPanel.vue'
import CloudClassRail from '@/components/cloudclass/CloudClassRail.vue'

const auth = useAuthStore()
const loading = ref(false)
const classes = ref<CloudClass[]>([])
const selectedClassId = ref<number | null>(null)
const activeTab = ref<'overview' | 'resources' | 'tasks' | 'members' | 'stats'>('overview')
const taskFilter = ref<'ALL' | 'OPEN' | 'DRAFT' | 'CLOSED'>('ALL')
const resourceTypeFilter = ref('ALL')
const memberKeyword = ref('')
const joinUpdating = ref(false)
const detail = ref<CloudClassDetail | null>(null)
const statistics = ref<CloudClassStatistics | null>(null)
const rosterMembers = ref<CloudClassRosterMember[]>([])
const rosterTotal = ref(0)
const rosterPage = ref(1)
const rosterReadOnly = ref(false)
const rosterLoading = ref(false)
const rosterPageSize = 100
const classDialogVisible = ref(false)
const joinDialogVisible = ref(false)
const resourceDialogVisible = ref(false)
const taskDialogVisible = ref(false)
const submitDialogVisible = ref(false)
const submissionsDialogVisible = ref(false)
const gradeDialogVisible = ref(false)
const attachmentDialogVisible = ref(false)
const attachmentTitle = ref('')
const attachmentClassResourceId = ref<number | null>(null)
const attachmentSubmissionId = ref<number | null>(null)
const attachmentUploadable = ref(false)
const attachments = ref<FileAttachment[]>([])
const attachmentLoading = ref(false)
const imageAttachments = computed<MediaItem[]>(() =>
  attachments.value.filter((item) => isImageExtension(item.originalName))
)
const documentAttachments = computed<FileAttachment[]>(() =>
  attachments.value.filter((item) => !isImageExtension(item.originalName))
)
const imagePurpose = computed<FilePurpose>(() =>
  attachmentSubmissionId.value ? 'SUBMISSION_IMAGE' : 'CLASS_RESOURCE_IMAGE'
)
const documentPurpose = computed<FilePurpose>(() =>
  attachmentSubmissionId.value ? 'SUBMISSION' : 'CLASS_RESOURCE'
)
const submissions = ref<CloudClassSubmission[]>([])
const selectedTaskId = ref<number | null>(null)
const selectedSubmission = ref<CloudClassSubmission | null>(null)

const subjects = ref<Subject[]>([])
const taskKnowledgePoints = ref<KnowledgePoint[]>([])
const classForm = reactive({
  subjectId: undefined as number | undefined,
  name: '',
  description: '',
  academicYear: '',
  semester: '',
  maxMembers: undefined as number | undefined
})
const joinCode = ref('')
const joinPreview = ref<CloudClass | null>(null)
const joinPreviewLoading = ref(false)
const resourceForm = reactive({
  title: '',
  resourceType: 'DOCUMENT',
  content: '',
  externalUrl: ''
})
const taskForm = reactive({
  title: '',
  description: '',
  taskType: 'TASK',
  knowledgePointId: undefined as number | undefined,
  dueAt: '',
  rewardPoints: 0,
  allowResubmit: false,
  allowLateSubmit: false,
  maxScore: undefined as number | undefined,
  status: 'PUBLISHED' as 'DRAFT' | 'PUBLISHED'
})
const submissionContent = ref('')
const gradeForm = reactive({
  score: 60,
  feedback: ''
})

const isTeacher = computed(() => auth.user?.roles.includes('TEACHER') === true)
const isStudent = computed(() => auth.user?.roles.includes('STUDENT') === true)
const selectedClass = computed(() => detail.value?.classInfo ?? null)
const joinAlreadyMember = computed(
  () => joinPreview.value !== null && classes.value.some((item) => item.id === joinPreview.value?.id)
)

const selectedStatus = computed(() => selectedClass.value?.status ?? 'ACTIVE')
const selectedStatusLabel = computed(() => {
  const labels: Record<string, string> = {
    ACTIVE: '进行中',
    CLOSED: '已结课',
    ARCHIVED: '已归档'
  }
  return labels[selectedStatus.value] ?? selectedStatus.value
})
const classMetrics = computed(() => {
  const item = selectedClass.value
  if (!item) {
    return []
  }
  if (isTeacher.value) {
    return [
      { label: '班级成员', value: statistics.value?.memberCount ?? item.memberCount, unit: '人' },
      { label: '课堂任务', value: statistics.value?.taskCount ?? item.taskCount, unit: '个' },
      {
        label: '待批改',
        value: statistics.value ? Math.max(0, statistics.value.submissionCount - statistics.value.gradedCount) : 0,
        unit: '份'
      },
      { label: '班级平均分', value: statistics.value?.averageScore ?? 0, unit: '分' }
    ]
  }
  return [
    { label: '课堂任务', value: item.taskCount, unit: '个' },
    { label: '待提交', value: item.myPendingTaskCount, unit: '个' },
    { label: '已逾期', value: item.myOverdueTaskCount, unit: '个' },
    { label: '我的平均分', value: item.myAverageScore ?? 0, unit: '分' }
  ]
})
const filteredTasks = computed(() => {
  const tasks = detail.value?.tasks ?? []
  const filtered = tasks.filter((task) => {
    if (taskFilter.value === 'ALL') return true
    if (taskFilter.value === 'OPEN') return task.status === 'PUBLISHED'
    if (taskFilter.value === 'DRAFT') return task.status === 'DRAFT'
    return task.status === 'CLOSED' || task.status === 'ARCHIVED'
  })
  return [...filtered].sort((left, right) =>
    String(left.dueAt ?? '9999').localeCompare(String(right.dueAt ?? '9999'))
  )
})
const filteredResources = computed(() => {
  const resources = detail.value?.resources ?? []
  return resourceTypeFilter.value === 'ALL'
    ? resources
    : resources.filter((resource) => resource.resourceType === resourceTypeFilter.value)
})
const filteredRosterMembers = computed(() => {
  const search = memberKeyword.value.trim().toLowerCase()
  if (!search) return rosterMembers.value
  return rosterMembers.value.filter((member) =>
    member.displayName.toLowerCase().includes(search)
    || member.loginId.toLowerCase().includes(search)
  )
})

onMounted(async () => {
  if (isTeacher.value) {
    await loadSubjects()
  }
  if (isTeacher.value || isStudent.value) {
    await loadClasses()
  }
})

async function loadSubjects(): Promise<void> {
  try {
    subjects.value = await listSubjects()
  } catch {
    ElMessage.error('课程列表加载失败')
  }
}

function openCreateClassDialog(): void {
  classDialogVisible.value = true
  if (subjects.value.length === 0) {
    void loadSubjects()
  }
}

function openJoinDialog(): void {
  joinDialogVisible.value = true
  joinCode.value = ''
  joinPreview.value = null
}

function classStatusLabel(status: string): string {
  if (status === 'ACTIVE') {
    return '班级开放中，可加入'
  }
  if (status === 'ARCHIVED') {
    return '班级已归档，暂不可加入'
  }
  if (status === 'CLOSED') {
    return '班级已关闭，暂不可加入'
  }
  return status
}

async function loadJoinPreview(): Promise<void> {
  const code = joinCode.value.trim().toUpperCase()
  if (code.length !== 6) {
    ElMessage.warning('请输入 6 位班级码')
    return
  }
  joinPreviewLoading.value = true
  try {
    joinPreview.value = await previewCloudClass(code)
  } catch (error) {
    joinPreview.value = null
    ElMessage.error(error instanceof Error ? error.message : '班级码校验失败')
  } finally {
    joinPreviewLoading.value = false
  }
}

async function openTaskDialog(): Promise<void> {
  taskDialogVisible.value = true
  taskForm.knowledgePointId = undefined
  const subjectId = selectedClass.value?.subjectId
  taskKnowledgePoints.value = subjectId ? await listKnowledgePoints(subjectId) : []
}

async function loadClasses(): Promise<void> {
  loading.value = true
  try {
    classes.value = isTeacher.value
      ? await listTeachingClasses()
      : await listLearningClasses()
    if (classes.value.length > 0) {
      await selectClass(classes.value[0].id)
    }
  } catch {
    ElMessage.error('云班级加载失败')
  } finally {
    loading.value = false
  }
}

async function selectClass(id: number): Promise<void> {
  const changedClass = selectedClassId.value !== id
  selectedClassId.value = id
  if (changedClass) {
    activeTab.value = 'overview'
    taskFilter.value = 'ALL'
    resourceTypeFilter.value = 'ALL'
    memberKeyword.value = ''
  }
  detail.value = await getCloudClassDetail(id)
  if (isTeacher.value) {
    statistics.value = await getCloudClassStatistics(id)
  }
  await loadRoster(id, 1)
}

function switchTab(tab: 'overview' | 'resources' | 'tasks' | 'members' | 'stats'): void {
  activeTab.value = tab
}

async function copyClassCode(): Promise<void> {
  if (!selectedClass.value?.classCode) {
    return
  }
  try {
    await navigator.clipboard.writeText(selectedClass.value.classCode)
    ElMessage.success('班级码已复制')
  } catch {
    ElMessage.warning('复制失败，请手动选择班级码')
  }
}

async function toggleJoinEnabled(): Promise<void> {
  if (!selectedClassId.value || !selectedClass.value) {
    return
  }
  joinUpdating.value = true
  try {
    const updated = await updateCloudClassJoinEnabled(
      selectedClassId.value,
      !selectedClass.value.joinEnabled
    )
    if (detail.value) {
      detail.value.classInfo = updated
    }
    const index = classes.value.findIndex((item) => item.id === updated.id)
    if (index >= 0) {
      classes.value[index] = updated
    }
    ElMessage.success(updated.joinEnabled ? '班级码加入入口已开放' : '班级码加入入口已关闭')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '班级码入口更新失败')
  } finally {
    joinUpdating.value = false
  }
}

function taskStatusLabel(status: string): string {
  const labels: Record<string, string> = {
    DRAFT: '草稿',
    PUBLISHED: '进行中',
    CLOSED: '已截止',
    ARCHIVED: '已归档'
  }
  return labels[status] ?? status
}

function taskStatusClass(status: string): string {
  if (status === 'PUBLISHED') return 'is-open'
  if (status === 'DRAFT') return 'is-draft'
  return 'is-closed'
}

function memberStatusLabel(status: string): string {
  const labels: Record<string, string> = {
    ACTIVE: '在班',
    SUSPENDED: '已暂停',
    REMOVED: '已移出'
  }
  return labels[status] ?? status
}

function selectTaskFilter(value: string): void {
  if (value === 'ALL' || value === 'OPEN' || value === 'DRAFT' || value === 'CLOSED') {
    taskFilter.value = value
  }
}

function dueState(task: CloudClassTask): 'overdue' | 'soon' | 'normal' {
  if (!task.dueAt) return 'normal'
  const diff = new Date(task.dueAt).getTime() - Date.now()
  if (diff < 0) return 'overdue'
  return diff <= 2 * 86_400_000 ? 'soon' : 'normal'
}

function resourceTypeLabel(type: string): string {
  const labels: Record<string, string> = {
    DOCUMENT: '文档',
    LINK: '链接',
    VIDEO: '视频',
    OTHER: '其他'
  }
  return labels[type] ?? type
}

function handleOverviewTask(): void {
  switchTab('tasks')
}

function handleOverviewResource(resource: { id: number; title: string }): void {
  activeTab.value = 'resources'
  void openClassResourceAttachments(resource)
}

async function loadRoster(classId: number, page: number): Promise<void> {
  rosterLoading.value = true
  try {
    const roster = await getCloudClassRoster(classId, page, rosterPageSize)
    rosterMembers.value = roster.members
    rosterTotal.value = roster.total
    rosterPage.value = roster.page
    rosterReadOnly.value = roster.readOnly
  } catch (error) {
    rosterMembers.value = []
    rosterTotal.value = 0
    ElMessage.error(error instanceof Error ? error.message : '班级名单加载失败')
  } finally {
    rosterLoading.value = false
  }
}

function handleRosterPageChange(page: number): void {
  const classId = selectedClassId.value
  if (!classId) {
    return
  }
  void loadRoster(classId, page)
}

async function submitCreateClass(): Promise<void> {
  if (!classForm.subjectId) {
    ElMessage.warning('请选择课程')
    return
  }
  if (!classForm.name.trim()) {
    ElMessage.warning('请输入班级名称')
    return
  }
  try {
    const created = await createCloudClass({
      subjectId: classForm.subjectId,
      name: classForm.name.trim(),
      description: classForm.description.trim() || undefined,
      academicYear: classForm.academicYear.trim() || undefined,
      semester: classForm.semester.trim() || undefined,
      maxMembers: classForm.maxMembers
    })
    classDialogVisible.value = false
    classForm.name = ''
    classForm.description = ''
    classForm.academicYear = ''
    classForm.semester = ''
    classForm.maxMembers = undefined
    await loadClasses()
    await selectClass(created.id)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '云班级创建失败')
  }
}

async function submitJoinClass(): Promise<void> {
  if (joinCode.value.trim().length !== 6) {
    ElMessage.warning('请输入 6 位班级码')
    return
  }
  try {
    const joined = await joinCloudClass(joinCode.value.trim().toUpperCase())
    joinDialogVisible.value = false
    joinCode.value = ''
    joinPreview.value = null
    await loadClasses()
    await selectClass(joined.id)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加入云班级失败')
  }
}

async function submitResource(): Promise<void> {
  if (!selectedClassId.value || !resourceForm.title.trim()) {
    ElMessage.warning('请输入资源标题')
    return
  }
  try {
    await publishCloudResource(selectedClassId.value, {
      title: resourceForm.title.trim(),
      resourceType: resourceForm.resourceType,
      content: resourceForm.content.trim() || undefined,
      externalUrl: resourceForm.externalUrl.trim() || undefined
    })
    resourceDialogVisible.value = false
    resourceForm.title = ''
    resourceForm.content = ''
    resourceForm.externalUrl = ''
    await selectClass(selectedClassId.value)
  } catch {
    ElMessage.error('资源发布失败')
  }
}

async function submitTask(): Promise<void> {
  if (!selectedClassId.value || !taskForm.title.trim()) {
    ElMessage.warning('请输入任务标题')
    return
  }
  try {
    await publishCloudTask(selectedClassId.value, {
      title: taskForm.title.trim(),
      description: taskForm.description.trim() || undefined,
      taskType: taskForm.taskType,
      knowledgePointId: taskForm.knowledgePointId,
      dueAt: taskForm.dueAt || undefined,
      rewardPoints: taskForm.rewardPoints,
      allowResubmit: taskForm.allowResubmit,
      allowLateSubmit: taskForm.allowLateSubmit,
      maxScore: taskForm.maxScore,
      status: taskForm.status
    })
    taskDialogVisible.value = false
    taskForm.title = ''
    taskForm.description = ''
    taskForm.dueAt = ''
    taskForm.knowledgePointId = undefined
    taskForm.allowResubmit = false
    taskForm.allowLateSubmit = false
    taskForm.maxScore = undefined
    taskForm.status = 'PUBLISHED'
    await selectClass(selectedClassId.value)
  } catch {
    ElMessage.error('任务发布失败')
  }
}

function openSubmit(taskId: number): void {
  selectedTaskId.value = taskId
  submissionContent.value = ''
  submitDialogVisible.value = true
}

async function submitTaskContent(): Promise<void> {
  if (!selectedTaskId.value || !submissionContent.value.trim()) {
    ElMessage.warning('请输入提交内容')
    return
  }
  try {
    await submitCloudTask(selectedTaskId.value, submissionContent.value.trim())
    submitDialogVisible.value = false
    if (selectedClassId.value) {
      await selectClass(selectedClassId.value)
    }
    ElMessage.success('提交成功')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '提交失败')
  }
}

async function openSubmissions(taskId: number): Promise<void> {
  selectedTaskId.value = taskId
  submissions.value = await listTaskSubmissions(taskId)
  submissionsDialogVisible.value = true
}

function openGrade(submission: CloudClassSubmission): void {
  selectedSubmission.value = submission
  gradeForm.score = submission.score ?? 60
  gradeForm.feedback = submission.feedback ?? ''
  gradeDialogVisible.value = true
}

function asSubmission(row: unknown): CloudClassSubmission {
  return row as CloudClassSubmission
}

async function openClassResourceAttachments(resource: {
  id: number
  title: string
}): Promise<void> {
  attachmentTitle.value = `教学资源附件：${resource.title}`
  attachmentClassResourceId.value = resource.id
  attachmentSubmissionId.value = null
  attachmentUploadable.value = isTeacher.value
  attachmentDialogVisible.value = true
  await loadAttachments()
}

async function openSubmissionAttachments(
  submissionId: number,
  title: string,
  uploadable: boolean
): Promise<void> {
  attachmentTitle.value = `作业附件：${title}`
  attachmentSubmissionId.value = submissionId
  attachmentClassResourceId.value = null
  attachmentUploadable.value = uploadable
    attachmentDialogVisible.value = true
    await loadAttachments()
}

async function openTaskSubmissionAttachments(
  submissionId: number | undefined,
  title: string
): Promise<void> {
  if (!submissionId) {
    return
  }
  await openSubmissionAttachments(submissionId, title, true)
}

async function openSubmissionAttachmentRow(submission: CloudClassSubmission): Promise<void> {
  await openSubmissionAttachments(
    submission.id,
    submission.studentName ?? '学生作业',
    false
  )
}

async function loadAttachments(): Promise<void> {
  attachmentLoading.value = true
  try {
    if (attachmentClassResourceId.value) {
      attachments.value = await listClassResourceAttachments(attachmentClassResourceId.value)
    } else if (attachmentSubmissionId.value) {
      attachments.value = await listSubmissionAttachments(attachmentSubmissionId.value)
    } else {
      attachments.value = []
    }
  } catch (error) {
    ElMessage.error(error instanceof Error && error.message ? error.message : '附件加载失败')
  } finally {
    attachmentLoading.value = false
  }
}

async function onAttachmentUploaded(fileId: number): Promise<void> {
  try {
    if (attachmentClassResourceId.value) {
      await attachToClassResource(attachmentClassResourceId.value, fileId)
    } else if (attachmentSubmissionId.value) {
      await attachToSubmission(attachmentSubmissionId.value, fileId)
    }
    await loadAttachments()
  } catch (error) {
    ElMessage.error(error instanceof Error && error.message ? error.message : '附件关联失败')
  }
}

async function removeAttachmentById(attachmentId: number): Promise<void> {
  try {
    if (attachmentClassResourceId.value) {
      await detachClassResourceAttachment(attachmentClassResourceId.value, attachmentId)
    } else if (attachmentSubmissionId.value) {
      await detachSubmissionAttachment(attachmentSubmissionId.value, attachmentId)
    }
    await loadAttachments()
    ElMessage.success('附件已移除')
  } catch (error) {
    ElMessage.error(error instanceof Error && error.message ? error.message : '附件移除失败')
  }
}

function onAttachmentRemove(attachment: FileAttachment): Promise<void> {
  return removeAttachmentById(attachment.id)
}

function onImageRemove(item: MediaItem): Promise<void> {
  if (typeof item.id !== 'number') {
    return Promise.resolve()
  }
  return removeAttachmentById(item.id)
}

async function onImageDownload(item: MediaItem): Promise<void> {
  try {
    await downloadFile(item.fileId, item.originalName)
  } catch (error) {
    ElMessage.error(error instanceof Error && error.message ? error.message : '下载失败')
  }
}

function asMember(row: unknown): CloudClassRosterMember {
  return row as CloudClassRosterMember
}

async function changeMemberStatus(
  member: Pick<CloudClassRosterMember, 'studentId'>,
  status: 'ACTIVE' | 'SUSPENDED' | 'REMOVED'
): Promise<void> {
  const classId = selectedClassId.value
  if (!classId) {
    return
  }
  try {
    await updateClassMemberStatus(classId, member.studentId, status)
    await selectClass(classId)
    ElMessage.success(status === 'REMOVED' ? '已移出该学生' : '成员状态已更新')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '成员状态更新失败')
  }
}

async function changeClassStatus(
  status: 'ACTIVE' | 'ARCHIVED' | 'CLOSED'
): Promise<void> {
  const classId = selectedClassId.value
  if (!classId) {
    return
  }
  try {
    await updateCloudClassStatus(classId, status)
    await loadClasses()
    await selectClass(classId)
    ElMessage.success(status === 'ACTIVE' ? '班级已恢复教学' : '班级状态已更新')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '班级状态更新失败')
  }
}

async function changeTaskStatus(
  task: CloudClassTask,
  status: 'DRAFT' | 'PUBLISHED' | 'CLOSED' | 'ARCHIVED'
): Promise<void> {
  const classId = selectedClassId.value
  if (!classId) {
    return
  }
  try {
    await updateCloudTaskStatus(task.id, status)
    await selectClass(classId)
    ElMessage.success('任务状态已更新')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '任务状态更新失败')
  }
}

async function submitGrade(): Promise<void> {
  if (!selectedSubmission.value) {
    return
  }
  try {
    const updated = await gradeSubmission(selectedSubmission.value.id, {
      score: gradeForm.score,
      feedback: gradeForm.feedback.trim() || undefined
    })
    const index = submissions.value.findIndex((item) => item.id === updated.id)
    if (index >= 0) {
      submissions.value[index] = updated
    }
    gradeDialogVisible.value = false
    ElMessage.success('批改结果已保存')
  } catch {
    ElMessage.error('批改保存失败')
  }
}

function formatDateTime(value?: string): string {
  return formatDateTimeText(value, '未设置')
}

function taskRuleLabel(task: CloudClassTask): string {
  const rules: string[] = []
  rules.push(task.allowLateSubmit ? '允许迟交' : '逾期不可交')
  rules.push(task.allowResubmit ? '允许重交' : '批改后不可重交')
  if (task.maxScore !== undefined && task.maxScore !== null) {
    rules.push(`满分 ${task.maxScore}`)
  }
  return rules.join(' · ')
}
</script>

<template>
  <div v-loading="loading" class="ledger-page">
    <header class="ledger-header">
      <div>
        <div class="ledger-kicker">云班级</div>
        <h1 class="ledger-title">{{ isTeacher ? '教学班级' : '我的班级' }}</h1>
        <p class="ledger-subtitle">
          {{ isTeacher
            ? '把课程资源、任务节奏和学生进度放在同一个课堂工作台。'
            : '查看课堂资源、完成任务，并追踪自己的提交与成绩。'
          }}
        </p>
      </div>
      <div class="ledger-date">
        {{ isTeacher ? '教师工作台' : '学生学习空间' }} · {{ classes.length }} 个班级
      </div>
    </header>

    <div class="cloud-class-layout">
      <CloudClassRail
        :classes="classes"
        :selected-id="selectedClassId"
        :is-teacher="isTeacher"
        @select="selectClass"
        @create="openCreateClassDialog"
        @join="openJoinDialog"
      />

      <section v-if="detail && selectedClass" class="cloud-class-main">
        <header class="class-hero">
          <div class="class-hero-copy">
            <div class="class-hero-meta">
              <span class="class-status-pill" :class="'status-' + selectedStatus.toLowerCase()">
                {{ selectedStatusLabel }}
              </span>
              <span>{{ selectedClass.subjectName || '未设置课程' }}</span>
              <span v-if="selectedClass.academicYear">{{ selectedClass.academicYear }}</span>
              <span v-if="selectedClass.semester">{{ selectedClass.semester }}</span>
            </div>
            <h2>{{ selectedClass.name }}</h2>
            <p>{{ selectedClass.description || '这个班级还没有补充说明。' }}</p>
            <div class="class-hero-facts">
              <span>指导教师 {{ selectedClass.teacherName || '未设置' }}</span>
              <span>成员 {{ selectedClass.memberCount }}<template v-if="selectedClass.maxMembers"> / {{ selectedClass.maxMembers }}</template></span>
              <span>任务 {{ selectedClass.taskCount }}</span>
              <span>资源 {{ selectedClass.resourceCount }}</span>
            </div>
          </div>
          <div class="class-code-panel">
            <span class="class-code-label">班级码</span>
            <strong>{{ selectedClass.classCode }}</strong>
            <button type="button" class="class-code-copy" @click="copyClassCode">
              <el-icon><DocumentCopy /></el-icon>
              复制
            </button>
            <small v-if="isTeacher">
              加入入口 {{ selectedClass.joinEnabled ? '开放中' : '已关闭' }}
            </small>
          </div>
        </header>

        <div class="class-metric-strip">
          <div v-for="metric in classMetrics" :key="metric.label" class="class-metric">
            <span>{{ metric.label }}</span>
            <strong>{{ metric.value }}<small>{{ metric.unit }}</small></strong>
          </div>
        </div>

        <div class="class-workspace-toolbar">
          <nav class="class-tab-nav" aria-label="班级内容导航">
            <button type="button" :class="{ active: activeTab === 'overview' }" @click="switchTab('overview')">概览</button>
            <button type="button" :class="{ active: activeTab === 'resources' }" @click="switchTab('resources')">资源</button>
            <button type="button" :class="{ active: activeTab === 'tasks' }" @click="switchTab('tasks')">任务</button>
            <button type="button" :class="{ active: activeTab === 'members' }" @click="switchTab('members')">成员</button>
            <button v-if="isTeacher" type="button" :class="{ active: activeTab === 'stats' }" @click="switchTab('stats')">统计</button>
          </nav>
          <div v-if="isTeacher" class="class-toolbar-actions">
            <el-button size="small" @click="resourceDialogVisible = true">发布资源</el-button>
            <el-button size="small" type="primary" @click="openTaskDialog">发布任务</el-button>
            <el-switch
              :model-value="selectedClass.joinEnabled"
              :loading="joinUpdating"
              inline-prompt
              active-text="加入开放"
              inactive-text="加入关闭"
              @change="toggleJoinEnabled"
            />
            <el-button
              v-if="selectedClass.status === 'ACTIVE'"
              size="small"
              @click="changeClassStatus('ARCHIVED')"
            >
              归档
            </el-button>
            <el-button v-else size="small" @click="changeClassStatus('ACTIVE')">恢复教学</el-button>
          </div>
        </div>

        <el-tabs v-model="activeTab" class="class-content-tabs">
          <el-tab-pane name="overview" label="概览">
            <ClassOverviewPanel
              :class-info="selectedClass"
              :tasks="detail.tasks"
              :resources="detail.resources"
              :statistics="statistics"
              :is-teacher="isTeacher"
              @open-task="handleOverviewTask"
              @open-resource="handleOverviewResource"
              @open-tasks="switchTab('tasks')"
            />
          </el-tab-pane>

          <el-tab-pane name="resources" label="教学资源">
            <div class="class-content-head">
              <div>
                <h3>教学资源</h3>
                <p>{{ filteredResources.length }} 份资源 · 按发布时间排列</p>
              </div>
              <div class="class-chip-filter">
                <button
                  v-for="type in ['ALL', 'DOCUMENT', 'LINK', 'VIDEO']"
                  :key="type"
                  type="button"
                  :class="{ active: resourceTypeFilter === type }"
                  @click="resourceTypeFilter = type"
                >
                  {{ type === 'ALL' ? '全部' : resourceTypeLabel(type) }}
                </button>
              </div>
            </div>
            <div v-if="filteredResources.length === 0" class="class-empty">
              <strong>暂无匹配资源</strong>
              <span>切换筛选条件，或等待教师发布新的课堂资源。</span>
            </div>
            <div v-for="resource in filteredResources" :key="resource.id" class="resource-row">
              <span class="resource-type">{{ resourceTypeLabel(resource.resourceType) }}</span>
              <div class="resource-copy">
                <strong>{{ resource.title }}</strong>
                <span>{{ resource.publisherName }} · {{ formatDateTime(resource.createdAt) }}</span>
                <p v-if="resource.content">{{ resource.content }}</p>
              </div>
              <el-button link type="primary" @click="openClassResourceAttachments(resource)">查看附件</el-button>
            </div>
          </el-tab-pane>

          <el-tab-pane name="tasks" label="任务与测试">
            <div class="class-content-head">
              <div>
                <h3>任务与测试</h3>
                <p>{{ filteredTasks.length }} 个活动 · 按截止时间排列</p>
              </div>
              <div class="class-chip-filter">
                <button
                  v-for="item in [
                    { value: 'ALL', label: '全部' },
                    { value: 'OPEN', label: '进行中' },
                    { value: 'DRAFT', label: '草稿' },
                    { value: 'CLOSED', label: '已结束' }
                  ]"
                  :key="item.value"
                  type="button"
                  :class="{ active: taskFilter === item.value }"
                  @click="selectTaskFilter(item.value)"
                >
                  {{ item.label }}
                </button>
              </div>
            </div>
            <div v-if="filteredTasks.length === 0" class="class-empty">
              <strong>暂无匹配任务</strong>
              <span>{{ isTeacher ? '发布任务后，学生会在这里看到提交入口。' : '切换筛选条件，或等待教师发布新的任务。' }}</span>
            </div>
            <div v-for="task in filteredTasks" :key="task.id" class="task-row" :class="taskStatusClass(task.status)">
              <div>
                <div class="task-row-head">
                  <span class="task-status-badge" :class="taskStatusClass(task.status)">
                    {{ taskStatusLabel(task.status) }}
                  </span>
                  <strong class="task-title">{{ task.title }}</strong>
                  <span v-if="task.dueAt" class="task-due" :class="'due-' + dueState(task)">
                    {{ dueState(task) === 'overdue' ? '已逾期' : formatDateTime(task.dueAt) }}
                  </span>
                </div>
                <div class="task-meta">
                  {{ task.taskType === 'TEST' ? '测试' : '任务' }} · 奖励 {{ task.rewardPoints }} 积分
                  <template v-if="task.maxScore"> · 满分 {{ task.maxScore }}</template>
                </div>
                <div v-if="task.knowledgePointName" class="task-meta">
                  关联知识点：{{ task.knowledgePointName }}
                </div>
                <div class="task-meta">{{ taskRuleLabel(task) }}</div>
                <div v-if="task.description" class="task-description">
                  {{ task.description }}
                </div>
              </div>
              <div class="task-actions">
                <el-button v-if="isStudent" link type="primary" @click="openSubmit(task.id)">
                  {{ task.mySubmissionStatus ? '重新提交' : '提交作业' }}
                </el-button>
                <el-button v-if="isTeacher" link type="primary" @click="openSubmissions(task.id)">
                  查看提交
                </el-button>
                <el-button
                  v-if="isStudent && task.mySubmissionId"
                  link
                  type="primary"
                  @click="openTaskSubmissionAttachments(task.mySubmissionId, task.title)"
                >
                  作业附件
                </el-button>
                <el-button
                  v-if="isTeacher && task.status === 'DRAFT'"
                  link
                  type="primary"
                  @click="changeTaskStatus(task, 'PUBLISHED')"
                >
                  发布
                </el-button>
                <el-button
                  v-if="isTeacher && task.status === 'PUBLISHED'"
                  link
                  type="warning"
                  @click="changeTaskStatus(task, 'CLOSED')"
                >
                  关闭提交
                </el-button>
                <el-button
                  v-if="isTeacher && (task.status === 'CLOSED' || task.status === 'ARCHIVED')"
                  link
                  type="primary"
                  @click="changeTaskStatus(task, 'PUBLISHED')"
                >
                  重新开放
                </el-button>
                <span v-if="task.myScore !== undefined" class="week-value">
                  {{ task.myScore }} 分
                </span>
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane name="members" :label="isTeacher ? '学生名单' : '班级名单'">
            <div class="class-content-head">
              <div>
                <h3>{{ isTeacher ? '学生名单' : '班级名单' }}</h3>
                <p>共 {{ rosterTotal }} 名成员</p>
              </div>
              <el-input v-model="memberKeyword" clearable placeholder="搜索姓名或学号" style="width: 220px" />
            </div>
            <div v-if="filteredRosterMembers.length === 0" class="class-empty">
              <strong>{{ rosterMembers.length === 0 ? '暂无成员' : '没有匹配的成员' }}</strong>
              <span>{{ isTeacher ? '学生加入后会显示在这里。' : '班级成员加入后会显示在这里。' }}</span>
            </div>
            <el-table v-else v-loading="rosterLoading" :data="filteredRosterMembers" class="class-member-table">
              <el-table-column prop="loginId" label="学号" width="150" />
              <el-table-column prop="displayName" label="姓名" width="120" />
              <el-table-column prop="joinedAt" label="加入时间" width="180">
                <template #default="{ row }">
                  {{ formatDateTime(row.joinedAt) }}
                </template>
              </el-table-column>
              <el-table-column label="成员状态" width="110">
                <template #default="{ row }">
                  <el-tag
                    size="small"
                    :type="row.status === 'ACTIVE' ? 'success' : row.status === 'SUSPENDED' ? 'warning' : 'info'"
                  >
                    {{ memberStatusLabel(row.status) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column
                v-if="isTeacher"
                prop="totalTaskCount"
                label="任务总数"
                width="100"
              />
              <el-table-column
                v-if="isTeacher"
                prop="submittedTaskCount"
                label="已提交"
                width="90"
              />
              <el-table-column
                v-if="isTeacher"
                prop="pendingTaskCount"
                label="待提交"
                width="90"
              />
              <el-table-column
                v-if="isTeacher"
                prop="overdueTaskCount"
                label="逾期"
                width="80"
              />
              <el-table-column
                v-if="isTeacher"
                prop="gradedCount"
                label="已批改"
                width="90"
              />
              <el-table-column
                v-if="isTeacher"
                prop="averageScore"
                label="平均分"
                width="90"
              />
              <el-table-column v-if="isTeacher" label="操作" width="150" fixed="right">
                <template #default="{ row }">
                  <el-button
                    link
                    type="primary"
                    @click="
                      changeMemberStatus(
                        asMember(row),
                        row.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE'
                      )
                    "
                  >
                    {{ row.status === 'ACTIVE' ? '暂停' : '恢复' }}
                  </el-button>
                  <el-button
                    link
                    type="danger"
                    @click="changeMemberStatus(asMember(row), 'REMOVED')"
                  >
                    移出
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
            <div v-if="rosterTotal > rosterPageSize" class="ledger-pagination">
              <el-pagination
                layout="prev, pager, next"
                :current-page="rosterPage"
                :page-size="rosterPageSize"
                :total="rosterTotal"
                background
                @current-change="handleRosterPageChange"
              />
            </div>
            <p v-if="rosterReadOnly" class="content-notice">
              名单为只读，仅显示同班成员的学号、姓名、加入时间与成员状态。
            </p>
          </el-tab-pane>

          <el-tab-pane v-if="isTeacher" name="stats" label="统计">
            <ClassStatisticsPanel :statistics="statistics" :members="rosterMembers" />
          </el-tab-pane>
        </el-tabs>
      </section>

      <div v-else class="chat-empty chat-empty-large">
        {{ isTeacher ? '新建或选择一个云班级。' : '使用班级码加入一个云班级。' }}
      </div>
    </div>

    <el-dialog v-model="classDialogVisible" title="新建云班级" width="520px">
      <el-form label-width="88px">
        <el-form-item label="课程" required>
          <el-select v-model="classForm.subjectId" placeholder="请选择课程" style="width: 100%">
            <el-option
              v-for="subject in subjects"
              :key="subject.id"
              :label="subject.name"
              :value="subject.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="班级名称" required>
          <el-input v-model="classForm.name" maxlength="128" />
        </el-form-item>
        <el-form-item label="学年">
          <el-input
            v-model="classForm.academicYear"
            maxlength="16"
            placeholder="例如 2026-2027"
          />
        </el-form-item>
        <el-form-item label="学期">
          <el-input
            v-model="classForm.semester"
            maxlength="16"
            placeholder="例如 第一学期"
          />
        </el-form-item>
        <el-form-item label="人数上限">
          <el-input-number v-model="classForm.maxMembers" :min="1" :max="10000" />
        </el-form-item>
        <el-form-item label="班级说明">
          <el-input v-model="classForm.description" type="textarea" :rows="4" maxlength="1000" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="classDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCreateClass">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="joinDialogVisible" title="加入云班级" width="460px">
      <el-input
        v-model="joinCode"
        maxlength="6"
        placeholder="请输入 6 位班级码"
        @input="joinPreview = null"
        @keyup.enter="loadJoinPreview"
      >
        <template #append>
          <el-button :loading="joinPreviewLoading" @click="loadJoinPreview">
            查询班级
          </el-button>
        </template>
      </el-input>
      <div v-if="joinPreview" class="summary-strip">
        <div class="summary-item">
          <span class="summary-value">{{ joinPreview.name }}</span>
          <span class="summary-label">班级名称</span>
        </div>
        <div class="summary-item">
          <span class="summary-value">{{ joinPreview.subjectName || '未设置' }}</span>
          <span class="summary-label">课程</span>
        </div>
        <div class="summary-item">
          <span class="summary-value">{{ joinPreview.teacherName || '未设置' }}</span>
          <span class="summary-label">任课教师</span>
        </div>
        <div class="summary-item">
          <span class="summary-value">{{ joinPreview.memberCount }}</span>
          <span class="summary-label">在班人数</span>
        </div>
      </div>
      <p v-if="joinPreview" class="content-notice">
        {{ joinPreview.academicYear || '未设置学年' }} ·
        {{ joinPreview.semester || '未设置学期' }} ·
        {{ classStatusLabel(joinPreview.status) }}
      </p>
      <p v-if="joinAlreadyMember" class="content-notice">
        你已加入该班级，无需重复加入。
      </p>
      <p v-if="joinPreview && !joinPreview.joinEnabled" class="content-notice">
        教师已关闭该班级的班级码加入入口，请联系任课教师。
      </p>
      <template #footer>
        <el-button @click="joinDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :disabled="!joinPreview || joinPreview.status !== 'ACTIVE' || joinAlreadyMember || !joinPreview.joinEnabled"
          @click="submitJoinClass"
        >
          {{ joinPreview && !joinPreview.joinEnabled ? '加入入口已关闭' : '确认加入' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="resourceDialogVisible" title="发布教学资源" width="620px">
      <el-form label-width="88px">
        <el-form-item label="资源标题" required>
          <el-input v-model="resourceForm.title" maxlength="255" />
        </el-form-item>
        <el-form-item label="资源类型">
          <el-select v-model="resourceForm.resourceType" style="width: 100%">
            <el-option label="文档" value="DOCUMENT" />
            <el-option label="视频" value="VIDEO" />
            <el-option label="链接" value="LINK" />
          </el-select>
        </el-form-item>
        <el-form-item label="资源内容">
          <el-input v-model="resourceForm.content" type="textarea" :rows="5" />
        </el-form-item>
        <el-form-item label="外部链接">
          <el-input v-model="resourceForm.externalUrl" maxlength="512" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resourceDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitResource">发布</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="taskDialogVisible" title="发布任务 / 测试" width="620px">
      <el-form label-width="88px">
        <el-form-item label="标题" required>
          <el-input v-model="taskForm.title" maxlength="255" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="taskForm.taskType" style="width: 100%">
            <el-option label="任务" value="TASK" />
            <el-option label="测试" value="TEST" />
          </el-select>
        </el-form-item>
        <el-form-item label="发布状态">
          <el-select v-model="taskForm.status" style="width: 100%">
            <el-option label="直接发布" value="PUBLISHED" />
            <el-option label="保存为草稿" value="DRAFT" />
          </el-select>
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="taskForm.description" type="textarea" :rows="4" />
        </el-form-item>
        <el-form-item label="截止时间">
          <el-date-picker
            v-model="taskForm.dueAt"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="知识点">
          <el-select
            v-model="taskForm.knowledgePointId"
            clearable
            placeholder="可选，用于写回学习进度"
            style="width: 100%"
          >
            <el-option
              v-for="point in taskKnowledgePoints"
              :key="point.id"
              :label="point.name"
              :value="point.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="奖励积分">
          <el-input-number v-model="taskForm.rewardPoints" :min="0" :max="10000" />
        </el-form-item>
        <el-form-item label="满分">
          <el-input-number v-model="taskForm.maxScore" :min="1" :max="100" />
        </el-form-item>
        <el-form-item label="允许迟交">
          <el-switch v-model="taskForm.allowLateSubmit" />
        </el-form-item>
        <el-form-item label="允许重交">
          <el-switch v-model="taskForm.allowResubmit" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="taskDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitTask">发布</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="submitDialogVisible" title="提交作业" width="620px">
      <el-input v-model="submissionContent" type="textarea" :rows="8" placeholder="输入你的答案或说明" />
      <template #footer>
        <el-button @click="submitDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitTaskContent">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="submissionsDialogVisible" title="提交记录" width="760px">
      <el-table :data="submissions" border>
        <el-table-column prop="studentName" label="学生" width="120" />
        <el-table-column prop="content" label="提交内容" min-width="200" show-overflow-tooltip />
        <el-table-column prop="score" label="分数" width="90" />
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column label="操作" width="180">
          <template #default="{ row }">
            <el-button link type="primary" @click="openGrade(asSubmission(row))">批改</el-button>
            <el-button
              link
              type="primary"
              @click="openSubmissionAttachmentRow(asSubmission(row))"
            >
              附件
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog v-model="gradeDialogVisible" title="批改提交" width="520px">
      <el-form label-width="80px">
        <el-form-item label="分数">
          <el-input-number v-model="gradeForm.score" :min="0" :max="100" />
        </el-form-item>
        <el-form-item label="评语">
          <el-input v-model="gradeForm.feedback" type="textarea" :rows="4" maxlength="1000" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="gradeDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitGrade">保存批改</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="attachmentDialogVisible" :title="attachmentTitle" width="760px">
      <div v-loading="attachmentLoading" class="attachment-dialog">
        <section class="attachment-section">
          <header class="attachment-section-head">
            <h4>图片附件</h4>
            <span>{{ imageAttachments.length }} 张</span>
          </header>
          <MediaGallery
            :items="imageAttachments"
            :removable="attachmentUploadable"
            empty-text="还没有上传图片。"
            @download="onImageDownload"
            @remove="onImageRemove"
          />
          <MediaUploader
            v-if="attachmentUploadable"
            :file-ids="[]"
            :purpose="imagePurpose"
            :max="9"
            auto-clear
            label="图片"
            @uploaded="onAttachmentUploaded"
          />
          <p v-if="attachmentUploadable" class="attachment-section-note">
            支持 jpg / png / webp / gif，单张不超过 5 MB，一次最多 9 张。图片附件仅本班教师与学生可见。
          </p>
        </section>

        <section class="attachment-section">
          <header class="attachment-section-head">
            <h4>文档附件</h4>
            <span>{{ documentAttachments.length }} 个</span>
          </header>
          <FileAttachmentPanel
            :attachments="documentAttachments"
            :uploadable="attachmentUploadable"
            :purpose="documentPurpose"
            empty-text="还没有上传文档。"
            :hint="
              attachmentSubmissionId
                ? '作业附件同样受截止时间与重交策略限制'
                : '班级教学文件仅本班教师与学生可见'
            "
            @uploaded="onAttachmentUploaded"
            @remove="onAttachmentRemove"
          />
        </section>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.cloud-class-layout {
  display: grid;
  grid-template-columns: minmax(240px, 0.28fr) minmax(0, 1fr);
  min-height: 700px;
  border-bottom: 1px solid var(--rule);
}

.cloud-class-rail {
  padding: 20px 18px 20px 0;
  border-right: 1px solid var(--rule);
}

.cloud-class-main {
  min-width: 0;
  padding: 0 0 28px 24px;
}

.class-hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 190px;
  gap: 22px;
  align-items: start;
  padding: 22px 0 18px;
  border-bottom: 1px solid var(--rule-strong);
}

.class-hero-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 7px 12px;
  color: var(--ink-soft);
  font-size: 11px;
}

.class-status-pill {
  padding: 2px 7px;
  color: var(--jade);
  background: var(--jade-soft);
  border: 1px solid transparent;
}

.class-status-pill.status-closed {
  color: var(--ochre);
  background: var(--ochre-soft);
}

.class-status-pill.status-archived {
  color: var(--ink-soft);
  background: var(--paper-deep);
}

.class-hero h2 {
  margin: 8px 0 0;
  color: var(--ink);
  font-family: var(--font-serif);
  font-size: 30px;
  line-height: 1.25;
}

.class-hero-copy > p {
  max-width: 650px;
  margin: 8px 0 0;
  color: var(--ink-soft);
  font-size: 12px;
  line-height: 1.75;
}

.class-hero-facts {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
  margin-top: 12px;
  color: var(--ink-soft);
  font-size: 11px;
}

.class-code-panel {
  display: grid;
  justify-items: end;
  gap: 4px;
  padding: 12px 14px;
  background: var(--paper);
  border-left: 2px solid var(--primary);
}

.class-code-label,
.class-code-panel small {
  color: var(--ink-soft);
  font-size: 10px;
}

.class-code-panel strong {
  color: var(--primary-deep);
  font-family: var(--font-numeric);
  font-size: 24px;
  letter-spacing: 0.1em;
}

.class-code-copy {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 0;
  color: var(--primary-deep);
  font-size: 10px;
  background: none;
  border: 0;
  cursor: pointer;
}

.class-metric-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  border-bottom: 1px solid var(--rule);
}

.class-metric {
  display: grid;
  gap: 4px;
  padding: 13px 14px;
  border-right: 1px solid var(--rule);
}

.class-metric:last-child { border-right: 0; }

.class-metric span {
  color: var(--ink-soft);
  font-size: 10px;
}

.class-metric strong {
  color: var(--ink);
  font-family: var(--font-numeric);
  font-size: 23px;
}

.class-metric strong small {
  margin-left: 3px;
  color: var(--ink-soft);
  font-family: var(--font-sans);
  font-size: 10px;
  font-weight: 400;
}

.class-workspace-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 48px;
  border-bottom: 1px solid var(--rule);
}

.class-tab-nav {
  display: flex;
  gap: 4px;
}

.class-tab-nav button {
  padding: 8px 10px;
  color: var(--ink-soft);
  font-size: 12px;
  background: transparent;
  border: 0;
  border-bottom: 2px solid transparent;
  cursor: pointer;
}

.class-tab-nav button:hover,
.class-tab-nav button.active {
  color: var(--primary-deep);
  border-bottom-color: var(--primary);
}

.class-toolbar-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 7px;
}

.class-content-tabs :deep(.el-tabs__header) {
  display: none;
}

.class-content-tabs :deep(.el-tabs__content) {
  overflow: visible;
}

.class-content-head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  padding: 18px 0 12px;
  border-bottom: 1px solid var(--rule);
}

.class-content-head h3 {
  margin: 0;
  color: var(--ink);
  font-family: var(--font-serif);
  font-size: 18px;
}

.class-content-head p {
  margin: 5px 0 0;
  color: var(--ink-soft);
  font-size: 11px;
}

.class-chip-filter {
  display: flex;
  gap: 4px;
}

.class-chip-filter button {
  padding: 3px 8px;
  color: var(--ink-soft);
  font-size: 10px;
  background: transparent;
  border: 1px solid var(--rule);
  cursor: pointer;
}

.class-chip-filter button:hover,
.class-chip-filter button.active {
  color: var(--primary-deep);
  background: var(--primary-wash);
  border-color: var(--primary-soft);
}

.resource-row,
.task-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 16px;
  padding: 14px 0;
  border-bottom: 1px solid var(--rule);
}

.resource-row {
  grid-template-columns: 48px minmax(0, 1fr) auto;
  align-items: start;
}

.resource-type {
  padding-top: 2px;
  color: var(--primary-deep);
  font-size: 10px;
}

.resource-copy,
.task-row > div:first-child {
  display: grid;
  min-width: 0;
  gap: 4px;
}

.resource-copy strong,
.task-row-head strong {
  color: var(--ink);
  font-size: 13px;
}

.resource-copy span,
.task-meta {
  color: var(--ink-soft);
  font-size: 10px;
  line-height: 1.6;
}

.resource-copy p,
.task-description {
  margin: 3px 0 0;
  color: var(--ink-soft);
  font-size: 11px;
  line-height: 1.7;
  white-space: pre-wrap;
}

.task-row-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.task-row-head .task-title {
  flex: 1 1 220px;
}

.task-status-badge {
  padding: 2px 6px;
  font-size: 10px;
  border: 1px solid var(--rule);
}

.task-status-badge.is-open {
  color: var(--jade);
  background: var(--jade-soft);
  border-color: transparent;
}

.task-status-badge.is-draft {
  color: var(--ink-soft);
  background: var(--paper-deep);
  border-color: transparent;
}

.task-status-badge.is-closed {
  color: var(--ochre);
  background: var(--ochre-soft);
  border-color: transparent;
}

.task-due {
  color: var(--ink-soft);
  font-size: 10px;
}

.task-due.due-soon { color: var(--ochre); }
.task-due.due-overdue { color: var(--cinnabar); }

.class-empty {
  display: grid;
  gap: 5px;
  padding: 28px 0;
  color: var(--ink-soft);
  font-size: 12px;
  line-height: 1.7;
}

.class-empty strong {
  color: var(--ink);
  font-size: 14px;
}

.cloud-class-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  padding: 22px 0 18px;
  border-bottom: 1px solid var(--rule-strong);
}

.cloud-class-header h2 {
  margin: 0;
  font-family: var(--font-display);
  font-size: 27px;
}

.cloud-class-header p {
  margin: 8px 0 0;
  color: var(--ink-soft);
}

.class-code {
  display: grid;
  gap: 4px;
  padding: 12px 16px;
  text-align: right;
  background: var(--paper-deep);
  border: 1px solid var(--rule);
}

.class-code span {
  color: var(--ink-soft);
  font-size: 12px;
}

.class-code strong {
  font-family: var(--font-display);
  font-size: 24px;
  letter-spacing: 0.12em;
}

.cloud-class-actions {
  display: flex;
  gap: 10px;
  padding: 16px 0 4px;
}

.cloud-resource,
.cloud-task {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 16px;
  padding: 16px 0;
  border-bottom: 1px solid var(--rule);
}

.cloud-resource-content {
  margin-top: 8px;
  color: var(--ink-soft);
  line-height: 1.8;
  white-space: pre-wrap;
}

.attachment-dialog {
  display: grid;
  gap: 20px;
}

.attachment-section {
  display: grid;
  gap: 12px;
}

.attachment-section-head {
  display: flex;
  gap: 10px;
  align-items: baseline;
  justify-content: space-between;
  padding-bottom: 6px;
  border-bottom: 1px solid var(--rule);
}

.attachment-section-head h4 {
  margin: 0;
  color: var(--ink);
  font-family: var(--font-display);
  font-size: 15px;
}

.attachment-section-head span {
  color: var(--ink-soft);
  font-size: 12px;
}

.attachment-section-note {
  margin: 0;
  color: var(--ink-soft);
  font-size: 12px;
  line-height: 1.7;
}

@media (max-width: 900px) {
  .cloud-class-layout {
    grid-template-columns: 1fr;
  }

  .cloud-class-rail {
    padding-right: 0;
    border-right: none;
    border-bottom: 1px solid var(--rule);
  }

  .cloud-class-main {
    padding-left: 0;
  }

  .class-hero {
    grid-template-columns: 1fr;
  }

  .class-code-panel {
    justify-items: start;
    border-top: 1px solid var(--rule);
    border-left: 0;
  }

  .class-metric-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .class-metric:nth-child(2n) { border-right: 0; }
}
</style>
