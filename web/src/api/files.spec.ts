import { beforeEach, describe, expect, it, vi } from 'vitest'

const httpMock = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  delete: vi.fn()
}))

vi.mock('@/api/http', () => ({ default: httpMock }))

import {
  downloadFile,
  fetchFileBlob,
  formatFileSize,
  previewableExtension,
  uploadFile,
  type FileAsset
} from '@/api/files'

const asset: FileAsset = {
  id: 9,
  originalName: '报告.pdf',
  extension: 'pdf',
  mimeType: 'application/pdf',
  sizeBytes: 2048,
  sha256: 'hash',
  purpose: 'SUBMISSION',
  status: 'ACTIVE',
  ownerId: 31,
  ownerName: '张三',
  downloadCount: 0,
  previewable: true,
  canDelete: true
}

describe('files API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    URL.createObjectURL = vi.fn(() => 'blob:file-9')
    URL.revokeObjectURL = vi.fn()
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined)
  })

  it('上传时按用途提交 multipart 并回报进度', async () => {
    const progress: number[] = []
    httpMock.post.mockImplementation(async (_url: string, _body: unknown, config: {
      params: Record<string, string>
      onUploadProgress: (event: { loaded: number; total: number }) => void
    }) => {
      expect(config.params).toEqual({ purpose: 'SUBMISSION' })
      config.onUploadProgress({ loaded: 512, total: 1024 })
      config.onUploadProgress({ loaded: 1024, total: 1024 })
      return { data: { data: asset } }
    })
    const file = { name: '报告.pdf', size: 2048 } as unknown as File

    const uploaded = await uploadFile(file, 'SUBMISSION', (percent) => progress.push(percent))

    expect(uploaded.id).toBe(9)
    expect(httpMock.post).toHaveBeenCalledWith(
      '/files',
      expect.anything(),
      expect.objectContaining({ headers: { 'Content-Type': 'multipart/form-data' } })
    )
    expect(progress).toEqual([50, 100])
  })

  it('downloadFile 取回 Blob 后触发下载并释放对象 URL', async () => {
    const blob = new Blob(['%PDF-1.7'])
    httpMock.get.mockResolvedValue({ data: blob })

    await downloadFile(9, '报告.pdf')

    expect(httpMock.get).toHaveBeenCalledWith('/files/9/download', { responseType: 'blob' })
    expect(HTMLAnchorElement.prototype.click).toHaveBeenCalledTimes(1)
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:file-9')
  })

  it('内联预览使用 content 接口并返回可释放的 URL', async () => {
    httpMock.get.mockResolvedValue({ data: new Blob(['%PDF']) })

    const inline = await fetchFileBlob(9, true)

    expect(httpMock.get).toHaveBeenCalledWith('/files/9/content', { responseType: 'blob' })
    expect(inline.url).toBe('blob:file-9')
    inline.dispose()
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:file-9')
  })

  it('文件大小与可预览扩展名的展示规则保持稳定', () => {
    expect(formatFileSize(512)).toBe('512 B')
    expect(formatFileSize(2048)).toBe('2.0 KB')
    expect(formatFileSize(3 * 1024 * 1024)).toBe('3.0 MB')
    expect(formatFileSize(0)).toBe('0 B')

    expect(previewableExtension('PDF')).toBe(true)
    expect(previewableExtension('docx')).toBe(true)
    expect(previewableExtension('zip')).toBe(false)
  })
})
