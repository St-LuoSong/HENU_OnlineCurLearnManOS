import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const files = vi.hoisted(() => ({
  fetchFileBlob: vi.fn(),
  uploadFile: vi.fn()
}))

vi.mock('@/api/files', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/files')>()
  return { ...actual, fetchFileBlob: files.fetchFileBlob, uploadFile: files.uploadFile }
})

import MediaUploader from '@/components/media/MediaUploader.vue'

const uploadStub = {
  name: 'ElUploadStub',
  props: ['beforeUpload', 'httpRequest', 'accept', 'showFileList', 'drag', 'multiple'],
  template: '<div class="stub-upload"><slot /></div>'
}

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

function mountUploader(count: number) {
  return mount(MediaUploader, {
    props: {
      fileIds: [],
      initialMedia: media(count),
      purpose: 'FORUM_IMAGE' as const
    },
    global: {
      stubs: {
        'el-upload': uploadStub,
        'el-button': { template: '<button type="button"><slot /></button>' }
      }
    }
  })
}

function pickFile(name: string, size = 1024): File {
  const file = new File(['x'], name, { type: 'image/png' })
  Object.defineProperty(file, 'size', { value: size })
  return file
}

function beforeUploadOf(wrapper: ReturnType<typeof mountUploader>): (file: File) => boolean {
  const upload = wrapper.findComponent({ name: 'ElUploadStub' })
  return upload.props('beforeUpload') as (file: File) => boolean
}

describe('MediaUploader 客户端上限', () => {
  beforeEach(() => {
    files.fetchFileBlob.mockReset()
    files.uploadFile.mockReset()
    files.fetchFileBlob.mockResolvedValue({
      blob: new Blob(),
      url: 'blob:stub',
      dispose: vi.fn()
    })
    files.uploadFile.mockResolvedValue({
      id: 900,
      originalName: 'photo.png',
      extension: 'png',
      mimeType: 'image/png',
      sizeBytes: 1024,
      sha256: 'stub',
      purpose: 'FORUM_IMAGE',
      status: 'ACTIVE',
      ownerId: 1,
      ownerName: '李同学',
      downloadCount: 0,
      previewable: false,
      canDelete: true
    })
    vi.spyOn(ElMessage, 'warning').mockImplementation(() => undefined as never)
  })

  it('已有 9 张时第 10 张被拒绝', async () => {
    const wrapper = mountUploader(8)
    const upload = wrapper.findComponent({ name: 'ElUploadStub' })
    const beforeUpload = upload.props('beforeUpload') as (file: File) => boolean
    const httpRequest = upload.props('httpRequest') as (options: unknown) => Promise<void>

    expect(beforeUpload(pickFile('photo-9.png'))).toBe(true)
    void httpRequest({ file: pickFile('photo-9.png') })
    await nextTick()

    const warning = vi.spyOn(ElMessage, 'warning')
    expect(beforeUpload(pickFile('photo-10.png'))).toBe(false)
    expect(warning).toHaveBeenCalledWith('一次最多上传 9 张图片')
  })

  it('未满 9 张时正常图片通过', () => {
    const wrapper = mountUploader(3)

    expect(beforeUploadOf(wrapper)(pickFile('photo.png'))).toBe(true)
  })

  it('非图片扩展名被拒绝', () => {
    const wrapper = mountUploader(0)
    const warning = vi.spyOn(ElMessage, 'warning')

    expect(beforeUploadOf(wrapper)(pickFile('report.pdf'))).toBe(false)
    expect(warning).toHaveBeenCalledWith('只支持 jpg / jpeg / png / webp / gif 图片')
  })

  it('超过 5 MB 的图片被拒绝', () => {
    const wrapper = mountUploader(0)
    const warning = vi.spyOn(ElMessage, 'warning')

    expect(beforeUploadOf(wrapper)(pickFile('huge.png', 6 * 1024 * 1024))).toBe(false)
    expect(warning).toHaveBeenCalledWith('单张图片不能超过 5 MB')
  })

  it('autoClear 模式下上传成功即把文件交给父组件并移出本地列表', async () => {
    const wrapper = mount(MediaUploader, {
      props: {
        fileIds: [],
        purpose: 'SUBMISSION_IMAGE' as const,
        autoClear: true,
        label: '作业图片'
      },
      global: {
        stubs: {
          'el-upload': uploadStub,
          'el-button': { template: '<button type="button"><slot /></button>' }
        }
      }
    })
    const upload = wrapper.findComponent({ name: 'ElUploadStub' })
    const httpRequest = upload.props('httpRequest') as (options: unknown) => Promise<void>

    await httpRequest({ file: pickFile('answer.png') })

    expect(wrapper.emitted('uploaded')).toEqual([[900]])
    expect(wrapper.findAll('.media-uploader-tile')).toHaveLength(0)
  })
})

