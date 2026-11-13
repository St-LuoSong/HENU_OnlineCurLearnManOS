<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'

import { downloadFile, fetchFileBlob, type MediaItem } from '@/api/files'

const props = defineProps<{
  modelValue: boolean
  items: MediaItem[]
  index: number
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'update:index', value: number): void
}>()

const urls = ref<Record<number, string>>({})
const loading = ref(false)

let disposers: Array<() => void> = []
let revision = 0

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value)
})

const current = computed(() => props.items[props.index] ?? null)
const total = computed(() => props.items.length)

watch(
  () => [props.modelValue, props.items] as const,
  async ([open, list]) => {
    if (!open) {
      release()
      return
    }
    await loadAll(list)
  },
  { immediate: true }
)

onMounted(() => window.addEventListener('keydown', handleKeydown))
onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleKeydown)
  release()
})

async function loadAll(list: MediaItem[]): Promise<void> {
  release()
  const revisionId = ++revision
  loading.value = true
  try {
    for (const item of list) {
      try {
        const blob = await fetchFileBlob(item.fileId, true)
        if (revisionId !== revision) {
          blob.dispose()
          return
        }
        disposers.push(blob.dispose)
        urls.value = { ...urls.value, [item.fileId]: blob.url }
      } catch {
        // 单张图片加载失败不影响其余图片继续浏览
      }
    }
  } finally {
    if (revisionId === revision) {
      loading.value = false
    }
  }
}

function release(): void {
  disposers.forEach((dispose) => dispose())
  disposers = []
  urls.value = {}
}

function step(offset: number): void {
  if (total.value === 0) {
    return
  }
  emit('update:index', (props.index + offset + total.value) % total.value)
}

function handleKeydown(event: KeyboardEvent): void {
  if (!props.modelValue) {
    return
  }
  if (event.key === 'ArrowLeft') {
    event.preventDefault()
    step(-1)
  } else if (event.key === 'ArrowRight') {
    event.preventDefault()
    step(1)
  }
}

async function download(): Promise<void> {
  const item = current.value
  if (!item) {
    return
  }
  try {
    await downloadFile(item.fileId, item.originalName)
  } catch (error) {
    ElMessage.error(error instanceof Error && error.message ? error.message : '下载失败')
  }
}
</script>

<template>
  <el-dialog
    v-model="visible"
    class="media-viewer-dialog"
    width="900px"
    append-to-body
    :close-on-click-modal="true"
    :show-close="false"
  >
    <div class="media-viewer">
      <header class="media-viewer-head">
        <div class="media-viewer-title">
          {{ current?.originalName ?? '图片预览' }}
          <span v-if="total > 1" class="media-viewer-counter">{{ index + 1 }} / {{ total }}</span>
        </div>
        <div class="media-viewer-tools">
          <el-button link type="primary" @click="download">下载原图</el-button>
          <el-button link @click="visible = false">关闭</el-button>
        </div>
      </header>

      <div v-loading="loading" class="media-viewer-stage">
        <img
          v-if="current && urls[current.fileId]"
          :src="urls[current.fileId]"
          :alt="current.originalName"
        />
        <div v-else-if="!loading" class="empty-ledger">图片已失效或无法读取。</div>
      </div>

      <div v-if="total > 1" class="media-viewer-nav">
        <el-button link type="primary" @click="step(-1)">上一张</el-button>
        <div class="media-viewer-strip">
          <button
            v-for="(item, itemIndex) in items"
            :key="item.id ?? item.fileId"
            type="button"
            class="media-viewer-thumb"
            :class="{ 'is-active': itemIndex === index }"
            @click="emit('update:index', itemIndex)"
          >
            <img v-if="urls[item.fileId]" :src="urls[item.fileId]" :alt="item.originalName" />
            <span v-else class="media-viewer-thumb-empty" />
          </button>
        </div>
        <el-button link type="primary" @click="step(1)">下一张</el-button>
      </div>
    </div>
  </el-dialog>
</template>

<style scoped>
.media-viewer {
  display: grid;
  gap: 12px;
}

.media-viewer-head {
  display: flex;
  gap: 12px;
  align-items: baseline;
  justify-content: space-between;
}

.media-viewer-title {
  color: var(--ink);
  font-family: var(--font-display);
  font-size: 15px;
  font-weight: 700;
  word-break: break-all;
}

.media-viewer-counter {
  margin-left: 8px;
  color: var(--ink-soft);
  font-family: "Noto Sans SC", "Microsoft YaHei", sans-serif;
  font-size: 12px;
  font-weight: 400;
}

.media-viewer-tools {
  display: flex;
  flex: 0 0 auto;
  gap: 4px;
}

.media-viewer-stage {
  display: grid;
  min-height: 320px;
  max-height: 62vh;
  place-items: center;
  overflow: hidden;
  background: var(--ink);
  border-radius: 4px;
}

.media-viewer-stage img {
  display: block;
  max-width: 100%;
  max-height: 62vh;
  object-fit: contain;
}

.media-viewer-nav {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
}

.media-viewer-strip {
  display: flex;
  gap: 6px;
  justify-content: center;
  overflow-x: auto;
  padding: 2px;
}

.media-viewer-thumb {
  flex: 0 0 auto;
  width: 54px;
  height: 54px;
  padding: 0;
  overflow: hidden;
  background: var(--paper-deep);
  border: 1px solid var(--rule);
  border-radius: 4px;
  cursor: pointer;
  opacity: 0.62;
}

.media-viewer-thumb.is-active {
  border-color: var(--jade);
  opacity: 1;
}

.media-viewer-thumb img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.media-viewer-thumb-empty {
  display: block;
  width: 100%;
  height: 100%;
}
</style>

