<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { reactive, ref, watch } from 'vue'

import { updateUserProfile, type UserProfile } from '@/api/settings'

const props = defineProps<{
  profile: UserProfile | null
}>()

const emit = defineEmits<{
  (event: 'saved', profile: UserProfile): void
}>()

const saving = ref(false)
const form = reactive({
  notifyEmail: true,
  notifyForumReply: true
})

watch(
  () => props.profile,
  (value) => {
    form.notifyEmail = value?.notifyEmail !== false
    form.notifyForumReply = value?.notifyForumReply !== false
  },
  { immediate: true }
)

async function save(): Promise<void> {
  if (!props.profile) {
    return
  }
  saving.value = true
  try {
    const saved = await updateUserProfile({
      displayName: props.profile.displayName,
      email: props.profile.email ?? '',
      avatarUrl: props.profile.avatarUrl ?? '',
      department: props.profile.department ?? '',
      major: props.profile.major ?? '',
      gradeLevel: props.profile.gradeLevel ?? '',
      phone: props.profile.phone ?? '',
      bio: props.profile.bio ?? '',
      teacherTitle: props.profile.teacherTitle ?? '',
      researchArea: props.profile.researchArea ?? '',
      notifyEmail: form.notifyEmail,
      notifyForumReply: form.notifyForumReply
    })
    emit('saved', saved)
    ElMessage.success('通知设置已保存')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '通知设置保存失败')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <section class="center-panel">
    <div class="center-panel-head">
      <div>
        <h2>通知与隐私</h2>
        <p>只调整与你有关的提醒方式，个人联系方式不会公开给其他学生。</p>
      </div>
      <span class="center-panel-meta">平台内可见</span>
    </div>

    <div class="switch-line">
      <div>
        <div class="switch-title">邮件通知</div>
        <div class="switch-desc">学习任务、审批结果等重要提醒</div>
      </div>
      <el-switch v-model="form.notifyEmail" />
    </div>
    <div class="switch-line">
      <div>
        <div class="switch-title">论坛回复提醒</div>
        <div class="switch-desc">有人回复我的帖子时在站内提醒</div>
      </div>
      <el-switch v-model="form.notifyForumReply" />
    </div>
    <div class="switch-line">
      <div>
        <div class="switch-title">个人资料可见范围</div>
        <div class="switch-desc">姓名、院系与专业对平台内教师与学生可见</div>
      </div>
      <el-tag type="info">平台内可见</el-tag>
    </div>
    <div class="center-actions">
      <el-button type="primary" :loading="saving" @click="save">保存通知设置</el-button>
    </div>
  </section>
</template>
