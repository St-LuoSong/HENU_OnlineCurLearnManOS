<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { onBeforeUnmount, ref, watch } from 'vue'

import {
  fetchFileBlob,
  formatFileSize,
  IMAGE_ACCEPT,
  IMAGE_MAX_SIZE_MB,
  isImageExtension,
  uploadFile,
  type FilePurpose,
  type MediaItem
} from '@/api/files'

interface UploadItem {
  key: string
  fileId: number | null
  name: string
  sizeBytes: number
  progress: number
  status: 'uploading' | 'done' | 'error'
  message?: string
  raw?: File
}

const props = withDefaults(
  defineProps<{
    fileIds: number[]
    initialMedia?: MediaItem[]
    max?: number
    purpose: FilePurpose
    accept?: string
    maxSizeMb?: number
    /** 上传成功后立刻交给父组件落库（附件场景），并从本地上传列表移除。 */
    autoClear?: boolean
    label?: string
  }>(),
  {
    initialMedia: () => [],
    max: 9,
    accept: IMAGE_ACCEPT,
    maxSizeMb: IMAGE_MAX_SIZE_MB,
    autoClear: false,
    label: '图片'
  }
)

const emit = defineEmits<{
  (event: 'update:fileIds', value: number[]): void
  (event: 'update:busy', value: boolean): void
  (event: 'uploaded', fileId: number): void
}>()

const items = ref<UploadItem[]>([])
const urls = ref<Record<string, string>>({})

/** 本地上传预览与远程 blob 都要回收，否则反复上传会持续占用内存。 */
const disposers = new Map<string, () => void>()
let revision = 0
let keySeed = 0

watch(
  () => props.initialMedia,
  async (media) => {
    const revisionId = ++revision
    releaseAll()
    items.value = media
      .slice()
      .sort((left, right) => (left.sortOrder ?? 0) - (right.sortOrder ?? 0))
      .map((entry) => ({
        key: `seed-${++keySeed}`,
        fileId: entry.fileId,
        name: entry.originalName,
        sizeBytes: entry.sizeBytes ?? 0,
        progress: 100,
        status: 'done' as const
      }))
    syncFileIds()
    for (const item of items.value) {
      if (item.fileId === null) {
        continue
      }
      try {
        const blob = await fetchFileBlob(item.fileId, true)
        if (revisionId !== revision) {
          blob.dispose()
          return
        }
        disposers.set(item.key, blob.dispose)
        urls.value = { ...urls.value, [item.key]: blob.url }
      } catch {
        // 单张历史图片加载失败只影响缩略图，不影响继续编辑
      }
    }
  },
  { immediate: true }
)

onBeforeUnmount(releaseAll)

function releaseAll(): void {
  disposers.forEach((dispose) => dispose())
  disposers.clear()
  urls.value = {}
}

function syncFileIds(): void {
  emit(
    'update:fileIds',
    items.value
      .filter((item) => item.status === 'done' && item.fileId !== null)
      .map((item) => item.fileId as number)
  )
  emit('update:busy', items.value.some((item) => item.status === 'uploading'))
}

function beforeUpload(rawFile: unknown): boolean {
  const file = rawFile as File
  if (items.value.length >= props.max) {
    ElMessage.warning(`一次最多上传 ${props.max} 张${props.label}`)
    return false
  }
  if (!isImageExtension(file.name)) {
    ElMessage.warning('只支持 jpg / jpeg / png / webp / gif 图片')
    return false
  }
  if (file.size > props.maxSizeMb * 1024 * 1024) {
    ElMessage.warning(`单张图片不能超过 ${props.maxSizeMb} MB`)
    return false
  }
  return true
}

async function handleUpload(options: unknown): Promise<void> {
  const { file } = options as { file: File }
  const item: UploadItem = {
    key: `local-${++keySeed}`,
    fileId: null,
    name: file.name,
    sizeBytes: file.size,
    progress: 0,
    status: 'uploading',
    raw: file
  }
  items.value = [...items.value, item]
  const objectUrl = URL.createObjectURL(file)
  urls.value = { ...urls.value, [item.key]: objectUrl }
  disposers.set(item.key, () => URL.revokeObjectURL(objectUrl))
  syncFileIds()
  await performUpload(item)
}

async function performUpload(item: UploadItem): Promise<void> {
  const raw = item.raw
  if (!raw) {
    return
  }
  update(item.key, { status: 'uploading', progress: 0, message: undefined })
  syncFileIds()
  try {
    const asset = await uploadFile(raw, props.purpose, (percent) => {
      update(item.key, { progress: percent })
    })
    update(item.key, { status: 'done', progress: 100, fileId: asset.id })
    emit('uploaded', asset.id)
    if (props.autoClear) {
      removeItem(item.key)
    }
  } catch (error) {
    update(item.key, {
      status: 'error',
      message: error instanceof Error && error.message ? error.message : '上传失败'
    })
  } finally {
    syncFileIds()
  }
}

async function retry(item: UploadItem): Promise<void> {
  await performUpload(item)
}

function update(key: string, patch: Partial<UploadItem>): void {
  items.value = items.value.map((item) => (item.key === key ? { ...item, ...patch } : item))
}

function removeItem(key: string): void {
  const dispose = disposers.get(key)
  if (dispose) {
    dispose()
    disposers.delete(key)
  }
  const nextUrls = { ...urls.value }
  delete nextUrls[key]
  urls.value = nextUrls
  items.value = items.value.filter((entry) => entry.key !== key)
  syncFileIds()
}

