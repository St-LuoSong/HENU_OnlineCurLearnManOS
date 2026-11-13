import { onBeforeUnmount, ref, watch, type Ref } from 'vue'

import { fetchFileBlob, type MediaItem } from '@/api/files'

export interface MediaBlobs {
  urls: Ref<Record<number, string>>
  failed: Ref<number[]>
  loading: Ref<number[]>
  release: () => void
}

/**
 * 按 fileId 逐个取回带 JWT 的图片内容并生成对象 URL。
 *
 * <p>对象 URL 必须显式回收：论坛长列表翻页、云班级反复打开作业都会持续泄漏内存，
 * 因此这里统一在来源变化与组件卸载时释放。</p>
 */
export function useMediaBlobs(source: () => MediaItem[]): MediaBlobs {
  const urls = ref<Record<number, string>>({})
  const failed = ref<number[]>([])
  const loading = ref<number[]>([])

  let disposers: Array<() => void> = []
  let revision = 0

  function release(): void {
    disposers.forEach((dispose) => dispose())
    disposers = []
    urls.value = {}
    failed.value = []
    loading.value = []
  }

  watch(
    source,
    async (list) => {
      release()
      const current = ++revision
      loading.value = list.map((item) => item.fileId)
      for (const item of list) {
        try {
          const blob = await fetchFileBlob(item.fileId, true)
          if (current !== revision) {
            blob.dispose()
            return
          }
          disposers.push(blob.dispose)
          urls.value = { ...urls.value, [item.fileId]: blob.url }
        } catch {
          if (current !== revision) {
            return
          }
          failed.value = [...failed.value, item.fileId]
        } finally {
          if (current === revision) {
            loading.value = loading.value.filter((id) => id !== item.fileId)
          }
        }
      }
    },
    { immediate: true }
  )

  onBeforeUnmount(release)

  return { urls, failed, loading, release }
}

