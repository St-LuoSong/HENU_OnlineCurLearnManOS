<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Aim,
  ChatDotRound,
  Collection,
  EditPen,
  Medal,
  School,
  TrendCharts,
  User
} from '@element-plus/icons-vue'
import { computed, onMounted, ref, type Component } from 'vue'
import { useRouter } from 'vue-router'

import {
  createDashboardNote,
  deleteDashboardNote,
  getDashboardOverview,
  listCalendarEvents,
  listDashboardNotes,
  updateDashboardNote,
  type CalendarEvent,
  type DashboardNote,
  type DashboardNoteTone,
  type DashboardOverview
} from '@/api/dashboard'
import {
  checkinToday,
  getCheckinStatus,
  type CheckinStatus
} from '@/api/checkin'
import {
  getCloudClassDetail,
  listTeachingClasses,
  type CloudClassDetail
} from '@/api/cloudClass'
import { listGoals, listTasks, type Goal, type Task } from '@/api/learning'
import MiniCalendar from '@/components/dashboard/MiniCalendar.vue'
import CheckinSeal from '@/components/dashboard/CheckinSeal.vue'
import InteractionPulse from '@/components/dashboard/InteractionPulse.vue'
import { useAuthStore } from '@/stores/auth'
import {
  formatChineseDate,
  formatDate,
  formatDateTime,
  formatWeekday,
  monthRange,
  toDateKey,
  toLocalDateKey
} from '@/utils/date'

const NOTE_TONES: Array<{ value: DashboardNoteTone; label: string }> = [
  { value: 'DEFAULT', label: '普通' },
  { value: 'IMPORTANT', label: '重要' },
  { value: 'RELAX', label: '放松' }
]

const EVENT_TYPE_LABELS: Record<string, string> = {
  GOAL: '目标',
  TASK: '任务',
  PLAN_START: '计划开始',
  PLAN_END: '计划结束',
  CAREER_ACTION: '生涯行动',
  CLASS_TASK: '班级任务'
}

interface StatCard {
  label: string
  value: string | number
  unit?: string
  hint: string
  tone: 'blue' | 'green' | 'amber' | 'coral'
  icon: Component
}

const router = useRouter()
const auth = useAuthStore()
const loading = ref(true)
const overview = ref<DashboardOverview | null>(null)
const tasks = ref<Task[]>([])
const goals = ref<Goal[]>([])
const calendarEvents = ref<CalendarEvent[]>([])
const notes = ref<DashboardNote[]>([])
const classDetails = ref<CloudClassDetail[]>([])
const checkin = ref<CheckinStatus>({
  checkedToday: false,
  currentStreak: 0,
  todayReward: 10,
  nextReward: 10,
  totalCheckins: 0,
  recentCheckins: []
})
const checking = ref(false)

const todayKey = toLocalDateKey(new Date())
const anchor = new Date()
const calendarDate = ref(todayKey)
const calendarRange = ref(monthRange(anchor.getFullYear(), anchor.getMonth() + 1))

const noteEditing = ref(false)
const noteSaving = ref(false)
const noteDraft = ref<{ id: number | null; content: string; tone: DashboardNoteTone }>({
  id: null,
  content: '',
  tone: 'DEFAULT'
})

const isTeacher = computed(() => overview.value?.role === 'TEACHER')
const studentSummary = computed(() => overview.value?.student?.summary)
const teacherSummary = computed(() => overview.value?.teacher)

const todayLabel = formatChineseDate(new Date())
const todayWeekday = formatWeekday(new Date())
const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) {
    return '夜深了'
  }
  if (hour < 11) {
    return '早上好'
  }
  if (hour < 14) {
    return '中午好'
  }
  if (hour < 18) {
    return '下午好'
  }
  return '晚上好'
})
const displayName = computed(
  () => auth.user?.displayName ?? (isTeacher.value ? '老师' : '同学')
)
const interaction = computed(() => overview.value?.student?.interaction)
const studentStats = computed<StatCard[]>(() => [
  {
    label: '本周互动',
    value: interaction.value?.totalThisWeek ?? 0,
    unit: '次',
    hint: `今天 ${interaction.value?.todayCount ?? 0} 次`,
    tone: 'blue',
    icon: ChatDotRound
  },
  {
    label: '活跃天数',
    value: interaction.value?.activeDaysThisWeek ?? 0,
    unit: '天',
    hint: '最近 7 天',
    tone: 'green',
    icon: Collection
  },
  {
    label: '练习正确率',
    value: interaction.value?.practiceAccuracy ?? 0,
    unit: '%',
    hint: '近 90 天已交卷套题',
    tone: 'amber',
    icon: Aim
  },
  {
    label: '已掌握知识点',
    value: studentSummary.value?.masteredPoints ?? 0,
    unit: '个',
    hint: '达到掌握标准',
    tone: 'green',
    icon: Medal
  }
])
const teacherStats = computed<StatCard[]>(() => [
  {
    label: '负责班级',
    value: teacherSummary.value?.classCount ?? 0,
    unit: '个',
    hint: '全部云班级',
    tone: 'blue',
    icon: School
  },
  {
    label: '学生总数',
    value: teacherSummary.value?.studentCount ?? 0,
    unit: '人',
    hint: '去重后的学生',
    tone: 'green',
    icon: User
  },
  {
    label: '待批改提交',
    value: teacherSummary.value?.pendingSubmissionCount ?? 0,
    unit: '份',
    hint: `已批改 ${teacherSummary.value?.gradedSubmissionCount ?? 0} 份`,
    tone: 'coral',
    icon: EditPen
  },
  {
    label: '班级平均分',
    value: teacherSummary.value?.averageScore ?? 0,
    unit: '分',
    hint: '已批改成绩均值',
    tone: 'amber',
    icon: TrendCharts
  }
])
const activeStats = computed(() => (isTeacher.value ? teacherStats.value : studentStats.value))
const heroTitle = computed(() =>
  isTeacher.value ? '把每一次教学，落在学生的进步上' : '学习，是一场向上的旅程'
)
const heroSubtitle = computed(() =>
  isTeacher.value
    ? '从班级状态、待批改提交到风险学生，把今天的教学重点放在一张桌上。'
    : '课程、任务、目标与班级进度都在这里，今天也从一件小事开始。'
)
const primaryActionLabel = computed(() => (isTeacher.value ? '管理云班级' : '继续学习'))