function move(index: number, offset: number): void {
  const target = index + offset
  if (target < 0 || target >= items.value.length) {
    return
  }
  const next = items.value.slice()
  const [moved] = next.splice(index, 1)
  next.splice(target, 0, moved)
  items.value = next
  syncFileIds()
}
</script>

<template>
  <div class="media-uploader">
    <div v-if="items.length > 0" class="media-uploader-strip">
      <figure v-for="(item, index) in items" :key="item.key" class="media-uploader-tile">
        <img v-if="urls[item.key]" :src="urls[item.key]" :alt="item.name" />
        <span v-else class="media-uploader-tile-empty" />
        <figcaption class="media-uploader-tile-foot">
          <span class="media-uploader-order">{{ index + 1 }}</span>
          <span
            class="media-uploader-name"
            :title="`${item.name} · ${formatFileSize(item.sizeBytes)}`"
          >
            {{ item.name }}
          </span>
        </figcaption>
        <div class="media-uploader-tile-mask">
          <span v-if="item.status === 'uploading'" class="media-uploader-progress">
            {{ item.progress }}%
          </span>
          <el-button v-else-if="item.status === 'error'" link size="small" @click="retry(item)">
            重试
          </el-button>
        </div>
        <div class="media-uploader-tile-tools">
          <button type="button" title="前移" :disabled="index === 0" @click="move(index, -1)">
            ←
          </button>
          <button
            type="button"
            title="后移"
            :disabled="index === items.length - 1"
            @click="move(index, 1)"
          >
            →
          </button>
          <button type="button" title="移除" @click="removeItem(item.key)">×</button>
        </div>
        <span v-if="item.status === 'error'" class="media-uploader-error" :title="item.message">
          {{ item.message }}
        </span>
      </figure>
    </div>

    <el-upload
      v-if="items.length < max"
      class="media-uploader-drop"
      drag
      multiple
      :accept="accept"
      :show-file-list="false"
      :before-upload="beforeUpload"
      :http-request="handleUpload"
    >
      <div class="media-uploader-hint">
        把{{ label }}拖到这里，或<em>点击选择</em>
      </div>
      <div class="media-uploader-note">
        已选 {{ items.length }} / {{ max }} 张，支持 jpg / png / webp / gif，单张不超过
        {{ maxSizeMb }} MB
      </div>
    </el-upload>
    <div v-else class="media-uploader-note media-uploader-full">
      已达到 {{ max }} 张上限，移除一张后才能继续添加。
    </div>
  </div>
</template>

<style scoped>
.media-uploader {
  display: grid;
  gap: 10px;
}

.media-uploader-strip {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(112px, 1fr));
  gap: 8px;
}

.media-uploader-tile {
  position: relative;
  margin: 0;
  overflow: hidden;
  background: var(--paper-deep);
  border: 1px solid var(--rule);
  border-radius: 4px;
}

.media-uploader-tile img,
.media-uploader-tile-empty {
  display: block;
  width: 100%;
  height: 84px;
  object-fit: cover;
}

.media-uploader-tile-empty {
  background: var(--paper-deep);
}

.media-uploader-tile-foot {
  display: flex;
  gap: 4px;
  align-items: center;
  padding: 4px 6px;
  font-size: 11px;
}

.media-uploader-order {
  flex: 0 0 auto;
  padding: 0 4px;
  color: var(--white);
  background: var(--jade);
  border-radius: 3px;
}

.media-uploader-name {
  overflow: hidden;
  color: var(--ink-soft);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.media-uploader-tile-mask {
  position: absolute;
  top: 0;
  left: 0;
  display: grid;
  width: 100%;
  height: 84px;
  place-items: center;
  color: var(--white);
  background: rgba(23, 33, 43, 0.42);
}

.media-uploader-tile-mask:empty {
  display: none;
}

.media-uploader-progress {
  font-size: 13px;
  letter-spacing: 0.04em;
}

.media-uploader-tile-tools {
  position: absolute;
  top: 4px;
  right: 4px;
  display: flex;
  gap: 2px;
}

.media-uploader-tile-tools button {
  width: 20px;
  height: 20px;
  padding: 0;
  color: var(--ink);
  line-height: 1;
  background: rgba(255, 255, 255, 0.86);
  border: none;
  border-radius: 3px;
  cursor: pointer;
}

.media-uploader-tile-tools button:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.media-uploader-error {
  display: block;
  padding: 0 6px 6px;
  overflow: hidden;
  color: var(--cinnabar);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.media-uploader-drop :deep(.el-upload) {
  width: 100%;
}

.media-uploader-drop :deep(.el-upload-dragger) {
  padding: 14px;
  background: var(--white);
  border: 1px dashed var(--rule-strong);
  border-radius: 4px;
}

.media-uploader-drop :deep(.el-upload-dragger:hover) {
  border-color: var(--jade);
}

.media-uploader-hint {
  color: var(--ink);
  font-size: 13px;
}

.media-uploader-hint em {
  color: var(--jade);
  font-style: normal;
}

.media-uploader-note {
  margin-top: 4px;
  color: var(--ink-soft);
  font-size: 12px;
}

.media-uploader-full {
  margin: 0;
}
</style>

