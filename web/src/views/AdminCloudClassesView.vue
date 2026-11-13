<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import {
  adminUpdateCloudClassStatus,
  listAllCloudClasses,
  type CloudClass
} from '@/api/cloudClass'

const loading = ref(false)
const classes = ref<CloudClass[]>([])
const filters = reactive({
  status: '',
  keyword: ''
})

onMounted(() => {
  void load()
})

async function load(): Promise<void> {
  loading.value = true
  try {
    classes.value = await listAllCloudClasses({
      status: filters.status || undefined,
      keyword: filters.keyword.trim() || undefined
    })
  } catch {
    ElMessage.error('云班级列表加载失败')
  } finally {
    loading.value = false
  }
}

async function changeStatus(
  cloudClass: CloudClass,
  status: 'ACTIVE' | 'ARCHIVED' | 'CLOSED'
): Promise<void> {
  try {
    if (status !== 'ACTIVE') {
      await ElMessageBox.confirm(
        `确认将班级“${cloudClass.name}”${status === 'CLOSED' ? '冻结关闭' : '归档'}吗？`,
        '班级状态变更',
        { type: 'warning' }
      )
    }
    await adminUpdateCloudClassStatus(cloudClass.id, status)
    await load()
    ElMessage.success('班级状态已更新')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error instanceof Error ? error.message : '班级状态更新失败')
    }
  }
}
</script>

<template>
  <div v-loading="loading" class="ledger-page">
    <header class="ledger-header">
      <div>
        <div class="ledger-kicker">管理后台</div>
        <h1 class="ledger-title">云班级监管</h1>
        <p class="ledger-subtitle">
          查看所有教师创建的云班级，按状态与关键词筛选，必要时冻结或归档异常班级。
        </p>
      </div>
      <div class="ledger-date">共 {{ classes.length }} 个云班级</div>
    </header>

    <div class="page-toolbar">
      <div class="toolbar-filters">
        <el-select v-model="filters.status" clearable placeholder="全部状态" style="width: 150px">
          <el-option label="教学中" value="ACTIVE" />
          <el-option label="已归档" value="ARCHIVED" />
          <el-option label="已冻结" value="CLOSED" />
        </el-select>
        <el-input
          v-model="filters.keyword"
          clearable
          placeholder="班级名称或班级码"
          style="width: 220px"
          @keyup.enter="load"
        />
        <el-button type="primary" @click="load">查询</el-button>
      </div>
    </div>

    <el-table :data="classes" border>
      <el-table-column prop="name" label="班级名称" min-width="180" />
      <el-table-column prop="subjectName" label="课程" width="140">
        <template #default="{ row }">{{ row.subjectName || '未设置' }}</template>
      </el-table-column>
      <el-table-column prop="teacherName" label="教师" width="130" />
      <el-table-column prop="classCode" label="班级码" width="110" />
      <el-table-column prop="memberCount" label="学生数" width="90" />
      <el-table-column prop="taskCount" label="任务数" width="90" />
      <el-table-column prop="resourceCount" label="资源数" width="90" />
      <el-table-column prop="status" label="状态" width="100" />
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.status === 'ACTIVE'"
            link
            type="danger"
            @click="changeStatus(row as CloudClass, 'CLOSED')"
          >
            冻结
          </el-button>
          <el-button
            v-else
            link
            type="primary"
            @click="changeStatus(row as CloudClass, 'ACTIVE')"
          >
            恢复
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>