function goPrimaryAction(): void {
  router.push(isTeacher.value ? '/cloud-classes' : '/assistant')
}

const openTasks = computed(() =>
  tasks.value.filter((task) => task.status !== 'COMPLETED' && task.status !== 'CANCELLED')
)
const todayTasks = computed(() =>
  openTasks.value
    .filter((task) => task.dueAt && toDateKey(task.dueAt) <= todayKey)
    .sort((left, right) => String(left.dueAt).localeCompare(String(right.dueAt)))
    .slice(0, 8)
)

const myCourses = computed(() => overview.value?.student?.myCourses ?? [])
const recentScores = computed(() => overview.value?.student?.recentScores ?? [])
const classNames = computed(() => overview.value?.student?.classNames ?? [])
const teacherNames = computed(() => overview.value?.student?.teacherNames ?? [])

const goalProgress = computed(() =>
  goals.value
    .filter((goal) => goal.status === 'ACTIVE')
    .sort((left, right) => {
      const leftDate = left.targetDate ?? '9999-12-31'
      const rightDate = right.targetDate ?? '9999-12-31'
      return leftDate.localeCompare(rightDate) || left.progress - right.progress
    })
    .slice(0, 5)
)

const riskStudents = computed(() => overview.value?.teacher?.riskStudents ?? [])

const classPendingBars = computed(() => {
  const rows = (teacherSummary.value?.classes ?? [])
    .map((item) => ({ label: item.className, value: item.pendingSubmissionCount }))
    .sort((left, right) => right.value - left.value)
    .slice(0, 6)
  const max = Math.max(1, ...rows.map((row) => row.value))
  return rows.map((row) => ({ ...row, percent: Math.round((row.value / max) * 100) }))
})
const classScoreBars = computed(() =>
  (teacherSummary.value?.classes ?? [])
    .map((item) => ({ label: item.className, value: item.averageScore }))
    .sort((left, right) => right.value - left.value)
    .slice(0, 6)
    .map((row) => ({ ...row, percent: Math.max(2, Math.min(100, Math.round(row.value))) }))
)

const teacherUpcomingTasks = computed(() =>
  calendarEvents.value
    .filter((event) => event.type === 'CLASS_TASK' && event.date >= todayKey)
    .slice(0, 8)
)
const recentResources = computed(() =>
  classDetails.value
    .flatMap((detail) =>
      detail.resources.map((resource) => ({ ...resource, className: detail.classInfo.name }))
    )
    .sort((left, right) => String(right.createdAt).localeCompare(String(left.createdAt)))
    .slice(0, 6)
)

const studentAdvice = computed(() => {
  const advice: string[] = []
  const summary = studentSummary.value
  if ((summary?.pendingTasks ?? 0) > 0) {
    advice.push('还有 ' + summary?.pendingTasks + ' 个待完成任务，先锁定今天最关键的 1-2 个。')
  }
  if (openTasks.value.some((task) => task.dueAt && toDateKey(task.dueAt) < todayKey)) {
    advice.push('有任务已经逾期，先重排截止时间再开始新的学习内容。')
  }
  if (myCourses.value.some((item) => item.totalPoints > 0 && item.masteryPercent < 40)) {
    advice.push('有课程掌握度偏低，回到对应知识点做一次分层练习更划算。')
  }
  if ((interaction.value?.totalThisWeek ?? 0) === 0) {
    advice.push('本周还没有学习互动，先完成一次问答或练习，让学习轨迹重新亮起来。')
  }
  if (advice.length === 0) {
    advice.push('当前节奏稳定，保持今天的最小行动即可。')
  }
  return advice
})

const teacherAdvice = computed(() => {
  const advice: string[] = []
  if ((teacherSummary.value?.pendingSubmissionCount ?? 0) > 0) {
    advice.push('有 ' + teacherSummary.value?.pendingSubmissionCount + ' 份提交待批改，先处理临近截止的班级。')
  }
  if (riskStudents.value.length > 0) {
    advice.push('发现 ' + riskStudents.value.length + ' 名逾期或低分学生，建议安排一次针对性跟进。')
  }
  if (advice.length === 0) {
    advice.push('当前教学节奏正常，可以继续发布下一阶段任务。')
  }
  return advice
})

const dayEvents = computed(() =>
  calendarEvents.value.filter((event) => event.date === calendarDate.value)
)
const dayNotes = computed(() => notes.value.filter((note) => note.noteDate === calendarDate.value))

onMounted(async () => {
  await load()
})

async function load(): Promise<void> {
  loading.value = true
  try {
    overview.value = await getDashboardOverview()
    if (overview.value.role === 'TEACHER') {
      await loadTeacher()
    } else {
      await Promise.all([loadStudent(), loadCheckin()])
    }
    await Promise.all([loadCalendar(), loadNotes()])
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '学习概览加载失败')
  } finally {
    loading.value = false
  }
}

async function loadCheckin(): Promise<void> {
  try {
    checkin.value = await getCheckinStatus()
  } catch {
    // 签到展示失败不阻塞首页其他数据。
  }
}

async function handleCheckin(): Promise<void> {
  if (checkin.value.checkedToday) {
    return
  }
  checking.value = true
  try {
    const result = await checkinToday()
    checkin.value = result.status
    ElMessage.success(`签到成功，获得 ${result.earnedPoints} 积分`)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '签到失败')
  } finally {
    checking.value = false
  }
}

