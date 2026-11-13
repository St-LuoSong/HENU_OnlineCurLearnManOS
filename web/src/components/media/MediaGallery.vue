<script setup lang="ts">
import { ref } from 'vue'

import type { MediaItem } from '@/api/files'
import ImagePreview from '@/components/media/ImagePreview.vue'
import MediaAttachmentCard from '@/components/media/MediaAttachmentCard.vue'
import { useMediaBlobs } from '@/components/media/useMediaBlobs'

const props = withDefaults(
  defineProps<{
    items: MediaItem[]
    removable?: boolean
    emptyText?: string
  }>(),
  {
    removable: false,
    emptyText: '暂无图片'
  }
)

const emit = defineEmits<{
  (event: 'download', item: MediaItem): void
  (event: 'remove', item: MediaItem): void
}>()

const { urls, failed } = useMediaBlobs(() => props.items)

const previewVisible = ref(false)
const previewIndex = ref(0)

function openPreview(index: number): void {
  previewIndex.value = index
  previewVisible.value = true
}
</script>

<template>
  <div class="media-gallery">
    <div v-if="items.length === 0" class="empty-ledger">{{ emptyText }}</div>
    <div v-else class="media-gallery-grid">
      <MediaAttachmentCard
        v-for="(item, index) in items"
        :key="item.id ?? item.fileId"
        :item="item"
        :url="urls[item.fileId]"
        :failed="failed.includes(item.fileId)"
        :removable="removable"
        @preview="openPreview(index)"
        @download="emit('download', item)"
        @remove="emit('remove', item)"
      />
    </div>

    <ImagePreview v-model="previewVisible" v-model:index="previewIndex" :items="items" />
  </div>
</template>

<style scoped>
.media-gallery-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(190px, 1fr));
  gap: 12px;
}

@media (max-width: 760px) {
  .media-gallery-grid {
    grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  }
}
</style>

