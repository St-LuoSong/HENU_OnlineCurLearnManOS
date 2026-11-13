import { flushPromises, mount } from '@vue/test-utils'
import { ElMessage } from 'element-plus'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({
  previewFile: vi.fn(),
  fetchFileBlob: vi.fn(),
  downloadFile: vi.fn()
}))

vi.mock('@/api/files', () => api)

import FilePreviewDialog from '@/components/FilePreviewDialog.vue'

const stubs = {
  'el-dialog': {
    props: { modelValue: { type: Boolean, default: false }, title: { type: String, default: '' } },
    emits: ['update:modelValue'],
    template: `
      <div v-if="modelValue" class="stub-dialog">
        <div class="stub-dialog-title">{{ title }}</div>
        <slot />
        <div class="stub-dialog-footer"><slot name="footer" /></div>
      </div>
    `
  },
  'el-button': { template: '<button type="button"><slot /></button>' },
  'el-alert': {
    props: { title: { type: String, default: '' } },
    template: '<div class="stub-alert">{{ title }}</div>'
  },
  'el-tabs': { template: '<div><slot /></div>' },
  'el-tab-pane': { template: '<div><slot /></div>' }
}

function mountDialog(fileId: number | null = 7) {
  return mount(FilePreviewDialog, {
    props: { modelValue: true, fileId, fileName: '报告.pdf' },
    global: {
      stubs,
      directives: { loading: {} }
    }
  })
}

describe('FilePreviewDialog 在线预览', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    api.fetchFileBlob.mockResolvedValue({
      blob: new Blob(['%PDF-1.7']),
      url: 'blob:preview-pdf',
      dispose: vi.fn()
    })
    vi.spyOn(ElMessage, 'error').mockImplementation(() => undefined as never)
  })

  it('文本文件以纯文本形式展示', async () => {
    api.previewFile.mockResolvedValue({
      fileId: 7,
      originalName: 'notes.txt',
      extension: 'txt',
      kind: 'TEXT',
      text: '第一行\n第二行',
      sheets: [],
      totalSheets: 0,
      truncated: false
    })
    const wrapper = mountDialog()
    await flushPromises()

    expect(api.previewFile).toHaveBeenCalledWith(7)
    expect(wrapper.find('.preview-text').text()).toContain('第二行')
    expect(wrapper.find('.preview-html').exists()).toBe(false)
  })

  it('Markdown 预览直接渲染服务端清洗后的 HTML', async () => {
    api.previewFile.mockResolvedValue({
      fileId: 7,
      originalName: 'guide.md',
      extension: 'md',
      kind: 'HTML',
      html: '<h1>标题</h1><p>&lt;script&gt;</p>',
      sheets: [],
      totalSheets: 0,
      truncated: false
    })
    const wrapper = mountDialog()
    await flushPromises()

    expect(wrapper.find('.preview-html').html()).toContain('&lt;script&gt;')
    expect(wrapper.find('script').exists()).toBe(false)
  })

  it('表格预览按工作表渲染单元格', async () => {
    api.previewFile.mockResolvedValue({
      fileId: 7,
      originalName: 'scores.xlsx',
      extension: 'xlsx',
      kind: 'SHEET',
      sheets: [
        { name: '成绩', rows: [['姓名', '分数'], ['张三', '90']], truncated: false }
      ],
      totalSheets: 1,
      truncated: false
    })
    const wrapper = mountDialog()
    await flushPromises()

    const cells = wrapper.findAll('.sheet-table td').map((cell) => cell.text())
    expect(cells).toEqual(['姓名', '分数', '张三', '90'])
  })

  it('PDF 预览通过 Blob 内联渲染并提示截断', async () => {
    api.previewFile.mockResolvedValue({
      fileId: 7,
      originalName: 'paper.pdf',
      extension: 'pdf',
      kind: 'PDF',
      contentUrl: '/api/v1/files/7/content',
      sheets: [],
      totalSheets: 0,
      truncated: true
    })
    const wrapper = mountDialog()
    await flushPromises()

    expect(api.fetchFileBlob).toHaveBeenCalledWith(7, true)
    expect(wrapper.find('iframe').attributes('src')).toBe('blob:preview-pdf')
    expect(wrapper.find('.stub-alert').text()).toContain('仅展示前部分')
  })

  it('下载按钮使用当前文件 ID 与原始文件名', async () => {
    api.previewFile.mockResolvedValue({
      fileId: 7,
      originalName: 'notes.txt',
      extension: 'txt',
      kind: 'TEXT',
      text: 'x',
      sheets: [],
      totalSheets: 0,
      truncated: false
    })
    api.downloadFile.mockResolvedValue(undefined)
    const wrapper = mountDialog()
    await flushPromises()

    const download = wrapper
      .findAll('button')
      .find((button) => button.text().includes('下载原文件'))
    await download!.trigger('click')
    await flushPromises()

    expect(api.downloadFile).toHaveBeenCalledWith(7, '报告.pdf')
  })

  it('预览失败时提示错误并关闭弹窗', async () => {
    api.previewFile.mockRejectedValue(new Error('该文件类型不支持在线预览'))
    const wrapper = mountDialog()
    await flushPromises()

    expect(ElMessage.error).toHaveBeenCalled()
    expect(wrapper.emitted('update:modelValue')).toContainEqual([false])
  })
})
