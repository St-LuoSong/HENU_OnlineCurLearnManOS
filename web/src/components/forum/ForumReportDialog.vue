<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { reactive, ref, watch } from 'vue'

import { reportForumContent } from '@/api/forum'

const props = defineProps<{
  modelValue: boolean
  targetType: 'POST' | 'REPLY'
  targetId?: number | null
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'submitted'): void
}>()

const reporting = ref(false)
const form = reactive({
  reason: 'SPAM',
  detail: ''
})

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) {
      form.reason = 'SPAM'
      form.detail = ''
    }
  }
)

async function submit(): Promise<void> {
  if (reporting.value || !props.targetId) {
    return
  }
  reporting.value = true
  try {
    await reportForumContent({
      targetType: props.targetType,
      targetId: props.targetId,
      reason: form.reason,
      detail: form.detail.trim() || undefined
    })
    ElMessage.success('举报已提交，管理员会尽快处理')
    emit('update:modelValue', false)
    emit('submitted')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '举报提交失败')
  } finally {
    reporting.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="举报内容"
    width="520px"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-form label-position="top">
      <el-form-item label="举报原因" required>
        <el-select v-model="form.reason" style="width: 100%">
          <el-option label="垃圾信息 / 广告" value="SPAM" />
          <el-option label="辱骂或人身攻击" value="ABUSE" />
          <el-option label="内容错误 / 误导" value="WRONG_CONTENT" />
          <el-option label="推广引流" value="ADVERTISING" />
          <el-option label="其他" value="OTHER" />
        </el-select>
      </el-form-item>
      <el-form-item label="补充说明">
        <el-input
          v-model="form.detail"
          type="textarea"
          :rows="4"
          maxlength="1000"
          placeholder="可补充说明问题所在"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="reporting" @click="submit">提交举报</el-button>
    </template>
  </el-dialog>
</template>
