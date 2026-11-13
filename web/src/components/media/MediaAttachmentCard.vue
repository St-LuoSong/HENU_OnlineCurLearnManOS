<script setup lang="ts">
import { computed } from 'vue'

import { formatFileSize, type MediaItem } from '@/api/files'
import { formatDateTime } from '@/utils/date'

const props = withDefaults(
  defineProps<{
    item: MediaItem
    url?: string
    failed?: boolean
    removable?: boolean
  }>(),
  {
    url: '',
    failed: false,
    removable: false
  }
)

const emit = defineEmits<{
  (event: 'preview'): void
  (event: 'download'): void
  (event: 'remove'): void
}>()

const sizeText = computed(() =>
  typeof props.item.sizeBytes === 'number' ? formatFileSize(props.item.sizeBytes) : ''
)

const metaText = computed(() =>
  [sizeText.value, props.item.uploadedByName, formatDateTime(props.item.createdAt, '')]
    .filter((part) => part && part.length > 0)
    .join(' · ')
)
</script>

<template>
  <figure class="media-card">
    <button type="button" class="media-card-thumb" :title="item.originalName" @click="emit('preview')">
      <img v-if="url" :src="url" :alt="item.originalName" />
      <span v-else-if="failed" class="media-card-fallback">图片已失效</span>
      <span v-else class="media-card-skeleton" aria-label="图片加载中" />
      <span class="media-card-zoom">点击预览</span>
    </button>
    <figcaption class="media-card-body">
      <div class="media-card-name" :title="item.originalName">{{ item.originalName }}</div>
      <div v-if="metaText" class="media-card-meta">{{ metaText }}</div>
      <div class="media-card-actions">
        <el-button link type="primary" size="small" @click="emit('preview')">预览</el-button>
        <el-button link type="primary" size="small" @click="emit('download')">下载</el-button>
        <el-button
          v-if="removable"
          link
          type="danger"
          size="small"
          @click="emit('remove')"
        >
          删除
        </el-button>
      </div>
    </figcaption>
  </figure>
</template>

<style scoped>
.media-card {
  display: flex;
  flex-direction: column;
  margin: 0;
  overflow: hidden;
  background: var(--white);
  border: 1px solid var(--rule);
  border-radius: 4px;
}

.media-card-thumb {
  position: relative;
  display: block;
  width: 100%;
  aspect-ratio: 4 / 3;
  padding: 0;
  overflow: hidden;
  background: var(--paper-deep);
  border: none;
  cursor: zoom-in;
}

.media-card-thumb img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.25s ease;
}

.media-card-thumb:hover img {
  transform: scale(1.04);
}

.media-card-zoom {
  position: absolute;
  inset: auto 0 0 0;
  padding: 4px 6px;
  color: var(--white);
  font-size: 11px;
  text-align: center;
  background: rgba(23, 33, 43, 0.5);
  opacity: 0;
  transition: opacity 0.2s ease;
}

.media-card-thumb:hover .media-card-zoom,
.media-card-thumb:focus-visible .media-card-zoom {
  opacity: 1;
}

.media-card-skeleton {
  display: block;
  width: 100%;
  height: 100%;
  background: linear-gradient(100deg, var(--paper-deep) 30%, #f4f6f1 50%, var(--paper-deep) 70%);
  background-size: 200% 100%;
  animation: media-card-shimmer 1.4s ease-in-out infinite;
}

.media-card-fallback {
  display: grid;
  width: 100%;
  height: 100%;
  place-items: center;
  color: var(--ink-soft);
  font-size: 12px;
}

.media-card-body {
  display: grid;
  gap: 4px;
  padding: 8px 10px 4px;
}

.media-card-name {
  overflow: hidden;
  color: var(--ink);
  font-size: 13px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.media-card-meta {
  overflow: hidden;
  color: var(--ink-soft);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.media-card-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 2px 8px;
}

@keyframes media-card-shimmer {
  from {
    background-position: 200% 0;
  }

  to {
    background-position: -40% 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .media-card-skeleton {
    animation: none;
  }

  .media-card-thumb img {
    transition: none;
  }
}
</style>

