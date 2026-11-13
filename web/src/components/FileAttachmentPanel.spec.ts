import { flushPromises, mount } from '@vue/test-utils'
import { ElMessage } from 'element-plus'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { formatFileSize, uploadFile, downloadFile, type FileAttachment } from '@/api/files'

vi.mock('@/api/files', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/files')>()
  return {
    ...actual,
    uploadFile: vi.fn(),
    downloadFile: vi.fn()
  }
})

import FileAttachmentPanel from '@/components/FileAttachmentPanel.vue'

const elUploadStub = {
  props: {
    httpRequest: { type: Function, default: null },
    beforeUpload: { type: Function, default: null },
    accept: { type: String, default: '' },
    disabled: { type: Boolean, default: false }
  },
  template: `
    <div class="stub-upload">
      <button
        type="button"
        class="stub-upload-button"
        @click="httpRequest && httpRequest({ file: { name: 'report.pdf', size: 4 } })"
      >
        <slot />
      </button>
    </div>
  `
}

const stubs = {
  'el-button': { template: '<button type="button"><slot /></button>' },
  'el-progress': { template: '<div class="stub-progress" />' },
  'el-alert': { template: '<div class="stub-alert" />' },
  'el-tabs': { template: '<div><slot /></div>' },
  'el-tab-pane': { template: '<div><slot /></div>' },
  'el-upload': elUploadStub,
  'el-dialog': {
    props: { modelValue: { type: Boolean, default: false } },
    template: '<div v-if="modelValue"><slot /></div>'
  }
}

function attachment(overrides: Partial<FileAttachment> = {}): FileAttachment {
  return {
    id: 3,
    fileId: 9,
    originalName: '实验报告.pdf',
    extension: 'pdf',
    mimeType: 'application/pdf',
    sizeBytes: 2048,
    uploadedBy: 31,
    uploadedByName: '张三',
    canDelete: true,
    ...overrides
  }
}

function mountPanel(props: Record<string, unknown> = {}) {
  return mount(FileAttachmentPanel, {
    props: {
      attachments: [attachment()],
      uploadable: true,
      purpose: 'SUBMISSION',
      ...props
    },
    global: {
      stubs,
      directives: { loading: {} }
    }
  })
}

describe('FileAttachmentPanel 附件面板', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.spyOn(ElMessage, 'error').mockImplementation(() => undefined as never)
    vi.spyOn(ElMessage, 'warning').mockImplementation(() => undefined as never)
    vi.spyOn(ElMessage, 'success').mockImplementation(() => undefined as never)
  })

  it('展示附件名称、大小、上传者与版本号', () => {
    const wrapper = mountPanel({
      attachments: [attachment({ versionNo: 2 })]
    })

    const text = wrapper.text()
    expect(text).toContain('实验报告.pdf')
    expect(text).toContain(formatFileSize(2048))
    expect(text).toContain('张三')
    expect(text).toContain('第 2 版')
  })

  it('可预览文件提供在线预览入口，不可删除时隐藏删除按钮', () => {
    const wrapper = mountPanel({
      uploadable: false,
      attachments: [attachment({ canDelete: false })]
    })

    const labels = wrapper.findAll('button').map((button) => button.text())
    expect(labels.some((label) => label.includes('在线预览'))).toBe(true)
    expect(labels.some((label) => label.includes('下载'))).toBe(true)
    expect(labels.some((label) => label.includes('删除'))).toBe(false)
  })

  it('上传成功后抛出 uploaded 事件并带上文件 ID', async () => {
    vi.mocked(uploadFile).mockResolvedValue({
      id: 12,
      originalName: 'report.pdf',
      extension: 'pdf',
      mimeType: 'application/pdf',
      sizeBytes: 4,
      sha256: 'hash',
      purpose: 'SUBMISSION',
      status: 'ACTIVE',
      ownerId: 31,
      ownerName: '张三',
      downloadCount: 0,
      previewable: true,
      canDelete: true
    })
    const wrapper = mountPanel()

    await wrapper.find('button.stub-upload-button').trigger('click')
    await flushPromises()

    expect(vi.mocked(uploadFile)).toHaveBeenCalledTimes(1)
    expect(vi.mocked(uploadFile).mock.calls[0][1]).toBe('SUBMISSION')
    expect(wrapper.emitted('uploaded')).toContainEqual([12])
  })

  it('上传失败时提示错误且不抛出事件', async () => {
    vi.mocked(uploadFile).mockRejectedValue(new Error('文件不能超过 20 MB'))
    const wrapper = mountPanel()

    await wrapper.find('button.stub-upload-button').trigger('click')
    await flushPromises()

    expect(ElMessage.error).toHaveBeenCalled()
    expect(wrapper.emitted('uploaded')).toBeUndefined()
  })

  it('点击删除与下载分别抛出事件或调用下载接口', async () => {
    vi.mocked(downloadFile).mockResolvedValue(undefined)
    const wrapper = mountPanel()

    const removeButton = wrapper.findAll('button').find((button) => button.text().includes('删除'))
    await removeButton!.trigger('click')
    expect(wrapper.emitted('remove')).toContainEqual([attachment()])

    const downloadButton = wrapper
      .findAll('button')
      .find((button) => button.text().includes('下载'))
    await downloadButton!.trigger('click')
    await flushPromises()
    expect(vi.mocked(downloadFile)).toHaveBeenCalledWith(9, '实验报告.pdf')
  })
})
