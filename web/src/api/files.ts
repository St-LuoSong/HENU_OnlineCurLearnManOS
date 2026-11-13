import http from '@/api/http'
import type { ApiEnvelope, PageResult } from '@/api/types'

/** 与后端 app.file.allowed-extensions 保持一致。 */
export const FILE_ACCEPT = '.xls,.xlsx,.docx,.pdf,.md,.txt'
export const FILE_MAX_SIZE_MB = 20

/** 论坛与班级图片：与后端 app.file.image-allowed-extensions 保持一致。 */
export const IMAGE_ACCEPT = '.jpg,.jpeg,.png,.webp,.gif'
export const IMAGE_MAX_SIZE_MB = 5
export const IMAGE_EXTENSIONS = ['jpg', 'jpeg', 'png', 'webp', 'gif']

export type FilePurpose =
  | 'PLATFORM_RESOURCE'
  | 'CLASS_RESOURCE'
  | 'SUBMISSION'
  | 'FORUM_IMAGE'
  | 'CLASS_RESOURCE_IMAGE'
  | 'SUBMISSION_IMAGE'

/**
 * 图片网格 / 图片卡片的通用条目。
 * 论坛媒体（ForumMedia）与文件附件（FileAttachment）都与之结构兼容，
 * 因此九宫格、图片查看器与上传组件可以在论坛和云班级之间复用。
 */
export interface MediaItem {
  id?: number | null
  fileId: number
  originalName: string
  mimeType?: string
  sizeBytes?: number
  sortOrder?: number | null
  canDelete?: boolean
  uploadedByName?: string
  createdAt?: string | null
}

export interface FileAsset {
  id: number
  originalName: string
  extension: string
  mimeType: string
  sizeBytes: number
  sha256: string
  purpose: string
  status: string
  ownerId: number
  ownerName: string
  downloadCount: number
  lastDownloadedAt?: string | null
  createdAt?: string | null
  previewable: boolean
  canDelete: boolean
}

export interface FileAttachment {
  id: number
  fileId: number
  originalName: string
  extension: string
  mimeType: string
  sizeBytes: number
  sortOrder?: number | null
  versionNo?: number | null
  uploadedBy: number
  uploadedByName: string
  createdAt?: string | null
  canDelete: boolean
}

export interface SheetPreview {
  name: string
  rows: string[][]
  truncated: boolean
}

export type PreviewKind = 'PDF' | 'TEXT' | 'HTML' | 'SHEET'

export interface FilePreview {
  fileId: number
  originalName: string
  extension: string
  kind: PreviewKind
  contentUrl?: string | null
  text?: string | null
  html?: string | null
  sheets: SheetPreview[]
  totalSheets: number
  truncated: boolean
}

export async function uploadFile(
  file: File,
  purpose: FilePurpose,
  onProgress?: (percent: number) => void
): Promise<FileAsset> {
  const form = new FormData()
  form.append('file', file)
  const response = await http.post<ApiEnvelope<FileAsset>>('/files', form, {
    params: { purpose },
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress: (event) => {
      if (!onProgress || !event.total) {
        return
      }
      onProgress(Math.min(100, Math.round((event.loaded / event.total) * 100)))
    }
  })
  return response.data.data
}

export async function getFileAsset(fileId: number): Promise<FileAsset> {
  const response = await http.get<ApiEnvelope<FileAsset>>(`/files/${fileId}`)
  return response.data.data
}

export async function previewFile(fileId: number): Promise<FilePreview> {
  const response = await http.get<ApiEnvelope<FilePreview>>(`/files/${fileId}/preview`)
  return response.data.data
}

/**
 * 下载与内联预览都必须携带 JWT，不能直接用 <a href>，
 * 因此统一取回 Blob 后再由前端生成对象 URL。
 */
export async function fetchFileBlob(
  fileId: number,
  inline = false
): Promise<{ blob: Blob; dispose: () => void; url: string }> {
  const response = await http.get<Blob>(
    inline ? `/files/${fileId}/content` : `/files/${fileId}/download`,
    { responseType: 'blob' }
  )
  const url = URL.createObjectURL(response.data)
  return {
    blob: response.data,
    url,
    dispose: () => URL.revokeObjectURL(url)
  }
}

