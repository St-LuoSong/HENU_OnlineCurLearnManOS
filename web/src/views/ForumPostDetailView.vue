<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import {
  createForumReply,
  deleteForumPost,
  deleteForumReply,
  getForumPost,
  likeForumPost,
  listForumCategories,
  unlikeForumPost,
  updateForumReply,
  type ForumPostDetail,
  type ForumCategory,
  type ForumReply
} from '@/api/forum'
import ForumPostEditorDialog from '@/components/forum/ForumPostEditorDialog.vue'
import ForumReportDialog from '@/components/forum/ForumReportDialog.vue'
import ImagePreview from '@/components/media/ImagePreview.vue'
import MediaGrid from '@/components/media/MediaGrid.vue'
import { formatDateTime as formatDateTimeText } from '@/utils/date'

const route = useRoute()
const router = useRouter()
const postId = Number(route.params.id)

const loading = ref(true)
const detail = ref<ForumPostDetail | null>(null)
const replyText = ref('')
const savingReply = ref(false)
const editingReplyId = ref<number | null>(null)
const replyEditText = ref('')
const editorVisible = ref(false)
const reportVisible = ref(false)
const reportTargetType = ref<'POST' | 'REPLY'>('POST')
const reportTargetId = ref<number | null>(null)
const categories = ref<ForumCategory[]>([])
const previewVisible = ref(false)
const previewIndex = ref(0)
const highlightedReplyId = ref<number | null>(null)
let highlightTimer: number | undefined

onMounted(async () => {
  await Promise.all([load(), loadCategories()])
  await locateReplyFromQuery()
})

onBeforeUnmount(() => {
  if (highlightTimer !== undefined) {
    window.clearTimeout(highlightTimer)
  }
})

async function loadCategories(): Promise<void> {
  try {
    categories.value = await listForumCategories()
  } catch {
    ElMessage.error('论坛分类加载失败')
  }
}

async function load(): Promise<void> {
  loading.value = true
  try {
    detail.value = await getForumPost(postId)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '帖子详情加载失败')
  } finally {
    loading.value = false
  }
}

async function locateReplyFromQuery(): Promise<void> {
  const rawReplyId = Array.isArray(route.query.replyId)
    ? route.query.replyId[0]
    : route.query.replyId
  const replyId = Number(rawReplyId)
  if (!Number.isFinite(replyId) || replyId <= 0) {
    return
  }

  await nextTick()
  const target = document.getElementById(`reply-${replyId}`)
  if (!target) {
    return
  }

  highlightedReplyId.value = replyId
  const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  target.scrollIntoView({
    behavior: reducedMotion ? 'auto' : 'smooth',
    block: 'center'
  })
  if (highlightTimer !== undefined) {
    window.clearTimeout(highlightTimer)
  }
  highlightTimer = window.setTimeout(() => {
    highlightedReplyId.value = null
    highlightTimer = undefined
  }, 2400)
}

function goBack(): void {
  const from = typeof route.query.from === 'string' ? route.query.from : ''
  if (from) {
    void router.push(from)
  } else {
    void router.push({ name: 'forum' })
  }
}

async function toggleLike(): Promise<void> {
  if (!detail.value) {
    return
  }
  try {
    const updated = detail.value.likedByMe
      ? await unlikeForumPost(detail.value.id)
      : await likeForumPost(detail.value.id)
    detail.value = {
      ...detail.value,
      likedByMe: updated.likedByMe,
      likeCount: updated.likeCount
    }
  } catch (error) {
    if (error instanceof Error && error.message.includes('不能点赞自己的帖子')) {
      ElMessage.warning('不能点赞自己的帖子')
    } else {
      ElMessage.error('点赞操作失败')
    }
  }
}

async function submitReply(): Promise<void> {
  if (!detail.value || savingReply.value || !replyText.value.trim()) {
    ElMessage.warning('请输入回复内容')
    return
  }
  savingReply.value = true
  try {
    const current = detail.value
    const created = await createForumReply(current.id, { content: replyText.value.trim() })
    replyText.value = ''
    detail.value = {
      ...current,
      replies: [...current.replies, created],
      replyCount: current.replyCount + 1
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '回复发布失败')
  } finally {
    savingReply.value = false
  }
}

