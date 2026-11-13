<script setup lang="ts">
import { Plus, Search, UserFilled } from '@element-plus/icons-vue'
import { computed, ref } from 'vue'

import type { CloudClass } from '@/api/cloudClass'

const props = defineProps<{
  classes: CloudClass[]
  selectedId: number | null
  isTeacher: boolean
}>()

const emit = defineEmits<{
  (event: 'select', id: number): void
  (event: 'create'): void
  (event: 'join'): void
}>()

const keyword = ref('')
const statusFilter = ref<'ALL' | 'ACTIVE' | 'CLOSED' | 'ARCHIVED'>('ALL')

const filteredClasses = computed(() => {
  const search = keyword.value.trim().toLowerCase()
  return props.classes.filter((item) => {
    const statusMatch = statusFilter.value === 'ALL' || item.status === statusFilter.value
    const searchMatch = !search
      || item.name.toLowerCase().includes(search)
      || (item.subjectName ?? '').toLowerCase().includes(search)
      || (item.classCode ?? '').toLowerCase().includes(search)
    return statusMatch && searchMatch
  })
})

function statusLabel(status: string): string {
  const labels: Record<string, string> = {
    ACTIVE: '进行中',
    CLOSED: '已结课',
    ARCHIVED: '已归档'
  }
  return labels[status] ?? status
}

function selectStatus(value: string): void {
  if (value === 'ALL' || value === 'ACTIVE' || value === 'CLOSED' || value === 'ARCHIVED') {
    statusFilter.value = value
  }
}
</script>

<template>
  <aside class="class-rail">
    <div class="rail-heading">
      <div>
        <span class="rail-title">我的课堂</span>
        <small>{{ classes.length }} 个班级</small>
      </div>
      <el-button
        v-if="isTeacher"
        type="primary"
        size="small"
        :icon="Plus"
        @click="emit('create')"
      >
        新建
      </el-button>
      <el-button
        v-else
        type="primary"
        size="small"
        :icon="UserFilled"
        @click="emit('join')"
      >
        加入
      </el-button>
    </div>

    <el-input v-model="keyword" clearable placeholder="搜索班级、课程或班级码">
      <template #prefix><el-icon><Search /></el-icon></template>
    </el-input>

    <div class="rail-filters">
      <button
        v-for="item in [
          { value: 'ALL', label: '全部' },
          { value: 'ACTIVE', label: '进行中' },
          { value: 'CLOSED', label: '结课' },
          { value: 'ARCHIVED', label: '归档' }
        ]"
        :key="item.value"
        type="button"
        class="rail-filter"
        :class="{ active: statusFilter === item.value }"
        @click="selectStatus(item.value)"
      >
        {{ item.label }}
      </button>
    </div>

    <div v-if="filteredClasses.length === 0" class="rail-empty">
      {{ keyword || statusFilter !== 'ALL' ? '没有匹配的班级。' : (isTeacher ? '创建第一个云班级。' : '使用班级码加入班级。') }}
    </div>
    <div v-else class="class-list">
      <button
        v-for="item in filteredClasses"
        :key="item.id"
        type="button"
        class="class-entry"
        :class="{ active: item.id === selectedId }"
        @click="emit('select', item.id)"
      >
        <span class="class-entry-accent" :class="'status-' + item.status.toLowerCase()" />
        <span class="class-entry-body">
          <span class="class-entry-top">
            <strong>{{ item.name }}</strong>
            <small>{{ statusLabel(item.status) }}</small>
          </span>
          <span class="class-entry-subject">{{ item.subjectName || '未设置课程' }}</span>
          <span class="class-entry-meta">
            <template v-if="isTeacher">
              {{ item.memberCount }} 名学生 · {{ item.taskCount }} 个任务
            </template>
            <template v-else>
              待提交 {{ item.myPendingTaskCount }} · 逾期 {{ item.myOverdueTaskCount }}
            </template>
          </span>
          <span v-if="!isTeacher" class="class-entry-progress">
            <span :style="{ width: Math.min(100, item.myGradedTaskCount * 14 + 8) + '%' }" />
          </span>
        </span>
      </button>
    </div>
  </aside>
</template>

<style scoped>
.class-rail {
  display: flex;
  min-height: 0;
  flex-direction: column;
  gap: 12px;
  padding: 18px 16px 18px 0;
  border-right: 1px solid var(--rule);
}

.rail-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.rail-heading > div {
  display: grid;
  gap: 3px;
}

.rail-title {
  color: var(--ink);
  font-family: var(--font-serif);
  font-size: 18px;
  font-weight: 700;
}

.rail-heading small {
  color: var(--ink-soft);
  font-size: 10px;
}

.rail-filters {
  display: flex;
  gap: 5px;
  overflow-x: auto;
}

.rail-filter {
  flex: 0 0 auto;
  padding: 3px 8px;
  color: var(--ink-soft);
  font-size: 10px;
  background: transparent;
  border: 1px solid var(--rule);
  cursor: pointer;
}

.rail-filter:hover,
.rail-filter.active {
  color: var(--primary-deep);
  background: var(--primary-wash);
  border-color: var(--primary-soft);
}

.class-list {
  display: grid;
  min-height: 0;
  gap: 2px;
  overflow-y: auto;
}

.class-entry {
  position: relative;
  display: grid;
  grid-template-columns: 3px minmax(0, 1fr);
  gap: 10px;
  padding: 11px 8px 11px 0;
  text-align: left;
  background: transparent;
  border: 0;
  border-bottom: 1px solid var(--rule);
  cursor: pointer;
}

.class-entry:hover,
.class-entry.active {
  background: var(--paper);
}

.class-entry-accent {
  width: 3px;
  height: 100%;
  min-height: 48px;
  background: var(--rule-strong);
}

.class-entry-accent.status-active { background: var(--jade); }
.class-entry-accent.status-closed { background: var(--ochre); }
.class-entry-accent.status-archived { background: var(--ink-faint); }

.class-entry-body {
  display: grid;
  min-width: 0;
  gap: 3px;
}

.class-entry-top {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}

.class-entry-top strong {
  overflow: hidden;
  color: var(--ink);
  font-size: 13px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.class-entry-top small,
.class-entry-subject,
.class-entry-meta {
  color: var(--ink-soft);
  font-size: 10px;
}

.class-entry-subject {
  color: var(--primary-deep);
}

.class-entry-progress {
  display: block;
  height: 3px;
  margin-top: 3px;
  overflow: hidden;
  background: var(--paper-deep);
}

.class-entry-progress span {
  display: block;
  height: 3px;
  background: var(--primary);
  transition: width 280ms ease;
}

.rail-empty {
  padding: 24px 4px;
  color: var(--ink-soft);
  font-size: 12px;
  line-height: 1.7;
}

@media (max-width: 900px) {
  .class-rail {
    max-height: 260px;
    padding-right: 0;
    border-right: 0;
    border-bottom: 1px solid var(--rule);
  }
}
</style>