export async function downloadFile(fileId: number, originalName: string): Promise<void> {
  const { url, dispose } = await fetchFileBlob(fileId)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = originalName
  document.body.appendChild(anchor)
  anchor.click()
  document.body.removeChild(anchor)
  dispose()
}

export async function deleteFile(fileId: number): Promise<void> {
  await http.delete<ApiEnvelope<null>>(`/files/${fileId}`)
}

export async function listResourceAttachments(resourceId: number): Promise<FileAttachment[]> {
  const response = await http.get<ApiEnvelope<FileAttachment[]>>(
    `/resources/${resourceId}/attachments`
  )
  return response.data.data
}

export async function attachToResource(
  resourceId: number,
  fileId: number
): Promise<FileAttachment> {
  const response = await http.post<ApiEnvelope<FileAttachment>>(
    `/resources/${resourceId}/attachments`,
    { fileId }
  )
  return response.data.data
}

export async function detachResourceAttachment(
  resourceId: number,
  attachmentId: number
): Promise<void> {
  await http.delete(`/resources/${resourceId}/attachments/${attachmentId}`)
}

export async function listClassResourceAttachments(
  classResourceId: number
): Promise<FileAttachment[]> {
  const response = await http.get<ApiEnvelope<FileAttachment[]>>(
    `/cloud-classes/class-resources/${classResourceId}/attachments`
  )
  return response.data.data
}

export async function attachToClassResource(
  classResourceId: number,
  fileId: number
): Promise<FileAttachment> {
  const response = await http.post<ApiEnvelope<FileAttachment>>(
    `/cloud-classes/class-resources/${classResourceId}/attachments`,
    { fileId }
  )
  return response.data.data
}

export async function detachClassResourceAttachment(
  classResourceId: number,
  attachmentId: number
): Promise<void> {
  await http.delete(`/cloud-classes/class-resources/${classResourceId}/attachments/${attachmentId}`)
}

export async function listSubmissionAttachments(
  submissionId: number
): Promise<FileAttachment[]> {
  const response = await http.get<ApiEnvelope<FileAttachment[]>>(
    `/cloud-classes/submissions/${submissionId}/attachments`
  )
  return response.data.data
}

export async function attachToSubmission(
  submissionId: number,
  fileId: number
): Promise<FileAttachment> {
  const response = await http.post<ApiEnvelope<FileAttachment>>(
    `/cloud-classes/submissions/${submissionId}/attachments`,
    { fileId }
  )
  return response.data.data
}

export async function detachSubmissionAttachment(
  submissionId: number,
  attachmentId: number
): Promise<void> {
  await http.delete(`/cloud-classes/submissions/${submissionId}/attachments/${attachmentId}`)
}

export async function listAdminFiles(params: {
  page?: number
  size?: number
  orphan?: boolean
  ownerId?: number
  purpose?: string
}): Promise<PageResult<FileAsset>> {
  const response = await http.get<ApiEnvelope<PageResult<FileAsset>>>('/admin/files', { params })
  return response.data.data
}

export async function purgeOrphanFiles(): Promise<number> {
  const response = await http.post<ApiEnvelope<number>>('/admin/files/orphans/purge')
  return response.data.data
}

export function formatFileSize(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes <= 0) {
    return '0 B'
  }
  if (bytes < 1024) {
    return `${bytes} B`
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`
  }
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

export function previewableExtension(extension: string): boolean {
  return ['pdf', 'txt', 'md', 'xls', 'xlsx', 'docx'].includes(extension.toLowerCase())
}

/** 扩展名是否属于图片白名单；是否真的能解码仍由服务端魔数与像素校验决定。 */
export function isImageExtension(fileName: string): boolean {
  const extension = fileName.split('.').pop()?.toLowerCase() ?? ''
  return IMAGE_EXTENSIONS.includes(extension)
}

/** 小写扩展名，用于图片卡片与上传校验提示。 */
export function extensionOf(fileName: string): string {
  return fileName.split('.').pop()?.toLowerCase() ?? ''
}