async function loadStudent(): Promise<void> {
  const [taskList, goalList] = await Promise.all([listTasks({}), listGoals()])
  tasks.value = taskList
  goals.value = goalList
}

async function loadTeacher(): Promise<void> {
  const classes = await listTeachingClasses()
  const settled = await Promise.allSettled(
    classes.map((cloudClass) => getCloudClassDetail(cloudClass.id))
  )
  classDetails.value = settled
    .filter((item): item is PromiseFulfilledResult<CloudClassDetail> => item.status === 'fulfilled')
    .map((item) => item.value)
}

async function loadCalendar(): Promise<void> {
  try {
    calendarEvents.value = await listCalendarEvents({
      from: calendarRange.value.from,
      to: calendarRange.value.to
    })
  } catch (error) {
    calendarEvents.value = []
    ElMessage.error(error instanceof Error ? error.message : '学习日历加载失败')
  }
}

async function loadNotes(): Promise<void> {
  try {
    notes.value = await listDashboardNotes({
      from: calendarRange.value.from,
      to: calendarRange.value.to
    })
  } catch (error) {
    notes.value = []
    ElMessage.error(error instanceof Error ? error.message : '便签加载失败')
  }
}

/** 小日历切换月份时，便签与学习事件都按新月份区间重取。 */
async function onMonthChange(range: { from: string; to: string }): Promise<void> {
  calendarRange.value = range
  noteEditing.value = false
  await Promise.all([loadNotes(), loadCalendar()])
}

function startCreateNote(): void {
  noteDraft.value = { id: null, content: '', tone: 'DEFAULT' }
  noteEditing.value = true
}

function startEditNote(note: DashboardNote): void {
  noteDraft.value = { id: note.id, content: note.content, tone: note.tone }
  noteEditing.value = true
}

function cancelNote(): void {
  noteEditing.value = false
  noteDraft.value = { id: null, content: '', tone: 'DEFAULT' }
}

function upsertNote(saved: DashboardNote): void {
  const index = notes.value.findIndex((item) => item.id === saved.id)
  if (index >= 0) {
    notes.value.splice(index, 1, saved)
  } else {
    notes.value = [...notes.value, saved]
  }
}

async function submitNote(): Promise<void> {
  const content = noteDraft.value.content.trim()
  if (!content) {
    ElMessage.warning('便签内容不能为空')
    return
  }
  noteSaving.value = true
  try {
    const payload = { noteDate: calendarDate.value, content, tone: noteDraft.value.tone }
    const saved =
      noteDraft.value.id === null
        ? await createDashboardNote(payload)
        : await updateDashboardNote(noteDraft.value.id, payload)
    upsertNote(saved)
    cancelNote()
    ElMessage.success('便签已保存')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '便签保存失败')
  } finally {
    noteSaving.value = false
  }
}

async function removeNote(note: DashboardNote): Promise<void> {
  try {
    await ElMessageBox.confirm('删除后无法恢复，确认删除这条便签吗？', '删除便签', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await deleteDashboardNote(note.id)
    notes.value = notes.value.filter((item) => item.id !== note.id)
    if (noteDraft.value.id === note.id) {
      cancelNote()
    }
    ElMessage.success('便签已删除')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '便签删除失败')
  }
}

function eventTypeLabel(type: string): string {
  return EVENT_TYPE_LABELS[type] ?? type
}

function taskStatusLabel(status: string): string {
  const labels: Record<string, string> = {
    TODO: '待开始',
    IN_PROGRESS: '进行中',
    COMPLETED: '已完成',
    CANCELLED: '已取消'
  }
  return labels[status] ?? status
}

function scoreSourceLabel(sourceType: string): string {
  const labels: Record<string, string> = {
    PRACTICE_SET: '练习套题',
    LEARNING_RECORD: '学习记录',
    CLASS_TASK: '班级任务'
  }
  return labels[sourceType] ?? sourceType
}

function priorityLabel(priority: string): string {
  const labels: Record<string, string> = {
    HIGH: '高优先级',
    MEDIUM: '中优先级',
    LOW: '低优先级'
  }
  return labels[priority] ?? priority
}

