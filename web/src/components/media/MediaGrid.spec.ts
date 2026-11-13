import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const files = vi.hoisted(() => ({
  fetchFileBlob: vi.fn()
}))

vi.mock('@/api/files', () => files)

import MediaGrid from '@/components/media/MediaGrid.vue'

function media(count: number) {
  return Array.from({ length: count }, (_, index) => ({
    id: index + 1,
    fileId: 700 + index,
    originalName: `photo-${index + 1}.png`,
    mimeType: 'image/png',
    sizeBytes: 2048,
    sortOrder: index,
    canDelete: true
  }))
}

function mountGrid(count: number) {
  return mount(MediaGrid, { props: { items: media(count) } })
}

describe('MediaGrid 九宫格布局', () => {
  beforeEach(() => {
    files.fetchFileBlob.mockReset()
    files.fetchFileBlob.mockResolvedValue({
      blob: new Blob(),
      url: 'blob:stub',
      dispose: vi.fn()
    })
  })

  it('1 张图片走单图布局', async () => {
    const wrapper = mountGrid(1)
    await flushPromises()

    expect(wrapper.classes()).toContain('media-grid--single')
    expect(wrapper.findAll('.media-grid-cell')).toHaveLength(1)
  })

  it('4 张图片走九宫格布局', async () => {
    const wrapper = mountGrid(4)
    await flushPromises()

    expect(wrapper.classes()).toContain('media-grid--grid')
    expect(wrapper.findAll('.media-grid-cell')).toHaveLength(4)
  })

  it('9 张图片全部渲染并带顺序角标', async () => {
    const wrapper = mountGrid(9)
    await flushPromises()

    expect(wrapper.findAll('.media-grid-cell')).toHaveLength(9)
    expect(wrapper.findAll('.media-grid-index')).toHaveLength(9)
    expect(wrapper.findAll('.media-grid-index')[8].text()).toBe('9')
  })

  it('点击第 N 张图抛出对应索引的预览事件', async () => {
    const wrapper = mountGrid(4)
    await flushPromises()

    await wrapper.findAll('.media-grid-cell')[2].trigger('click')

    expect(wrapper.emitted('preview')).toEqual([[2]])
  })

  it('没有图片时不渲染任何格子', () => {
    const wrapper = mount(MediaGrid, { props: { items: [] } })

    expect(wrapper.find('.media-grid').exists()).toBe(false)
  })
})

