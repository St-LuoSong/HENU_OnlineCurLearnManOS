<script setup lang="ts">
import { computed } from 'vue'

import type {
  CloudClass,
  CloudClassResource,
  CloudClassStatistics,
  CloudClassTask
} from '@/api/cloudClass'
import { formatDateTime } from '@/utils/date'

const props = defineProps<{
  classInfo: CloudClass
  tasks: CloudClassTask[]
  resources: CloudClassResource[]
  statistics: CloudClassStatistics | null
  isTeacher: boolean
}>()

const emit = defineEmits<{
  (event: 'open-task', task: CloudClassTask): void
  (event: 'open-resource', resource: CloudClassResource): void
  (event: 'open-tasks'): void
}>()

const pendingSubmissionCount = computed(() => {
  const stats = props.statistics
  return stats ? Math.max(0, stats.submissionCount - stats.gradedCount) : 0
})

const upcomingTasks = computed(() =>
  props.tasks
    .filter((task) => task.status === 'PUBLISHED')
    .sort((left, right) => String(left.dueAt ?? '9999').localeCompare(String(right.dueAt ?? '9999')))
    .slice(0, 4)
)

const overdueTasks = computed(() => {
  const now = Date.now()
  return props.tasks.filter((task) =>
    task.dueAt
      && new Date(task.dueAt).getTime() < now
      && task.mySubmissionStatus !== 'GRADED'
  )
})

const recentActivities = computed(() => [
  ...props.resources.map((item) => ({
    id: `resource-${item.id}`,
    type: '资源',
    title: item.title,
    date: item.createdAt,
    raw: item
  })),
  ...props.tasks.map((item) => ({
    id: `task-${item.id}`,
    type: '任务',
    title: item.title,
    date: item.dueAt ?? '',
    raw: item
  }))
].sort((left, right) => String(right.date).localeCompare(String(left.date))).slice(0, 5))

const classHealth = computed(() => {
  if (props.isTeacher) {
    return pendingSubmissionCount.value > 0
      ? `还有 ${pendingSubmissionCount.value} 份提交待批改`
      : '当前没有待批改提交'
  }
  return overdueTasks.value.length > 0
    ? `有 ${overdueTasks.value.length} 个任务已逾期`
    : '当前没有逾期任务'
})

function dueLabel(task: CloudClassTask): string {
  if (!task.dueAt) {
    return '未设置截止时间'
  }
  const due = new Date(task.dueAt).getTime()
  const diff = due - Date.now()
  if (diff < 0) {
    return '已逾期'
  }
  const days = Math.ceil(diff / 86_400_000)
  return days <= 1 ? '今天截止' : `${days} 天后截止`
}

function openTask(task: CloudClassTask): void {
  emit('open-task', task)
}
</script>

<template>
  <div class="class-overview">
    <section class="overview-block overview-queue">
      <header class="overview-head">
        <div>
          <h3>{{ isTeacher ? '待处理课堂事项' : '接下来要完成' }}</h3>
          <p>{{ classHealth }}</p>
        </div>
        <button type="button" class="overview-link" @click="emit('open-tasks')">
          查看全部任务
        </button>
      </header>

      <div v-if="isTeacher" class="overview-summary-row">
        <div>
          <strong>{{ pendingSubmissionCount }}</strong>
          <span>待批改提交</span>
        </div>
        <div>
          <strong>{{ statistics?.memberCount ?? classInfo.memberCount }}</strong>
          <span>班级成员</span>
        </div>
        <div>
          <strong>{{ statistics?.averageScore ?? 0 }}</strong>
          <span>班级平均分</span>
        </div>
      </div>

      <div v-if="upcomingTasks.length === 0" class="overview-empty">
        当前没有已发布的任务，发布一次课堂任务后，学生会在首页看到提醒。
      </div>
      <button
        v-for="task in upcomingTasks"
        :key="task.id"
        type="button"
        class="overview-task"
        @click="openTask(task)"
      >
        <span class="task-state-dot" :class="dueLabel(task) === '已逾期' ? 'is-overdue' : 'is-open'" />
        <span class="overview-task-body">
          <strong>{{ task.title }}</strong>
          <small>{{ dueLabel(task) }} · {{ task.taskType === 'TEST' ? '测试' : '任务' }}</small>
        </span>
        <span class="overview-task-arrow">→</span>
      </button>
    </section>

    <section class="overview-block overview-recent">
      <header class="overview-head">
        <div>
          <h3>最近课堂动态</h3>
          <p>资源与任务的发布时间线</p>
        </div>
      </header>
      <div v-if="recentActivities.length === 0" class="overview-empty">
        还没有课堂动态。教师发布资源或任务后会显示在这里。
      </div>
      <div v-for="item in recentActivities" :key="item.id" class="activity-row">
        <span class="activity-type">{{ item.type }}</span>
        <span class="activity-copy">
          <strong>{{ item.title }}</strong>
          <small>{{ formatDateTime(item.date) }}</small>
        </span>
      </div>
    </section>
  </div>
</template>

<style scoped>
.class-overview {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(280px, 0.85fr);
  gap: 24px;
  padding-top: 18px;
}

.overview-block {
  min-width: 0;
}

.overview-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--rule);
}

.overview-head h3 {
  margin: 0;
  color: var(--ink);
  font-family: var(--font-serif);
  font-size: 18px;
}

.overview-head p {
  margin: 5px 0 0;
  color: var(--ink-soft);
  font-size: 11px;
}

.overview-link {
  padding: 0;
  color: var(--primary-deep);
  font-size: 11px;
  background: none;
  border: 0;
  cursor: pointer;
}

.overview-summary-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  padding: 14px 0;
  border-bottom: 1px solid var(--rule);
}

.overview-summary-row div {
  display: grid;
  gap: 3px;
}

.overview-summary-row strong {
  color: var(--primary-deep);
  font-family: var(--font-numeric);
  font-size: 24px;
}

.overview-summary-row span {
  color: var(--ink-soft);
  font-size: 10px;
}

.overview-task {
  display: grid;
  grid-template-columns: 8px minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
  width: 100%;
  padding: 11px 0;
  text-align: left;
  background: transparent;
  border: 0;
  border-bottom: 1px solid var(--rule);
  cursor: pointer;
}

.overview-task:hover .overview-task-body strong {
  color: var(--primary-deep);
}

.task-state-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
}

.task-state-dot.is-open { background: var(--jade); }
.task-state-dot.is-overdue { background: var(--cinnabar); }

.overview-task-body {
  display: grid;
  min-width: 0;
  gap: 3px;
}

.overview-task-body strong {
  overflow: hidden;
  color: var(--ink);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: color 140ms ease;
}

.overview-task-body small {
  color: var(--ink-soft);
  font-size: 10px;
}

.overview-task-arrow {
  color: var(--ink-faint);
  font-size: 15px;
}

.overview-empty {
  padding: 18px 0;
  color: var(--ink-soft);
  font-size: 12px;
  line-height: 1.7;
}

.activity-row {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr);
  gap: 10px;
  align-items: start;
  padding: 11px 0;
  border-bottom: 1px solid var(--rule);
}

.activity-type {
  padding-top: 2px;
  color: var(--primary-deep);
  font-size: 10px;
}

.activity-copy {
  display: grid;
  min-width: 0;
  gap: 3px;
}

.activity-copy strong {
  overflow: hidden;
  color: var(--ink);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.activity-copy small {
  color: var(--ink-soft);
  font-size: 10px;
}

@media (max-width: 900px) {
  .class-overview {
    grid-template-columns: 1fr;
  }
}
</style>