function goCourse(course: { pendingTaskCount: number; totalPoints: number; nextAction: string }): void {
  if (course.pendingTaskCount > 0) {
    router.push('/cloud-classes')
  } else if (course.totalPoints > 0) {
    router.push('/practice')
  } else {
    router.push('/resources')
  }
}
</script>
<template>
  <div v-loading="loading" class="ledger-page dashboard-page">
    <section class="dash-hero">
      <div class="dash-hero-copy">
        <p class="dash-hero-date">{{ todayLabel }} · {{ todayWeekday }}</p>
        <h1>{{ greeting }}，{{ displayName }}</h1>
        <p class="dash-hero-tagline">{{ heroTitle }}</p>
        <p class="dash-hero-sub">{{ heroSubtitle }}</p>
        <div class="dash-hero-actions">
          <el-button type="primary" @click="goPrimaryAction">{{ primaryActionLabel }}</el-button>
          <el-button @click="router.push('/growth')">成长规划</el-button>
        </div>
      </div>
      <CheckinSeal
        v-if="!isTeacher"
        :status="checkin"
        :checking="checking"
        @checkin="handleCheckin"
      />
      <aside v-else class="teacher-hero-panel">
        <span class="teacher-hero-label">今日教学节奏</span>
        <strong>{{ teacherSummary?.pendingSubmissionCount ?? 0 }}</strong>
        <span class="teacher-hero-unit">份待批改提交</span>
        <div class="teacher-hero-split">
          <span>{{ teacherSummary?.classCount ?? 0 }} 个班级</span>
          <span>{{ riskStudents.length }} 名需关注学生</span>
        </div>
        <el-button type="primary" plain @click="router.push('/cloud-classes')">
          进入云班级
        </el-button>
      </aside>
    </section>

    <section class="stat-grid">
      <article
        v-for="stat in activeStats"
        :key="stat.label"
        class="stat-card"
        :class="'tone-' + stat.tone"
      >
        <span class="stat-icon">
          <el-icon><component :is="stat.icon" /></el-icon>
        </span>
        <div class="stat-body">
          <span class="stat-label">{{ stat.label }}</span>
          <strong class="stat-value">
            {{ stat.value }}<small v-if="stat.unit">{{ stat.unit }}</small>
          </strong>
          <span class="stat-hint">{{ stat.hint }}</span>
        </div>
      </article>
    </section>

    <div class="dashboard-shell">
      <div class="dashboard-main">
        <template v-if="isTeacher">
          <section class="dash-panel panel-wide">
            <header class="panel-head">
              <h2>班级概览</h2>
              <span>按班级聚合</span>
            </header>
            <div v-if="(teacherSummary?.classes ?? []).length === 0" class="dash-empty">
              还没有负责的云班级，先去创建一个班级并生成班级码。
            </div>
            <ul v-else class="class-list">
              <li v-for="item in teacherSummary?.classes ?? []" :key="item.classId">
                <div class="class-head">
                  <strong>{{ item.className }}</strong>
                  <span class="class-subject">{{ item.subjectName || '未设置课程' }}</span>
                </div>
                <div class="bar-track">
                  <div
                    class="bar-fill is-jade"
                    :style="{
                      width:
                        item.gradedSubmissionCount + item.pendingSubmissionCount === 0
                          ? '0%'
                          : Math.round(
                              (item.gradedSubmissionCount /
                                (item.gradedSubmissionCount + item.pendingSubmissionCount)) *
                                100
                            ) + '%'
                    }"
                  />
                </div>
                <div class="class-stats">
                  <span>{{ item.memberCount }} 名学生</span>
                  <span>{{ item.taskCount }} 个任务</span>
                  <span>{{ item.pendingSubmissionCount }} 待批</span>
                  <span>{{ item.averageScore }} 平均分</span>
                </div>
              </li>
            </ul>
          </section>

          <section class="dash-panel">
            <header class="panel-head">
              <h2>待批改分布</h2>
              <span>按班级排序</span>
            </header>
            <div v-if="classPendingBars.length === 0" class="dash-empty">暂无待批改提交。</div>
            <div v-for="row in classPendingBars" :key="'pending-' + row.label" class="bar-row">
              <span class="bar-label">{{ row.label }}</span>
              <div class="bar-track">
                <div class="bar-fill is-cinnabar" :style="{ width: row.percent + '%' }" />
              </div>
              <span class="bar-value">{{ row.value }}</span>
            </div>
          </section>

          <section class="dash-panel">
            <header class="panel-head">
              <h2>班级平均分</h2>
              <span>满分 100</span>
            </header>
            <div v-if="classScoreBars.length === 0" class="dash-empty">暂无已批改成绩。</div>
            <div v-for="row in classScoreBars" :key="'score-' + row.label" class="bar-row">
              <span class="bar-label">{{ row.label }}</span>
              <div class="bar-track">
                <div class="bar-fill is-ochre" :style="{ width: row.percent + '%' }" />
              </div>
              <span class="bar-value">{{ row.value }}</span>
            </div>
          </section>

          <section class="dash-panel panel-wide">
            <header class="panel-head">
              <h2>即将截止的班级任务</h2>
              <span>来自学习日历</span>
            </header>
            <div v-if="teacherUpcomingTasks.length === 0" class="dash-empty">本月没有待截止的班级任务。</div>
            <ul v-else class="action-list">
              <li v-for="event in teacherUpcomingTasks" :key="'task-' + event.sourceId">
                <span class="action-date">{{ formatDate(event.date) }}</span>
                <div class="action-body">
                  <span class="action-title">{{ event.title }}</span>
                  <span class="action-meta">{{ event.detail || '云班级任务' }} · {{ event.status }}</span>
                </div>
              </li>
            </ul>
          </section>

          <section class="dash-panel panel-wide">
            <header class="panel-head">
              <h2>最近发布资源</h2>
              <span>班级教学文件</span>
            </header>
            <div v-if="recentResources.length === 0" class="dash-empty">最近还没有发布教学资源。</div>
            <ul v-else class="action-list">
              <li v-for="resource in recentResources" :key="resource.className + '-' + resource.id">
                <span class="action-date">{{ resource.resourceType }}</span>
                <div class="action-body">
                  <span class="action-title">{{ resource.title }}</span>
                  <span class="action-meta">
                    {{ resource.className }} · {{ formatDateTime(resource.createdAt) }}
                  </span>
                </div>
              </li>
            </ul>
          </section>

          <section class="dash-panel panel-wide advice-panel">
            <header class="panel-head">
              <h2>教学建议</h2>
              <span>根据当前班级状态生成</span>
            </header>
            <ul class="advice-list">
              <li v-for="item in teacherAdvice" :key="item">{{ item }}</li>
            </ul>
          </section>
        </template>

        <template v-else>
          <section class="dash-panel panel-wide">
            <header class="panel-head">
              <h2>我的课程</h2>
              <span>{{ myCourses.length }} 门 · 按最近活动排序</span>
            </header>
            <div v-if="myCourses.length === 0" class="dash-empty">
              还没有课程活动。去资源库挑一门课，或加入一个云班级开始学习。
            </div>
            <div v-else class="course-ledger">
              <article
                v-for="(course, index) in myCourses"
                :key="course.subjectId"
                class="course-row"
              >
                <div class="course-art" :class="'course-art--' + (index % 4)">
                  {{ course.subjectName.slice(0, 1) }}
                </div>
                <div class="course-main">
                  <div class="course-heading">
                    <strong>{{ course.subjectName }}</strong>
                    <span>{{ course.className || '自主学习' }}</span>
                  </div>
                  <div class="course-meta">
                    <span>掌握 {{ course.masteryPercent }}%</span>
                    <span>{{ course.masteredPoints }}/{{ course.totalPoints }} 个知识点</span>
                    <span v-if="course.teacherName">{{ course.teacherName }}</span>
                    <span v-if="course.averageScore != null">平均分 {{ course.averageScore }}</span>
                  </div>
                  <div class="course-track">
                    <div class="course-fill" :style="{ width: course.masteryPercent + '%' }" />
                  </div>
                </div>
                <div class="course-side">
                  <span class="course-action-label">{{ course.nextAction }}</span>
                  <el-button link type="primary" @click="goCourse(course)">
                    进入课程
                  </el-button>
                </div>
              </article>
            </div>
          </section>

          <section class="dash-panel panel-wide">
            <header class="panel-head">
              <h2>学习互动脉冲</h2>
              <span>最近 7 天 · 本周 {{ interaction?.totalThisWeek ?? 0 }} 次</span>
            </header>
            <div class="interaction-summary">
              <span><b>{{ interaction?.breakdown.aiQuestions ?? 0 }}</b> AI 提问</span>
              <span><b>{{ interaction?.breakdown.practiceAnswers ?? 0 }}</b> 练习作答</span>
              <span><b>{{ interaction?.breakdown.resourceViews ?? 0 }}</b> 资源打开</span>
              <span><b>{{ interaction?.breakdown.forumActivities ?? 0 }}</b> 论坛互动</span>
              <span><b>{{ interaction?.breakdown.classSubmissions ?? 0 }}</b> 班级提交</span>
            </div>
            <InteractionPulse :trend="interaction?.trend ?? []" />
          </section>

          <section class="dash-panel">
            <header class="panel-head">
              <h2>最近成绩</h2>
              <span>练习 · 记录 · 班级任务</span>
            </header>
            <div v-if="recentScores.length === 0" class="dash-empty">
              还没有成绩记录。完成一次练习或云班级任务后会出现在这里。
            </div>
            <ul v-else class="score-list">
              <li v-for="item in recentScores" :key="item.sourceType + '-' + item.sourceId">
                <strong :class="{ 'is-low': (item.score ?? 0) < 60 }">{{ item.score ?? '-' }}</strong>
                <div class="action-body">
                  <span class="action-title">{{ item.title }}</span>
                  <span class="action-meta">
                    {{ item.subjectName || '通用' }} · {{ scoreSourceLabel(item.sourceType) }}
                    · {{ formatDateTime(item.recordedAt) }}
                  </span>
                </div>
                <span class="score-max">/ {{ item.maxScore ?? 100 }}</span>
              </li>
            </ul>
          </section>

          <section class="dash-panel">
            <header class="panel-head">
              <h2>目标进度</h2>
              <span>优先推进最接近截止的目标</span>
            </header>
            <div v-if="goalProgress.length === 0" class="dash-empty">暂无进行中的目标。</div>
            <div v-for="goal in goalProgress" :key="goal.id" class="goal-row">
              <div class="goal-head">
                <strong>{{ goal.title }}</strong>
                <span>{{ goal.completedTaskCount }}/{{ goal.taskCount }} 任务</span>
              </div>
              <div class="bar-track">
                <div class="bar-fill is-ochre" :style="{ width: goal.progress + '%' }" />
              </div>
              <div class="goal-meta">
                <span>{{ goal.targetDate ? `截止 ${formatDate(goal.targetDate)}` : '未设置截止日期' }}</span>
                <span>{{ priorityLabel(goal.priority) }} · {{ goal.progress }}%</span>
              </div>
            </div>
          </section>
        </template>
      </div>

      <aside class="dashboard-rail">
        <MiniCalendar
          v-model="calendarDate"
          :events="calendarEvents"
          :notes="notes"
          :checked-dates="checkin.recentCheckins"
          @month-change="onMonthChange"
        />

        <section v-if="!isTeacher" class="dash-panel rail-panel">
          <header class="panel-head">
            <h2>今日学习任务</h2>
            <button type="button" class="panel-link" @click="router.push('/growth')">
              全部任务
            </button>
          </header>
          <div v-if="todayTasks.length === 0" class="dash-empty">
            今天没有到期任务，可以从目标清单里挑一件最想推进的事。
          </div>
          <ul v-else class="today-list">
            <li
              v-for="task in todayTasks"
              :key="task.id"
              :class="{ 'is-overdue': task.dueAt && toDateKey(task.dueAt) < todayKey }"
            >
              <span class="today-check" aria-hidden="true" />
              <div class="action-body">
                <span class="action-title">{{ task.title }}</span>
                <span class="action-meta">
                  {{ taskStatusLabel(task.status) }} · {{ formatDate(task.dueAt) }}
                </span>
              </div>
            </li>
          </ul>
        </section>

        <section class="dash-panel rail-panel">
          <header class="panel-head">
            <h2>便签</h2>
            <button v-if="!noteEditing" type="button" class="panel-link" @click="startCreateNote">
              + 新增
            </button>
          </header>
          <p class="rail-sub">{{ calendarDate }} · {{ dayNotes.length }} 条</p>

          <ul v-if="dayEvents.length" class="note-events">
            <li v-for="event in dayEvents" :key="eventTypeLabel(event.type) + event.title">
              <em>{{ eventTypeLabel(event.type) }}</em>
              <span>{{ event.title }}</span>
            </li>
          </ul>

          <div v-if="dayNotes.length === 0 && !noteEditing" class="dash-empty">
            当天还没有便签，点击「新增」记录一条。
          </div>
          <ul v-else class="note-list">
            <li v-for="note in dayNotes" :key="note.id" :class="'tone-' + note.tone.toLowerCase()">
              <p>{{ note.content }}</p>
              <div class="note-actions">
                <button type="button" @click="startEditNote(note)">编辑</button>
                <button type="button" @click="removeNote(note)">删除</button>
              </div>
            </li>
          </ul>

          <div v-if="noteEditing" class="note-editor">
            <el-input
              v-model="noteDraft.content"
              type="textarea"
              :rows="3"
              maxlength="500"
              show-word-limit
              placeholder="例如：20:00 复习线性代数第三章"
            />
            <el-radio-group v-model="noteDraft.tone" size="small">
              <el-radio-button v-for="tone in NOTE_TONES" :key="tone.value" :value="tone.value">
                {{ tone.label }}
              </el-radio-button>
            </el-radio-group>
            <div class="note-editor-actions">
              <el-button size="small" @click="cancelNote">取消</el-button>
              <el-button size="small" type="primary" :loading="noteSaving" @click="submitNote">
                保存
              </el-button>
            </div>
          </div>
        </section>

        <template v-if="isTeacher">
          <section class="dash-panel rail-panel">
            <header class="panel-head">
              <h2>风险学生</h2>
              <span>逾期或低分</span>
            </header>
            <div v-if="riskStudents.length === 0" class="dash-empty">当前没有明显风险学生。</div>
            <ul v-else class="risk-list">
              <li v-for="student in riskStudents" :key="student.classId + '-' + student.studentId">
                <div class="action-body">
                  <span class="action-title">{{ student.displayName }}</span>
                  <span class="action-meta">
                    {{ student.className }} · 逾期 {{ student.overdueTaskCount }} · 待交
                    {{ student.pendingTaskCount }}
                  </span>
                </div>
                <strong :class="{ 'is-low': student.averageScore < 60 }">
                  {{ student.averageScore }}
                </strong>
              </li>
            </ul>
          </section>

          <section class="dash-panel rail-panel">
            <header class="panel-head">
              <h2>快捷操作</h2>
            </header>
            <div class="quick-actions">
              <el-button @click="router.push('/cloud-classes')">管理云班级</el-button>
              <el-button @click="router.push('/teacher/students')">查看学生状态</el-button>
              <el-button @click="router.push('/forum')">进入学习论坛</el-button>
            </div>
          </section>
        </template>

        <template v-else>
          <section class="dash-panel rail-panel">
            <header class="panel-head">
              <h2>我的云班级</h2>
              <span>指导教师</span>
            </header>
            <div v-if="classNames.length === 0" class="dash-empty">还没有加入云班级。</div>
            <div v-else class="tag-cloud">
              <el-tag v-for="name in classNames" :key="name" size="small">{{ name }}</el-tag>
            </div>
            <p class="rail-sub">指导教师：{{ teacherNames.join('、') || '未设置' }}</p>
          </section>

          <section class="dash-panel rail-panel advice-panel">
            <header class="panel-head">
              <h2>下一步建议</h2>
              <span>基于当前数据</span>
            </header>
            <ul class="advice-list">
              <li v-for="item in studentAdvice" :key="item">{{ item }}</li>
            </ul>
          </section>
        </template>
      </aside>
    </div>
  </div>
