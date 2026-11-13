<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import {
  deleteFile,
  downloadFile,
  formatFileSize,
  listAdminFiles,
  previewableExtension,
  purgeOrphanFiles,
  type FileAsset
} from '@/api/files'
import FilePreviewDialog from '@/components/FilePreviewDialog.vue'

const PURPOSE_OPTIONS = [
  { label: '全部用途', value: '' },
  { label: '平台教学资源', value: 'PLATFORM_RESOURCE' },
  { label: '云班级资源', value: 'CLASS_RESOURCE' },
  { label: '学生作业附件', value: 'SUBMISSION' }
]

const loading = ref(false)
const purging = ref(false)
const files = ref<FileAsset[]>([])
const total = ref(0)
const page = ref(1)
const size = 20
const purpose = ref('')
const orphanOnly = ref(false)
const previewVisible = ref(false)
const previewId = ref<number | null>(null)
const previewName = ref('')

onMounted(() => {
  void load()
})

async function load(): Promise<void> {
  loading.value = true
  try {
    const result = await listAdminFiles({
      page: page.value,
      size,
      purpose: purpose.value || undefined,
      orphan: orphanOnly.value ? true : undefined
    })
    files.value = result.records
    total.value = result.total
  } catch {
    ElMessage.error('文件列表加载失败')
  } finally {
    loading.value = false
  }
}

function handleFilterChange(): void {
  page.value = 1
  void load()
}

function handlePageChange(value: number): void {
  page.value = value
  void load()
}

function openPreview(asset: FileAsset): void {
  previewId.value = asset.id
  previewName.value = asset.originalName
  previewVisible.value = true
}

async function download(asset: FileAsset): Promise<void> {
  try {
    await downloadFile(asset.id, asset.originalName)
  } catch (error) {
    ElMessage.error(error instanceof Error && error.message ? error.message : '下载失败')
  }
}

async function remove(asset: FileAsset): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `删除文件“${asset.originalName}”会同时解除它在教学资源或作业中的关联，确认继续？`,
      '删除确认',
      { type: 'warning' }
    )
    await deleteFile(asset.id)
    ElMessage.success('文件已删除')
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error instanceof Error && error.message ? error.message : '删除失败')
    }
  }
}

async function purge(): Promise<void> {
  purging.value = true
  try {
    const removed = await purgeOrphanFiles()
    ElMessage.success(removed > 0 ? `已清理 ${removed} 个孤儿文件` : '没有需要清理的孤儿文件')
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error && error.message ? error.message : '清理失败')
  } finally {
    purging.value = false
  }
}

function purposeLabel(value: string): string {
  return PURPOSE_OPTIONS.find((option) => option.value === value)?.label ?? value
}

function formatDateTime(value?: string | null): string {
  return value ? value.replace('T', ' ').slice(0, 19) : '—'
}

function asFile(row: unknown): FileAsset {
  return row as FileAsset
}
</script>

<template>
  <div v-loading="loading" class="ledger-page">
    <header class="ledger-header">
      <div>
        <div class="ledger-kicker">管理后台</div>
        <h1 class="ledger-title">文件治理</h1>
        <p class="ledger-subtitle">
          查看全平台上传文件、检查下载情况、清理没有被任何教学资源或作业引用的孤儿文件，并对违规文件强制下架。
          所有强制操作都会写入审计日志。
        </p>
      </div>
      <div class="ledger-date">共 {{ total }} 个文件</div>
    </header>

    <div class="page-toolbar">
      <div class="toolbar-filters">
        <el-select v-model="purpose" style="width: 170px" @change="handleFilterChange">
          <el-option
            v-for="option in PURPOSE_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-checkbox v-model="orphanOnly" @change="handleFilterChange">只看孤儿文件</el-checkbox>
        <el-button @click="load">刷新</el-button>
      </div>
      <el-button type="warning" plain :loading="purging" @click="purge">
        清理孤儿文件
      </el-button>
    </div>

    <div v-if="files.length === 0" class="empty-ledger">没有符合条件的文件。</div>

    <el-table v-else :data="files" border>
      <el-table-column prop="originalName" label="文件名" min-width="240" show-overflow-tooltip />
      <el-table-column label="类型" width="90">
        <template #default="{ row }">{{ String(row.extension).toUpperCase() }}</template>
      </el-table-column>
      <el-table-column label="大小" width="110">
        <template #default="{ row }">{{ formatFileSize(Number(row.sizeBytes)) }}</template>
      </el-table-column>
      <el-table-column label="用途" width="140">
        <template #default="{ row }">{{ purposeLabel(String(row.purpose)) }}</template>
      </el-table-column>
      <el-table-column prop="ownerName" label="上传者" width="120" />
      <el-table-column prop="downloadCount" label="下载次数" width="100" />
      <el-table-column label="上传时间" width="180">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="210" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="previewableExtension(String(row.extension))"
            link
            type="primary"
            @click="openPreview(asFile(row))"
          >
            预览
          </el-button>
          <el-button link type="primary" @click="download(asFile(row))">下载</el-button>
          <el-button link type="danger" @click="remove(asFile(row))">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-if="total > size"
      class="ledger-pagination"
      layout="prev, pager, next"
      :current-page="page"
      :page-size="size"
      :total="total"
      @current-change="handlePageChange"
    />

    <FilePreviewDialog
      v-model="previewVisible"
      :file-id="previewId"
      :file-name="previewName"
    />
  </div>
</template>
