import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const files = vi.hoisted(() => ({
  fetchFileBlob: vi.fn()
}))

vi.mock('@/api/files', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/files')>()
  return { ...actual, fetchFileBlob: files.fetchFileBlob }
})

import ImagePreview from '@/components/media/ImagePreview.vue'
import MediaAttachmentCard from '@/components/media/MediaAttachmentCard.vue'
import MediaGallery from '@/components/media/MediaGallery.vue'

function media(count: number) {
  return Array.from({ length: count }, (_, index) => ({
    id: index + 1,
    fileId: 800 + index,
    originalName: `class-${index + 1}.png`,
    mimeType: 'image/png',
    sizeBytes: 4096,
    sortOrder: index,
    uploadedByName: '王老师',
    createdAt: '2026-09-21T09:00:00'
  }))
}

function mountGallery(
  items: ReturnType<typeof media>,
  props: Record<string, unknown> = {}
) {
  return mount(MediaGallery, {
    props: { items, ...props },
    global: {
      stubs: {
        'el-button': { template: '<button type="button"><slot /></button>' }
      },
      directives: { loading: {} }
    }
  })
}

describe('MediaGallery 图片卡片画廊', () => {
  beforeEach(() => {
    files.fetchFileBlob.mockReset()
    files.fetchFileBlob.mockResolvedValue({
      blob: new Blob(),
      url: 'blob:stub',
      dispose: vi.fn()
    })
  })

  it('没有图片时显示空态且不渲染卡片', () => {
    const wrapper = mountGallery([], { emptyText: '还没有上传图片。' })

    expect(wrapper.find('.empty-ledger').text()).toBe('还没有上传图片。')
    expect(wrapper.findAllComponents(MediaAttachmentCard)).toHaveLength(0)
  })

  it('每张图片渲染一张带文件名的卡片', async () => {
    const wrapper = mountGallery(media(3))
    await flushPromises()

    const cards = wrapper.findAllComponents(MediaAttachmentCard)
    expect(cards).toHaveLength(3)
    expect(cards[1].text()).toContain('class-2.png')
  })

  it('removable 透传到卡片，默认不可删除', async () => {
    const locked = mountGallery(media(1))
    const editable = mountGallery(media(1), { removable: true })
    await flushPromises()

    expect(locked.findComponent(MediaAttachmentCard).props('removable')).toBe(false)
    expect(editable.findComponent(MediaAttachmentCard).props('removable')).toBe(true)
  })

  it('卡片下载与删除事件带原始条目向上冒泡', async () => {
    const items = media(2)
    const wrapper = mountGallery(items, { removable: true })
    await flushPromises()

    const card = wrapper.findAllComponents(MediaAttachmentCard)[1]
    card.vm.$emit('download')
    card.vm.$emit('remove')

    expect(wrapper.emitted('download')).toEqual([[items[1]]])
    expect(wrapper.emitted('remove')).toEqual([[items[1]]])
  })

  it('点击缩略图打开图片查看器', async () => {
    const wrapper = mountGallery(media(2))
    await flushPromises()

    expect(wrapper.findComponent(ImagePreview).props('modelValue')).toBe(false)
    await wrapper.find('.media-card-thumb').trigger('click')

    expect(wrapper.findComponent(ImagePreview).props('modelValue')).toBe(true)
  })
})