</template>
<style scoped>

/* 2026 视觉刷新：横幅 + 指标卡 + 主区 / 右栏 */
.dashboard-page {
  display: grid;
  gap: 20px;
}

.dash-hero {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(300px, 0.45fr);
  min-height: 230px;
  padding: 28px 30px;
  overflow: hidden;
  background: linear-gradient(120deg, #e9f3ff 0%, #f7fbff 52%, #e7f2ff 100%);
  border: 1px solid #dbe8fa;
  border-radius: var(--radius-xl);
}

.dash-hero-copy {
  position: relative;
  z-index: 1;
  align-self: center;
  max-width: 640px;
}

.dash-hero-date {
  margin: 0;
  color: var(--primary-deep);
  font-size: 12px;
  font-weight: 700;
}

.dash-hero-copy h1 {
  margin: 8px 0 0;
  color: var(--ink);
  font-family: var(--font-serif);
  font-size: clamp(24px, 2.4vw, 32px);
  font-weight: 800;
  line-height: 1.25;
}

.dash-hero-tagline {
  margin: 10px 0 0;
  color: var(--ink);
  font-size: 15px;
  font-weight: 700;
}

.dash-hero-sub {
  max-width: 560px;
  margin: 8px 0 0;
  color: var(--ink-soft);
  font-size: 13px;
  line-height: 1.8;
}

.dash-hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 18px;
}

