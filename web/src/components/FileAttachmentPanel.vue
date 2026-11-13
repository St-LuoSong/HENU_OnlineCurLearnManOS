<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'

import {
  downloadFile,
  FILE_ACCEPT,
  FILE_MAX_SIZE_MB,
  formatFileSize,
  previewableExtension,
  uploadFile,
  type FileAttachment,
  type FilePurpose
} from '@/api/files'
import FilePreviewDialog from '@/components/FilePreviewDialog.vue'

const props = withDefaults(
  defineProps<{
    attachments: FileAttachment[]
    loading?: boolean
    uploadable?: boolean
    purpose?: FilePurpose
    emptyText?: string
    hint?: string
  }>(),
  {
    loading: false,
    uploadable: false,
    purpose: 'PLATFORM_RESOURCE',
    emptyText: '暂无附件',
    hint: ''
  }
)

const emit = defineEmits<{
  (event: 'uploaded', fileId: number): void
  (event: 'remove', attachment: FileAttachment): void
}>()

const uploading = ref(false)
const progress = ref(0)
const previewVisible = ref(false)
const previewId = ref<number | null>(null)
const previewName = ref('')

function beforeUpload(rawFile: unknown): boolean {
  const file = rawFile as File
  const extension = file.name.split('.').pop()?.toLowerCase() ?? ''
  if (!FILE_ACCEPT.includes(`.${extension}`)) {
    ElMessage.warning('只支持 xls / xlsx / docx / pdf / md / txt 文件')
    return false
  }
  if (file.size > FILE_MAX_SIZE_MB * 1024 * 1024) {
    ElMessage.warning(`单个文件不能超过 ${FILE_MAX_SIZE_MB} MB`)
    return false
  }
  return true
}

async function handleUpload(options: unknown): Promise<void> {
  const { file } = options as { file: File }
  uploading.value = true
  progress.value = 0
  try {
    const asset = await uploadFile(file, props.purpose, (percent) => {
      progress.value = percent
    })
    emit('uploaded', asset.id)
    ElMessage.success('文件已上传')
  } catch (error) {
    ElMessage.error(error instanceof Error && error.message ? error.message : '文件上传失败')
  } finally {
    uploading.value = false
    progress.value = 0
  }
}

function openPreview(attachment: FileAttachment): void {
  previewId.value = attachment.fileId
  previewName.value = attachment.originalName
  previewVisible.value = true
}

async function download(attachment: FileAttachment): Promise<void> {
  try {
    await downloadFile(attachment.fileId, attachment.originalName)
  } catch (error) {
    ElMessage.error(error instanceof Error && error.message ? error.message : '下载失败')
  }
}
</script>

<template>
  <div v-loading="loading" class="attachment-panel">
    <div v-if="attachments.length === 0" class="empty-ledger">{{ emptyText }}</div>
    <div v-for="attachment in attachments" :key="attachment.id" class="attachment-line">
      <div class="attachment-main">
        <div class="attachment-name">{{ attachment.originalName }}</div>
        <div class="attachment-meta">
          {{ attachment.extension.toUpperCase() }} · {{ formatFileSize(attachment.sizeBytes) }}
          · {{ attachment.uploadedByName }}
          <span v-if="attachment.versionNo"> · 第 {{ attachment.versionNo }} 版</span>
        </div>
      </div>
      <div class="attachment-actions">
        <el-button
          v-if="previewableExtension(attachment.extension)"
          link
          type="primary"
          @click="openPreview(attachment)"
        >
          在线预览
        </el-button>
        <el-button link type="primary" @click="download(attachment)">下载</el-button>
        <el-button v-if="attachment.canDelete" link type="danger" @click="emit('remove', attachment)">
          删除
        </el-button>
      </div>
    </div>

    <div v-if="uploadable" class="attachment-upload">
      <el-upload
        :show-file-list="false"
        :accept="FILE_ACCEPT"
        :before-upload="beforeUpload"
        :http-request="handleUpload"
        :disabled="uploading"
      >
        <el-button type="primary" plain :loading="uploading">上传附件</el-button>
      </el-upload>
      <span class="attachment-hint">
        {{ hint || `支持 xls / xlsx / docx / pdf / md / txt，单个不超过 ${FILE_MAX_SIZE_MB} MB` }}
      </span>
      <el-progress v-if="uploading" :percentage="progress" class="attachment-progress" />
    </div>

    <FilePreviewDialog
      v-model="previewVisible"
      :file-id="previewId"
      :file-name="previewName"
    />
  </div>
</template>

<style scoped>
.attachment-line {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px solid var(--rule);
}

.attachment-name {
  font-weight: 700;
  word-break: break-all;
}

.attachment-meta {
  margin-top: 4px;
  color: var(--ink-soft);
  font-size: 12px;
}

.attachment-actions {
  display: flex;
  gap: 4px;
}

.attachment-upload {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  padding-top: 14px;
}

.attachment-hint {
  color: var(--ink-soft);
  font-size: 12px;
}

.attachment-progress {
  flex: 1 1 100%;
  max-width: 320px;
}
</style>
