<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { computed, reactive, ref, watch } from 'vue'

import {
  createForumPost,
  updateForumPost,
  type ForumCategory,
  type ForumMedia,
  type ForumPostDetail,
  type ForumPostSaveRequest,
  type ForumPostSummary
} from '@/api/forum'
import MediaUploader from '@/components/media/MediaUploader.vue'

const props = defineProps<{
  modelValue: boolean
  categories: ForumCategory[]
  post?: ForumPostSummary | ForumPostDetail | null
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'saved', post: ForumPostDetail): void
}>()

const saving = ref(false)
const mediaBusy = ref(false)
const form = reactive({
  categoryId: undefined as number | undefined,
  title: '',
  content: '',
  mediaFileIds: [] as number[]
})

/** 编辑时用于回填已有的九宫格；发新帖时为空数组。 */
const initialMedia = computed<ForumMedia[]>(() => {
  const post = props.post
  return post && 'media' in post && Array.isArray(post.media) ? post.media : []
})

watch(
  () => [props.modelValue, props.post] as const,
  () => {
    if (!props.modelValue) {
      return
    }
    form.categoryId = props.post?.categoryId ?? props.categories[0]?.id
    form.title = props.post?.title ?? ''
    form.content = props.post && 'content' in props.post ? props.post.content : ''
    form.mediaFileIds = initialMedia.value.map((item) => item.fileId)
    mediaBusy.value = false
  },
  { immediate: true }
)

async function submit(): Promise<void> {
  if (!form.categoryId) {
    ElMessage.warning('请选择帖子分类')
    return
  }
  if (!form.title.trim() || !form.content.trim()) {
    ElMessage.warning('请填写标题和内容')
    return
  }
  if (mediaBusy.value) {
    ElMessage.warning('图片还在上传，请稍候再发布')
    return
  }
  const payload: ForumPostSaveRequest = {
    categoryId: form.categoryId,
    title: form.title.trim(),
    content: form.content.trim(),
    mediaFileIds: form.mediaFileIds
  }
  saving.value = true
  try {
    const saved = props.post
      ? await updateForumPost(props.post.id, payload)
      : await createForumPost(payload)
    ElMessage.success(props.post ? '帖子已更新' : '帖子已发布')
    emit('update:modelValue', false)
    emit('saved', saved)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '帖子保存失败')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="post ? '编辑帖子' : '发布帖子'"
    width="680px"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-form label-position="top">
      <el-form-item label="分类" required>
        <el-select v-model="form.categoryId" style="width: 100%">
          <el-option
            v-for="category in categories"
            :key="category.id"
            :label="category.name"
            :value="category.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="标题" required>
        <el-input v-model="form.title" maxlength="255" />
      </el-form-item>
      <el-form-item label="内容" required>
        <el-input v-model="form.content" type="textarea" :rows="9" maxlength="20000" />
      </el-form-item>
      <el-form-item label="图片（最多 9 张）">
        <MediaUploader
          :key="props.post?.id ?? 'new'"
          v-model:fileIds="form.mediaFileIds"
          :initial-media="initialMedia"
          purpose="FORUM_IMAGE"
          @update:busy="mediaBusy = $event"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">
        {{ post ? '保存修改' : '发布帖子' }}
      </el-button>
    </template>
  </el-dialog>
</template>