.dash-hero-art {
  position: absolute;
  right: 0;
  bottom: 0;
  width: 52%;
  height: 100%;
  pointer-events: none;
  opacity: 0.95;
}

.teacher-hero-panel {
  position: relative;
  z-index: 1;
  display: grid;
  align-content: center;
  gap: 8px;
  padding: 24px;
  background: rgba(255, 255, 255, 0.58);
  border-left: 1px solid var(--rule);
}

.teacher-hero-label {
  color: var(--primary-deep);
  font-size: 12px;
  font-weight: 700;
}

.teacher-hero-panel strong {
  color: var(--primary-deep);
  font-family: var(--font-numeric);
  font-size: 46px;
  line-height: 1;
}

.teacher-hero-unit {
  color: var(--ink-soft);
  font-size: 13px;
}

.teacher-hero-split {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  padding: 10px 0;
  color: var(--ink-soft);
  font-size: 12px;
  border-top: 1px solid var(--rule);
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.stat-card {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 13px;
  padding: 18px;
  background: var(--white);
  border: 1px solid var(--rule);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
  transition:
    transform 200ms ease,
    box-shadow 200ms ease,
    border-color 200ms ease;
}

.stat-card:hover {
  border-color: var(--rule-strong);
  box-shadow: var(--shadow);
  transform: translateY(-2px);
}

.stat-icon {
  display: grid;
  flex: 0 0 auto;
  width: 42px;
  height: 42px;
  place-items: center;
  color: var(--tone);
  font-size: 20px;
  background: var(--tone-soft);
  border-radius: 11px;
}

.stat-body {
  display: grid;
  min-width: 0;
  gap: 2px;
}

.stat-label {
  color: var(--ink-soft);
  font-size: 12px;
}

.stat-value {
  color: var(--ink);
  font-family: var(--font-numeric);
  font-size: 26px;
  font-weight: 800;
  line-height: 1.15;
}

.stat-value small {
  margin-left: 4px;
  color: var(--ink-soft);
  font-family: var(--font-sans);
  font-size: 12px;
  font-weight: 600;
}

.stat-hint {
  overflow: hidden;
  color: var(--ink-faint);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.stat-card.tone-blue {
  --tone: var(--primary);
  --tone-soft: var(--primary-soft);
}

.stat-card.tone-green {
  --tone: var(--jade);
  --tone-soft: var(--jade-soft);
}

.stat-card.tone-amber {
  --tone: var(--ochre);
  --tone-soft: var(--ochre-soft);
}

.stat-card.tone-coral {
  --tone: var(--cinnabar);
  --tone-soft: var(--cinnabar-soft);
}

.dashboard-shell {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  align-items: start;
  gap: 18px;
}

.dashboard-main {
  display: grid;
  min-width: 0;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}

.dashboard-rail {
  display: grid;
  min-width: 0;
  gap: 16px;
}

.dash-panel {
  display: grid;
  align-content: start;
  gap: 14px;
  padding: 18px 20px 20px;
  background: var(--white);
  border: 1px solid var(--rule);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
}

.panel-wide {
  grid-column: 1 / -1;
}

.rail-panel {
  gap: 12px;
  padding: 16px;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--rule);
}

.panel-head h2 {
  margin: 0;
  color: var(--ink);
  font-family: var(--font-display);
  font-size: 16px;
  font-weight: 800;
}

.panel-head span {
  color: var(--ink-faint);
  font-size: 12px;
}

.panel-link {
  padding: 0;
  color: var(--primary-deep);
  font-size: 12px;
  background: none;
  border: none;
  cursor: pointer;
}

.panel-link:hover {
  text-decoration: underline;
}

.dash-empty {
  color: var(--ink-soft);
  font-size: 13px;
  line-height: 1.75;
}

.course-ledger {
  display: grid;
  gap: 0;
}

.course-row {
  display: grid;
  grid-template-columns: 56px minmax(0, 1fr) auto;
  gap: 16px;
  align-items: center;
  padding: 14px 0;
  border-bottom: 1px solid var(--rule);
}

.course-row:last-child {
  border-bottom: 0;
}

.course-main {
  display: grid;
  min-width: 0;
  gap: 5px;
}

.course-heading {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}

.course-heading strong {
  color: var(--ink);
  font-family: var(--font-display);
  font-size: 16px;
}

.course-heading span,
.course-side {
  color: var(--ink-soft);
  font-size: 12px;
}

.course-side {
  display: grid;
  justify-items: end;
  gap: 5px;
  min-width: 92px;
}

.course-action-label {
  color: var(--primary-deep);
  font-size: 11px;
}

.interaction-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 20px;
  color: var(--ink-soft);
  font-size: 12px;
}

