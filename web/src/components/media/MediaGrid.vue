<script setup lang="ts">
import { computed } from 'vue'

import type { MediaItem } from '@/api/files'
import { useMediaBlobs } from '@/components/media/useMediaBlobs'

const props = defineProps<{
  items: MediaItem[]
}>()

const emit = defineEmits<{
  (event: 'preview', index: number): void
}>()

const { urls, failed, loading } = useMediaBlobs(() => props.items)

const layout = computed(() => {
  const count = props.items.length
  if (count === 1) {
    return 'single'
  }
  if (count === 2) {
    return 'duo'
  }
  if (count === 3) {
    return 'trio'
  }
  return 'grid'
})
</script>

<template>
  <div v-if="items.length > 0" class="media-grid" :class="`media-grid--${layout}`">
    <button
      v-for="(item, index) in items"
      :key="item.id ?? item.fileId"
      type="button"
      class="media-grid-cell"
      :title="item.originalName"
      @click.stop="emit('preview', index)"
    >
      <img
        v-if="urls[item.fileId]"
        :src="urls[item.fileId]"
        :alt="item.originalName"
        loading="lazy"
      />
      <span v-else-if="failed.includes(item.fileId)" class="media-grid-fallback">图片已失效</span>
      <span
        v-else
        class="media-grid-skeleton"
        :aria-label="loading.includes(item.fileId) ? '图片加载中' : '图片待加载'"
      />
      <span v-if="items.length > 3" class="media-grid-index">{{ index + 1 }}</span>
    </button>
  </div>
</template>

<style scoped>
.media-grid {
  display: grid;
  gap: 6px;
  margin-top: 12px;
  max-width: 560px;
}

.media-grid--single {
  grid-template-columns: minmax(0, 1fr);
}

.media-grid--duo {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.media-grid--trio,
.media-grid--grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.media-grid-cell {
  position: relative;
  display: block;
  padding: 0;
  overflow: hidden;
  background: var(--paper-deep);
  border: 1px solid var(--rule);
  border-radius: 4px;
  cursor: zoom-in;
}

.media-grid--single .media-grid-cell,
.media-grid--duo .media-grid-cell {
  aspect-ratio: 4 / 3;
}

.media-grid--trio .media-grid-cell,
.media-grid--grid .media-grid-cell {
  aspect-ratio: 1 / 1;
}

.media-grid-cell img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.25s ease;
}

.media-grid-cell:hover img {
  transform: scale(1.03);
}

.media-grid-cell:focus-visible {
  outline: 2px solid var(--jade);
  outline-offset: 2px;
}

.media-grid-skeleton {
  display: block;
  width: 100%;
  height: 100%;
  background: linear-gradient(100deg, var(--paper-deep) 30%, #f4f6f1 50%, var(--paper-deep) 70%);
  background-size: 200% 100%;
  animation: media-grid-shimmer 1.4s ease-in-out infinite;
}

.media-grid-fallback {
  display: grid;
  width: 100%;
  height: 100%;
  place-items: center;
  color: var(--ink-soft);
  font-size: 12px;
}

.media-grid-index {
  position: absolute;
  right: 6px;
  bottom: 6px;
  padding: 0 5px;
  color: var(--white);
  font-size: 11px;
  line-height: 16px;
  background: rgba(23, 33, 43, 0.55);
  border-radius: 3px;
}

@keyframes media-grid-shimmer {
  from {
    background-position: 200% 0;
  }

  to {
    background-position: -40% 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .media-grid-skeleton {
    animation: none;
  }

  .media-grid-cell img {
    transition: none;
  }
}

@media (max-width: 760px) {
  .media-grid--trio,
  .media-grid--grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>

