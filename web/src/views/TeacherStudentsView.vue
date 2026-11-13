<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'

import { listTeacherStudents, type TeacherStudent } from '@/api/cloudClass'

const loading = ref(false)
const students = ref<TeacherStudent[]>([])

onMounted(() => {
  void load()
})

async function load(): Promise<void> {
  loading.value = true
  try {
    students.value = await listTeacherStudents()
  } catch {
    ElMessage.error('学生状态加载失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div v-loading="loading" class="ledger-page">
    <header class="ledger-header">
      <div>
        <div class="ledger-kicker">教师端</div>
        <h1 class="ledger-title">学生状态</h1>
        <p class="ledger-subtitle">查看云班级中的学生、任务完成情况与平均成绩。</p>
      </div>
      <div class="ledger-date">共 {{ students.length }} 条学生记录</div>
    </header>

    <el-table :data="students" border>
      <el-table-column prop="className" label="云班级" min-width="160" />
      <el-table-column prop="loginId" label="学号" width="150" />
      <el-table-column prop="displayName" label="姓名" width="130" />
      <el-table-column prop="totalTaskCount" label="任务总数" width="100" />
      <el-table-column prop="submittedTaskCount" label="已提交" width="90" />
      <el-table-column prop="pendingTaskCount" label="待提交" width="90" />
      <el-table-column prop="overdueTaskCount" label="逾期" width="80" />
      <el-table-column prop="gradedCount" label="已批改" width="90" />
      <el-table-column prop="averageScore" label="平均分" width="100" />
      <el-table-column prop="status" label="成员状态" width="110" />
    </el-table>
  </div>
</template>
