<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import {
  getResource,
  recordResourceView,
  updateResourceStatus,
  type LearningResource
} from '@/api/content'
import {
  attachToResource,
  detachResourceAttachment,
  listResourceAttachments,
  type FileAttachment
} from '@/api/files'
import FileAttachmentPanel from '@/components/FileAttachmentPanel.vue'
import { useAuthStore } from '@/stores/auth'
import { formatDateTime } from '@/utils/date'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const resourceId = Number(route.params.id)

const loading = ref(true)
const resource = ref<LearningResource | null>(null)
const attachments = ref<FileAttachment[]>([])
const attachmentLoading = ref(false)
const statusUpdating = ref(false)

const canManage = computed(() => auth.user?.roles.includes('ADMIN') === true)

onMounted(async () => {
  await load()
})

async function load(): Promise<void> {
  loading.value = true
  try {
    resource.value = await getResource(resourceId)
    void recordResourceView(resourceId).catch(() => undefined)
    await loadAttachments()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '学习资源加载失败')
  } finally {
    loading.value = false
  }
}

async function loadAttachments(): Promise<void> {
  attachmentLoading.value = true
  try {
    attachments.value = await listResourceAttachments(resourceId)
  } catch {
    attachments.value = []
  } finally {
    attachmentLoading.value = false
  }
}

async function onAttachmentUploaded(fileId: number): Promise<void> {
  try {
    await attachToResource(resourceId, fileId)
    await loadAttachments()
    ElMessage.success('附件已关联')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '附件关联失败')
  }
}

async function onAttachmentRemove(attachment: FileAttachment): Promise<void> {
  try {
    await detachResourceAttachment(resourceId, attachment.id)
    await loadAttachments()
    ElMessage.success('附件已移除')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '附件移除失败')
  }
}

async function toggleStatus(): Promise<void> {
  if (!resource.value) {
    return
  }
  statusUpdating.value = true
  try {
    const next = resource.value.status === 'PUBLISHED' ? 'DRAFT' : 'PUBLISHED'
    resource.value = await updateResourceStatus(resourceId, next)
    ElMessage.success(next === 'PUBLISHED' ? '资源已发布' : '资源已下架')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '资源状态更新失败')
  } finally {
    statusUpdating.value = false
  }
}
</script>

<template>
  <div v-loading="loading" class="ledger-page resource-detail-page">
    <header class="ledger-header">
      <div>
        <div class="ledger-kicker">学习资源详情</div>
        <h1 class="ledger-title">{{ resource?.title ?? '正在加载资源' }}</h1>
        <p class="ledger-subtitle">
          {{ resource?.subjectName ?? '课程' }}
          <template v-if="resource?.updatedAt"> · 更新于 {{ formatDateTime(resource.updatedAt) }}</template>
        </p>
      </div>
      <div class="ledger-date">
        <el-button @click="router.push({ name: 'resources' })">返回资源列表</el-button>
        <el-button
          v-if="canManage && resource"
          type="primary"
          :loading="statusUpdating"
          @click="toggleStatus"
        >
          {{ resource.status === 'PUBLISHED' ? '下架资源' : '发布资源' }}
        </el-button>
      </div>
    </header>

    <template v-if="resource">
      <article class="resource-detail-card">
        <div class="resource-detail-meta">
          <el-tag v-if="canManage" :type="resource.status === 'PUBLISHED' ? 'success' : 'info'">
            {{ resource.status === 'PUBLISHED' ? '已发布' : '草稿' }}
          </el-tag>
          <el-tag v-for="name in resource.knowledgePointNames" :key="name" type="warning" effect="plain">
            {{ name }}
          </el-tag>
        </div>

        <p v-if="resource.description" class="resource-detail-description">
          {{ resource.description }}
        </p>
        <div v-if="resource.content" class="resource-detail-content">{{ resource.content }}</div>
        <a
          v-if="resource.externalUrl"
          class="resource-external-link"
          :href="resource.externalUrl"
          target="_blank"
          rel="noopener noreferrer"
        >
          查看外部参考资料
        </a>

        <div v-if="resource.knowledgePointNames.length > 0" class="resource-detail-nav">
          <span class="task-meta">关联知识点</span>
          <div class="resource-detail-tags">
            <el-tag v-for="name in resource.knowledgePointNames" :key="name">{{ name }}</el-tag>
          </div>
        </div>
      </article>

      <section class="ledger-section resource-attachment-section">
        <h2 class="ledger-section-title">
          附件
          <small>
            {{ canManage ? '管理员可上传、解绑与预览' : '可在线预览或下载已发布资源附件' }}
          </small>
        </h2>
        <FileAttachmentPanel
          :attachments="attachments"
          :loading="attachmentLoading"
          :uploadable="canManage"
          purpose="PLATFORM_RESOURCE"
          empty-text="该资源还没有上传附件。"
          :hint="canManage ? '发布资源前至少需要保留一个有效附件' : '附件由管理员统一维护'"
          @uploaded="onAttachmentUploaded"
          @remove="onAttachmentRemove"
        />
      </section>
    </template>

    <div v-else-if="!loading" class="empty-ledger">
      资源不存在、尚未发布或没有可查看附件。
    </div>
  </div>
</template>

<style scoped>
.resource-detail-card {
  padding: 24px;
  background: var(--white);
  border: 1px solid var(--rule);
}

.resource-detail-meta,
.resource-detail-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.resource-detail-description {
  margin: 20px 0 0;
  color: var(--ink-soft);
  font-size: 16px;
  line-height: 1.85;
}

.resource-detail-content {
  margin-top: 22px;
  font-size: 16px;
  line-height: 1.95;
  white-space: pre-wrap;
}

.resource-external-link {
  display: inline-block;
  margin-top: 18px;
  color: var(--jade);
  font-weight: 700;
}

.resource-detail-nav {
  margin-top: 26px;
  padding-top: 18px;
  border-top: 1px solid var(--rule);
}

.resource-attachment-section {
  margin-top: 26px;
}
</style>