.interaction-summary b {
  color: var(--ink);
  font-family: var(--font-numeric);
  font-size: 17px;
}

.goal-row {
  display: grid;
  gap: 6px;
  padding: 11px 0;
  border-bottom: 1px solid var(--rule);
}

.goal-row:last-child {
  border-bottom: 0;
}

.goal-head,
.goal-meta {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  color: var(--ink-soft);
  font-size: 11px;
}

.goal-head strong {
  overflow: hidden;
  color: var(--ink);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.course-art {
  display: grid;
  width: 56px;
  height: 56px;
  place-items: center;
  color: var(--white);
  font-family: var(--font-display);
  font-size: 21px;
  font-weight: 800;
  border-radius: 10px;
}

.course-art--0 {
  background: linear-gradient(135deg, #4f86f0, #2c63d4);
}

.course-art--1 {
  background: linear-gradient(135deg, #279c74, #167a55);
}

.course-art--2 {
  background: linear-gradient(135deg, #d98a1f, #b56c08);
}

.course-art--3 {
  background: linear-gradient(135deg, #d95545, #b53a2d);
}

.course-body {
  display: grid;
  min-width: 0;
  gap: 3px;
}

.course-name {
  overflow: hidden;
  color: var(--ink);
  font-size: 13px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.course-meta {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  color: var(--ink-soft);
  font-size: 11px;
}

.course-track {
  height: 6px;
  margin-top: 4px;
  overflow: hidden;
  background: var(--white);
  border-radius: var(--radius-pill);
}

.course-fill {
  height: 100%;
  background: linear-gradient(90deg, var(--primary), #6aa4ff);
  border-radius: var(--radius-pill);
  transition: width 560ms cubic-bezier(0.22, 1, 0.36, 1);
}

.action-list,
.class-list,
.score-list,
.risk-list,
.note-list,
.note-events,
.advice-list,
.today-list {
  display: grid;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.action-list li {
  display: grid;
  grid-template-columns: 92px minmax(0, 1fr);
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid var(--rule);
}

.action-list li:last-child {
  border-bottom: none;
}

.action-list li.is-overdue .action-date {
  color: var(--cinnabar);
  font-weight: 700;
}

.action-date {
  color: var(--ink-soft);
  font-size: 12px;
}

.action-body {
  min-width: 0;
}

.action-title {
  display: block;
  color: var(--ink);
  font-size: 13px;
  font-weight: 600;
}

.action-meta {
  display: block;
  margin-top: 3px;
  color: var(--ink-soft);
  font-size: 11px;
  line-height: 1.6;
}

.bar-row {
  display: grid;
  grid-template-columns: 92px minmax(0, 1fr) 52px;
  align-items: center;
  gap: 10px;
}

.bar-label {
  overflow: hidden;
  color: var(--ink);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.bar-track {
  height: 7px;
  overflow: hidden;
  background: var(--paper-deep);
  border-radius: var(--radius-pill);
}

.bar-fill {
  height: 100%;
  background: var(--primary);
  border-radius: var(--radius-pill);
  transition: width 560ms cubic-bezier(0.22, 1, 0.36, 1);
}

.bar-fill.is-cinnabar {
  background: var(--cinnabar);
}

.bar-fill.is-ochre {
  background: var(--ochre);
}

.bar-fill.is-jade {
  background: var(--jade);
}

.bar-value {
  color: var(--ink-soft);
  font-size: 12px;
  text-align: right;
}

.class-list li {
  display: grid;
  gap: 8px;
  padding: 12px 0;
  border-bottom: 1px solid var(--rule);
}

.class-list li:last-child {
  border-bottom: none;
}

.class-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.class-head strong {
  color: var(--ink);
  font-size: 13px;
}

.class-subject {
  color: var(--ink-soft);
  font-size: 11px;
}

.class-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  color: var(--ink-soft);
  font-size: 11px;
}

.score-list li {
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr) 48px;
  align-items: center;
  gap: 12px;
  padding: 9px 0;
  border-bottom: 1px solid var(--rule);
}

.score-max {
  color: var(--ink-soft);
  font-family: var(--font-numeric);
  font-size: 11px;
  text-align: right;
}

.score-list li:last-child {
  border-bottom: none;
}

.score-list strong {
  color: var(--jade);
  font-family: var(--font-numeric);
  font-size: 21px;
  font-weight: 800;
}

.score-list strong.is-low,
.risk-list strong.is-low {
  color: var(--cinnabar);
}

.risk-list li {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 44px;
  align-items: center;
  gap: 10px;
  padding: 9px 0;
  border-bottom: 1px solid var(--rule);
}

.risk-list li:last-child {
  border-bottom: none;
}

.risk-list strong {
  color: var(--jade);
  font-family: var(--font-numeric);
  text-align: right;
}

.advice-list li {
  position: relative;
  padding-left: 15px;
  color: var(--ink-soft);
  font-size: 12px;
  line-height: 1.75;
}

.advice-list li::before {
  position: absolute;
  top: 8px;
  left: 0;
  width: 6px;
  height: 6px;
  background: var(--primary);
  border-radius: 50%;
  content: "";
}

.tag-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.rail-sub {
  margin: 0;
  color: var(--ink-soft);
  font-size: 11px;
  line-height: 1.7;
}

.quick-actions {
  display: grid;
  gap: 10px;
}

.today-list li {
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr);
  gap: 10px;
  align-items: start;
  padding: 9px 0;
  border-bottom: 1px solid var(--rule);
}

.today-list li:last-child {
  border-bottom: none;
}

.today-check {
  width: 16px;
  height: 16px;
  margin-top: 2px;
  border: 2px solid var(--rule-strong);
  border-radius: 5px;
}

.today-list li.is-overdue .action-title {
  color: var(--cinnabar);
}

.note-events {
  gap: 6px;
}

.note-events li {
  display: flex;
  align-items: baseline;
  gap: 8px;
  color: var(--ink-soft);
  font-size: 11px;
}

.note-events em {
  padding: 1px 7px;
  color: var(--primary-deep);
  font-size: 10px;
  font-style: normal;
  background: var(--primary-soft);
  border-radius: var(--radius-pill);
}

.note-list li {
  display: grid;
  gap: 6px;
  padding: 10px 12px;
  background: var(--primary-wash);
  border-left: 3px solid var(--ochre);
  border-radius: var(--radius-md);
}

.note-list li.tone-important {
  border-left-color: var(--cinnabar);
}

.note-list li.tone-relax {
  border-left-color: var(--jade);
}

.note-list p {
  margin: 0;
  font-size: 12px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.note-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.note-actions button {
  padding: 0;
  color: var(--ink-soft);
  font-size: 11px;
  background: none;
  border: none;
  cursor: pointer;
}

.note-actions button:hover {
  color: var(--cinnabar);
}

.note-editor {
  display: grid;
  gap: 10px;
  padding: 12px;
  background: var(--paper);
  border: 1px solid var(--rule);
  border-radius: var(--radius-md);
}

.note-editor-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

@media (max-width: 1240px) {
  .stat-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 1180px) {
  .dashboard-shell {
    grid-template-columns: 1fr;
  }

  .dashboard-rail {
    grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  }
}

@media (max-width: 900px) {
  .dash-hero {
    grid-template-columns: 1fr;
  }

  .dash-hero-art {
    width: 100%;
    opacity: 0.42;
  }

  .teacher-hero-panel {
    border-top: 1px solid var(--rule);
    border-left: 0;
  }

  .dashboard-main {
    grid-template-columns: 1fr;
  }

  .panel-wide {
    grid-column: auto;
  }
}

@media (max-width: 620px) {
  .course-row {
    grid-template-columns: 44px minmax(0, 1fr);
  }

  .course-side {
    grid-column: 2;
    justify-items: start;
  }
}

@media (max-width: 620px) {
  .stat-grid {
    grid-template-columns: 1fr;
  }

  .dash-hero {
    padding: 20px;
  }

  .dash-hero-copy h1 {
    font-size: 24px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .stat-card,
  .bar-fill,
  .course-fill {
    transition: none;
  }
}
</style>
