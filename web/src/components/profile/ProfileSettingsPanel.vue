<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { computed, reactive, ref, watch } from 'vue'

import {
  updateUserProfile,
  type UserProfile,
  type UserProfileUpdatePayload
} from '@/api/settings'

const props = defineProps<{
  profile: UserProfile | null
}>()

const emit = defineEmits<{
  (event: 'saved', profile: UserProfile): void
}>()

const saving = ref(false)
const form = reactive<UserProfileUpdatePayload>({
  displayName: '',
  email: '',
  avatarUrl: '',
  department: '',
  major: '',
  gradeLevel: '',
  phone: '',
  bio: '',
  teacherTitle: '',
  researchArea: '',
  notifyEmail: true,
  notifyForumReply: true
})
const isTeacher = computed(() => props.profile?.role === 'TEACHER')

watch(
  () => props.profile,
  (value) => {
    if (!value) {
      return
    }
    form.displayName = value.displayName ?? ''
    form.email = value.email ?? ''
    form.avatarUrl = value.avatarUrl ?? ''
    form.department = value.department ?? ''
    form.major = value.major ?? ''
    form.gradeLevel = value.gradeLevel ?? ''
    form.phone = value.phone ?? ''
    form.bio = value.bio ?? ''
    form.teacherTitle = value.teacherTitle ?? ''
    form.researchArea = value.researchArea ?? ''
    form.notifyEmail = value.notifyEmail !== false
    form.notifyForumReply = value.notifyForumReply !== false
  },
  { immediate: true }
)

async function save(): Promise<void> {
  if (!form.displayName.trim()) {
    ElMessage.warning('姓名不能为空')
    return
  }
  saving.value = true
  try {
    const saved = await updateUserProfile({ ...form })
    emit('saved', saved)
    ElMessage.success('基本资料已保存')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '基本资料保存失败')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <section class="center-panel">
    <div class="center-panel-head">
      <div>
        <h2>基本资料</h2>
        <p>登录标识由学校统一分配，姓名、院系与联系方式可以自行维护。</p>
      </div>
      <span v-if="profile?.updatedAt" class="center-panel-meta">
        最近更新 {{ profile.updatedAt.slice(0, 16).replace('T', ' ') }}
      </span>
    </div>

    <div class="profile-identity">
      <span class="identity-label">登录标识</span>
      <span class="identity-value">{{ profile?.loginId || '—' }}</span>
      <span class="identity-hint">学号 / 工号只读</span>
    </div>

    <el-form label-width="96px" class="center-form">
      <el-form-item label="姓名">
        <el-input v-model="form.displayName" maxlength="64" placeholder="请输入姓名" />
      </el-form-item>
      <el-form-item label="邮箱">
        <el-input v-model="form.email" maxlength="128" placeholder="用于接收通知" />
      </el-form-item>
      <el-form-item label="头像地址">
        <el-input v-model="form.avatarUrl" maxlength="512" placeholder="可选，图片链接" />
      </el-form-item>
      <el-form-item label="院系">
        <el-input v-model="form.department" maxlength="128" placeholder="例如：计算机学院" />
      </el-form-item>
      <el-form-item label="专业">
        <el-input v-model="form.major" maxlength="128" placeholder="例如：软件工程" />
      </el-form-item>
      <el-form-item v-if="!isTeacher" label="年级">
        <el-input v-model="form.gradeLevel" maxlength="32" placeholder="例如：2024 级" />
      </el-form-item>
      <el-form-item v-if="isTeacher" label="职称">
        <el-input v-model="form.teacherTitle" maxlength="64" placeholder="例如：副教授" />
      </el-form-item>
      <el-form-item v-if="isTeacher" label="研究方向">
        <el-input v-model="form.researchArea" maxlength="255" placeholder="例如：分布式系统" />
      </el-form-item>
      <el-form-item label="手机号">
        <el-input v-model="form.phone" maxlength="32" placeholder="可选" />
      </el-form-item>
      <el-form-item label="个人简介">
        <el-input
          v-model="form.bio"
          type="textarea"
          :rows="3"
          maxlength="500"
          placeholder="简单介绍你的学习或教学方向"
        />
      </el-form-item>
    </el-form>
    <div class="center-actions">
      <el-button type="primary" :loading="saving" @click="save">保存基本资料</el-button>
    </div>
  </section>
</template>