function startEditReply(reply: ForumReply): void {
  if (!reply.canEdit) {
    return
  }
  editingReplyId.value = reply.id
  replyEditText.value = reply.content
}

function cancelEditReply(): void {
  editingReplyId.value = null
  replyEditText.value = ''
}

async function saveReplyEdit(reply: ForumReply): Promise<void> {
  if (!replyEditText.value.trim()) {
    ElMessage.warning('回复内容不能为空')
    return
  }
  try {
    const updated = await updateForumReply(reply.id, { content: replyEditText.value.trim() })
    if (detail.value) {
      const index = detail.value.replies.findIndex((item) => item.id === reply.id)
      if (index >= 0) {
        detail.value.replies[index] = updated
      }
    }
    cancelEditReply()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '回复更新失败')
  }
}

async function removeReply(reply: ForumReply): Promise<void> {
  try {
    await ElMessageBox.confirm('确认删除这条回复吗？', '删除回复', { type: 'warning' })
    await deleteForumReply(reply.id)
    if (detail.value) {
      detail.value.replies = detail.value.replies.filter((item) => item.id !== reply.id)
      detail.value.replyCount = Math.max(0, detail.value.replyCount - 1)
    }
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error('回复删除失败')
    }
  }
}

async function removePost(): Promise<void> {
  if (!detail.value) {
    return
  }
  try {
    await ElMessageBox.confirm(`确认删除帖子“${detail.value.title}”吗？`, '删除帖子', {
      type: 'warning'
    })
    await deleteForumPost(detail.value.id)
    ElMessage.success('帖子已删除')
    goBack()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error('帖子删除失败')
    }
  }
}

function openReport(targetType: 'POST' | 'REPLY', targetId: number): void {
  reportTargetType.value = targetType
  reportTargetId.value = targetId
  reportVisible.value = true
}

function openPreview(index: number): void {
  previewIndex.value = index
  previewVisible.value = true
}

function formatDateTime(value?: string | null): string {
  return formatDateTimeText(value, '刚刚')
}
</script>

