<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'

import { downloadFile, fetchFileBlob, previewFile, type FilePreview } from '@/api/files'

const props = defineProps<{
  modelValue: boolean
  fileId?: number | null
  fileName?: string
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
}>()

const loading = ref(false)
const preview = ref<FilePreview | null>(null)
const pdfUrl = ref('')
const activeSheet = ref(0)
let disposePdf: (() => void) | null = null

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value)
})

watch(
  () => [props.modelValue, props.fileId] as const,
  async ([open, fileId]) => {
    if (!open) {
      cleanup()
      return
    }
    if (fileId) {
      await load(fileId)
    }
  },
  { immediate: true }
)

onBeforeUnmount(cleanup)

async function load(fileId: number): Promise<void> {
  loading.value = true
  preview.value = null
  activeSheet.value = 0
  cleanup()
  try {
    const result = await previewFile(fileId)
    preview.value = result
    if (result.kind === 'PDF') {
      const inline = await fetchFileBlob(fileId, true)
      pdfUrl.value = inline.url
      disposePdf = inline.dispose
    }
  } catch (error) {
    ElMessage.error(error instanceof Error && error.message ? error.message : '预览加载失败')
    visible.value = false
  } finally {
    loading.value = false
  }
}

function cleanup(): void {
  if (disposePdf) {
    disposePdf()
    disposePdf = null
  }
  pdfUrl.value = ''
}

async function download(): Promise<void> {
  if (!props.fileId) {
    return
  }
  try {
    await downloadFile(props.fileId, props.fileName ?? preview.value?.originalName ?? 'file')
  } catch (error) {
    ElMessage.error(error instanceof Error && error.message ? error.message : '下载失败')
  }
}
</script>

<template>
  <el-dialog v-model="visible" :title="fileName || preview?.originalName || '文件预览'" width="880px">
    <div v-loading="loading" class="preview-shell">
      <template v-if="preview">
        <!-- 服务端已在生成 HTML 前完成转义与白名单标签，前端不再二次插入用户内容 -->
        <div v-if="preview.kind === 'TEXT'" class="preview-text">{{ preview.text }}</div>
        <div v-else-if="preview.kind === 'HTML'" class="preview-html" v-html="preview.html" />
        <div v-else-if="preview.kind === 'PDF'" class="preview-pdf">
          <iframe v-if="pdfUrl" :src="pdfUrl" title="PDF 预览" />
          <div v-else class="empty-ledger">PDF 正在加载…</div>
        </div>
        <div v-else-if="preview.kind === 'SHEET'" class="preview-sheet">
          <el-tabs v-model="activeSheet" type="card">
            <el-tab-pane
              v-for="(sheet, index) in preview.sheets"
              :key="sheet.name + index"
              :label="sheet.name"
              :name="index"
            >
              <div class="sheet-scroll">
                <table class="sheet-table">
                  <tbody>
                    <tr v-for="(row, rowIndex) in sheet.rows" :key="rowIndex">
                      <td v-for="(cell, cellIndex) in row" :key="cellIndex">{{ cell }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
              <div v-if="sheet.truncated" class="preview-tip">该工作表内容较多，仅展示前部分。</div>
            </el-tab-pane>
          </el-tabs>
          <div v-if="preview.sheets.length === 0" class="empty-ledger">表格中没有可展示的数据。</div>
        </div>
        <el-alert
          v-if="preview.truncated"
          type="warning"
          show-icon
          :closable="false"
          title="内容较多，仅展示前部分，完整内容请下载查看"
        />
      </template>
      <div v-else-if="!loading" class="empty-ledger">暂无可预览的内容。</div>
    </div>
    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
      <el-button type="primary" @click="download">下载原文件</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.preview-shell {
  min-height: 180px;
}

.preview-text {
  max-height: 520px;
  overflow: auto;
  padding: 16px;
  margin: 0;
  background: var(--paper-deep);
  font-family: "JetBrains Mono", "Cascadia Code", Consolas, monospace;
  font-size: 13px;
  line-height: 1.9;
  white-space: pre-wrap;
  word-break: break-word;
}

.preview-html {
  max-height: 520px;
  overflow: auto;
  padding: 4px 16px;
  line-height: 1.9;
}

.preview-html :deep(h1),
.preview-html :deep(h2),
.preview-html :deep(h3) {
  font-family: var(--font-display);
}

.preview-html :deep(table),
.preview-html :deep(.docx-table) {
  width: 100%;
  border-collapse: collapse;
  margin: 12px 0;
}

.preview-html :deep(td) {
  border: 1px solid var(--rule);
  padding: 6px 10px;
}

.preview-html :deep(pre) {
  padding: 12px;
  background: var(--paper-deep);
  overflow: auto;
}

.preview-html :deep(blockquote) {
  margin: 12px 0;
  padding: 6px 14px;
  border-left: 3px solid var(--jade);
  color: var(--ink-soft);
}

.preview-pdf iframe {
  width: 100%;
  height: 560px;
  border: 1px solid var(--rule);
}

.sheet-scroll {
  max-height: 420px;
  overflow: auto;
}

.sheet-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.sheet-table td {
  border: 1px solid var(--rule);
  padding: 6px 10px;
  white-space: nowrap;
}

.sheet-table tr:first-child td {
  background: var(--paper-deep);
  font-weight: 700;
}

.preview-tip {
  margin-top: 8px;
  color: var(--ink-soft);
  font-size: 12px;
}
</style>