<template>
  <div v-loading="loading" class="ledger-page forum-detail-page">
    <header class="ledger-header">
      <div>
        <div class="ledger-kicker">帖子详情</div>
        <h1 class="ledger-title">{{ detail?.title ?? '正在加载帖子' }}</h1>
        <p v-if="detail" class="ledger-subtitle">
          {{ detail.categoryName }} · {{ detail.authorName }} ·
          {{ formatDateTime(detail.createdAt) }}
        </p>
      </div>
      <div class="ledger-date">
        <el-button @click="goBack">返回列表</el-button>
      </div>
    </header>

    <template v-if="detail">
      <article class="forum-detail-card">
        <div class="forum-post-meta">
          <el-tag
            v-for="badge in detail.authorBadges"
            :key="badge"
            size="small"
            type="warning"
            effect="plain"
          >
            {{ badge }}
          </el-tag>
          <span>浏览 {{ detail.viewCount }}</span>
          <span>回复 {{ detail.replyCount }}</span>
          <span>点赞 {{ detail.likeCount }}</span>
        </div>
        <div class="forum-content">{{ detail.content }}</div>
        <MediaGrid
          v-if="detail.media && detail.media.length > 0"
          :items="detail.media"
          @preview="openPreview"
        />
        <div class="forum-detail-actions">
          <el-button
            link
            :type="detail.likedByMe ? 'success' : 'primary'"
            @click="toggleLike"
          >
            {{ detail.likedByMe ? '已点赞' : '点赞' }} {{ detail.likeCount }}
          </el-button>
          <el-button v-if="detail.canEdit" link @click="editorVisible = true">编辑帖子</el-button>
          <el-button v-if="detail.canDelete" link type="danger" @click="removePost">
            删除帖子
          </el-button>
          <el-button
            v-if="detail.canReport"
            link
            type="warning"
            @click="openReport('POST', detail.id)"
          >
            举报帖子
          </el-button>
        </div>
      </article>

      <section class="forum-replies">
        <h2 class="ledger-section-title">
          回复 {{ detail.replies.length }}
          <small>按发布时间排序</small>
        </h2>
        <div v-if="detail.replies.length === 0" class="empty-ledger">还没有回复。</div>
        <article
          v-for="reply in detail.replies"
          :id="`reply-${reply.id}`"
          :key="reply.id"
          class="reply-item"
          :class="{ 'is-target': highlightedReplyId === reply.id }"
        >
          <div class="goal-entry-meta">
            {{ reply.authorName }}
            <el-tag
              v-for="badge in reply.authorBadges"
              :key="badge"
              size="small"
              type="warning"
              effect="plain"
            >
              {{ badge }}
            </el-tag>
            · {{ formatDateTime(reply.createdAt) }}
          </div>
          <template v-if="editingReplyId === reply.id">
            <el-input v-model="replyEditText" type="textarea" :rows="3" />
            <div class="forum-post-actions">
              <el-button link type="primary" @click="saveReplyEdit(reply)">保存</el-button>
              <el-button link @click="cancelEditReply">取消</el-button>
            </div>
          </template>
          <template v-else>
            <div class="reply-content">{{ reply.content }}</div>
            <div class="forum-post-actions">
              <el-button v-if="reply.canEdit" link @click="startEditReply(reply)">编辑</el-button>
              <el-button v-if="reply.canDelete" link type="danger" @click="removeReply(reply)">
                删除
              </el-button>
              <el-button
                v-if="reply.canReport"
                link
                type="warning"
                @click="openReport('REPLY', reply.id)"
              >
                举报
              </el-button>
            </div>
          </template>
        </article>

        <div class="forum-reply-editor">
          <el-input
            v-model="replyText"
            type="textarea"
            :rows="4"
            maxlength="20000"
            placeholder="写下你的回复"
          />
          <el-button type="primary" :loading="savingReply" @click="submitReply">
            发布回复
          </el-button>
        </div>
      </section>
    </template>

    <div v-else-if="!loading" class="empty-ledger">帖子不存在或已被隐藏。</div>

    <ForumPostEditorDialog
      v-model="editorVisible"
      :categories="categories"
      :post="detail"
      @saved="load"
    />
    <ForumReportDialog
      v-model="reportVisible"
      :target-type="reportTargetType"
      :target-id="reportTargetId"
    />
    <ImagePreview
      v-model="previewVisible"
      v-model:index="previewIndex"
      :items="detail?.media ?? []"
    />
  </div>
</template>

<style scoped>
.forum-detail-card {
  padding: 24px;
  background: var(--white);
  border: 1px solid var(--rule);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
}

.forum-post-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 10px;
  align-items: center;
  color: var(--ink-soft);
  font-size: 13px;
}

.forum-content {
  margin: 22px 0;
  color: var(--ink);
  font-size: 16px;
  line-height: 1.95;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.forum-detail-actions,
.forum-post-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.forum-replies {
  margin-top: 30px;
}

.reply-item {
  padding: 16px 14px;
  margin-bottom: 10px;
  background: var(--white);
  border: 1px solid var(--rule);
  border-left: 3px solid transparent;
  border-radius: var(--radius-md);
  scroll-margin-top: 24px;
  transition:
    background 180ms ease,
    border-color 180ms ease,
    box-shadow 180ms ease;
}

.reply-item:hover {
  box-shadow: var(--shadow-soft);
}

.reply-item.is-target {
  background: var(--ochre-soft);
  border-left-color: var(--ochre);
}

.reply-content {
  margin-top: 8px;
  line-height: 1.85;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.forum-reply-editor {
  display: grid;
  gap: 10px;
  margin-top: 20px;
  padding: 16px;
  background: var(--white);
  border: 1px solid var(--rule);
  border-radius: var(--radius-lg);
}

@media (prefers-reduced-motion: reduce) {
  .reply-item {
    transition: none;
  }
}
</style>
