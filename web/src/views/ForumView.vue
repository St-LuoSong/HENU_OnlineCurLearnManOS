<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { listTeachingClasses, type CloudClass } from '@/api/cloudClass'
import {
  acknowledgeForumMessage,
  createForumAnnouncement,
  deleteForumAnnouncement,
  deleteForumPost,
  likeForumPost,
  listForumAnnouncements,
  listForumCategories,
  listForumMessages,
  listForumPosts,
  listForumRankings,
  unlikeForumPost,
  type ForumAnnouncement,
  type ForumMessage,
  type ForumPostSummary,
  type ForumCategory,
  type ForumMedia,
  type ForumRankingMetric,
  type ForumRankings,
  type ForumRankingWindow
} from '@/api/forum'
import ForumCategoryRail from '@/components/forum/ForumCategoryRail.vue'
import ForumMessageFeed from '@/components/forum/ForumMessageFeed.vue'
import ForumPostCard from '@/components/forum/ForumPostCard.vue'
import ForumPostEditorDialog from '@/components/forum/ForumPostEditorDialog.vue'
import ForumReportDialog from '@/components/forum/ForumReportDialog.vue'
import ForumRankingCard from '@/components/forum/ForumRankingCard.vue'
import ImagePreview from '@/components/media/ImagePreview.vue'
import { useAuthStore } from '@/stores/auth'
import { formatDateTime as formatDateTimeText } from '@/utils/date'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const size = 10

const listLoading = ref(false)
const categories = ref<ForumCategory[]>([])
const posts = ref<ForumPostSummary[]>([])
const total = ref(0)
const page = ref(1)
const filterCategoryId = ref<number | undefined>()
const keyword = ref('')
let postsRequestId = 0

const editorVisible = ref(false)
const editingPost = ref<ForumPostSummary | null>(null)
const reportVisible = ref(false)
const reportTargetType = ref<'POST' | 'REPLY'>('POST')
const reportTargetId = ref<number | null>(null)

/** 右侧排行榜：窗口口径与指标口径都由用户显式选择，避免与左侧分类栏重复。 */
const rankings = ref<ForumRankings | null>(null)
const rankingsLoading = ref(false)
const rankingWindow = ref<ForumRankingWindow>('ALL')
const rankingMetric = ref<ForumRankingMetric>('view')
let rankingsRequestId = 0

const announcements = ref<ForumAnnouncement[]>([])
const announcementsLoading = ref(false)
const announcementDialogVisible = ref(false)
const announcementSaving = ref(false)
const teachingClasses = ref<CloudClass[]>([])
const announcementForm = reactive({
  classId: undefined as number | undefined,
  title: '',
  content: ''
})

const forumMessages = ref<ForumMessage[]>([])
const messagesLoading = ref(false)
const unreadMessageCount = ref(0)
const acknowledgingMessages = new Set<string>()
let messagesRequestId = 0

const previewVisible = ref(false)
const previewMedia = ref<ForumMedia[]>([])
const previewIndex = ref(0)

const roles = computed(() => auth.user?.roles ?? [])
const isAdmin = computed(() => roles.value.includes('ADMIN'))
const isTeacher = computed(() => roles.value.includes('TEACHER'))
const canPublishAnnouncement = computed(() => isAdmin.value || isTeacher.value)

onMounted(async () => {
  applyQuery()
  await Promise.all([
    loadCategories(),
    loadPosts(),
    loadRankings(),
    loadAnnouncements(),
    loadMessages()
  ])
})

function applyQuery(): void {
  const queryPage = Number(route.query.page)
  page.value = Number.isFinite(queryPage) && queryPage > 0 ? Math.floor(queryPage) : 1

  const queryCategory = Number(route.query.categoryId)
  filterCategoryId.value =
    Number.isFinite(queryCategory) && queryCategory > 0 ? queryCategory : undefined

  keyword.value = typeof route.query.keyword === 'string' ? route.query.keyword : ''
}

function syncQuery(): void {
  const query: Record<string, string> = {}
  if (filterCategoryId.value) {
    query.categoryId = String(filterCategoryId.value)
  }
  if (keyword.value.trim()) {
    query.keyword = keyword.value.trim()
  }
  if (page.value > 1) {
    query.page = String(page.value)
  }
  void router.replace({ query })
}

async function loadCategories(): Promise<void> {
  try {
    categories.value = await listForumCategories()
  } catch {
    ElMessage.error('论坛分类加载失败')
  }
}

async function loadPosts(): Promise<void> {
  const requestId = ++postsRequestId
  listLoading.value = true
  try {
    const result = await listForumPosts({
      page: page.value,
      size,
      categoryId: filterCategoryId.value,
      keyword: keyword.value.trim() || undefined
    })
    if (requestId !== postsRequestId) {
      return
    }
    const lastPage = Math.max(1, Math.ceil(result.total / size))
    if (page.value > lastPage) {
      page.value = lastPage
      syncQuery()
      return loadPosts()
    }
    posts.value = result.records
    total.value = result.total
  } catch {
    if (requestId === postsRequestId) {
      ElMessage.error('帖子列表加载失败')
    }
  } finally {
    if (requestId === postsRequestId) {
      listLoading.value = false
    }
  }
}

async function loadRankings(): Promise<void> {
  const requestId = ++rankingsRequestId
  rankingsLoading.value = true
  try {
    const result = await listForumRankings(rankingWindow.value, 5)
    if (requestId === rankingsRequestId) {
      rankings.value = result
    }
  } catch {
    if (requestId === rankingsRequestId) {
      rankings.value = null
    }
  } finally {
    if (requestId === rankingsRequestId) {
      rankingsLoading.value = false
    }
  }
}

async function loadAnnouncements(): Promise<void> {
  announcementsLoading.value = true
  try {
    announcements.value = await listForumAnnouncements(5)
  } catch {
    announcements.value = []
  } finally {
    announcementsLoading.value = false
  }
}

async function loadMessages(): Promise<void> {
  const requestId = ++messagesRequestId
  messagesLoading.value = true
  try {
    const result = await listForumMessages(6)
    if (requestId === messagesRequestId) {
      forumMessages.value = result.records
      unreadMessageCount.value = result.unreadCount
    }
  } catch {
    if (requestId === messagesRequestId) {
      forumMessages.value = []
      unreadMessageCount.value = 0
    }
  } finally {
    if (requestId === messagesRequestId) {
      messagesLoading.value = false
    }
  }
}

function changeRankingWindow(value: ForumRankingWindow): void {
  if (rankingWindow.value === value) {
    return
  }
  rankingWindow.value = value
  void loadRankings()
}

function changeRankingMetric(value: ForumRankingMetric): void {
  rankingMetric.value = value
}

function selectCategory(categoryId?: number): void {
  filterCategoryId.value = categoryId
  page.value = 1
  syncQuery()
  void loadPosts()
}

function search(): void {
  page.value = 1
  syncQuery()
  void loadPosts()
}

function handlePageChange(value: number): void {
  page.value = value
  syncQuery()
  void loadPosts()
}

function openCreatePost(): void {
  editingPost.value = null
  editorVisible.value = true
}

function openEditPost(post: ForumPostSummary): void {
  editingPost.value = post
  editorVisible.value = true
}

function openDetail(post: ForumPostSummary): void {
  void router.push({
    name: 'forum-post-detail',
    params: { id: post.id },
    query: { from: route.fullPath }
  })
}

function openMessage(message: ForumMessage): void {
  const postId = message.postId
  if (!postId) {
    return
  }
  if (!message.acknowledged) {
    void acknowledgeMessage(message)
  }
  const query: Record<string, string> = { from: route.fullPath }
  if (message.replyId) {
    query.replyId = String(message.replyId)
  }
  void router.push({
    name: 'forum-post-detail',
    params: { id: postId },
    query
  })
}

async function acknowledgeMessage(message: ForumMessage): Promise<void> {
  const messageKey = `${message.type}-${message.sourceId}`
  if (message.acknowledged || acknowledgingMessages.has(messageKey)) {
    return
  }
  acknowledgingMessages.add(messageKey)
  try {
    await acknowledgeForumMessage({
      type: message.type,
      sourceId: message.sourceId
    })
    const index = forumMessages.value.findIndex(
      (item) => item.type === message.type && item.sourceId === message.sourceId
    )
    if (index >= 0) {
      forumMessages.value[index] = {
        ...forumMessages.value[index],
        acknowledged: true
      }
    }
    unreadMessageCount.value = Math.max(0, unreadMessageCount.value - 1)
  } catch {
    ElMessage.error('消息状态更新失败，请稍后重试')
  } finally {
    acknowledgingMessages.delete(messageKey)
  }
}

function openPreview(payload: { post: ForumPostSummary; index: number }): void {
  previewMedia.value = payload.post.media ?? []
  previewIndex.value = payload.index
  previewVisible.value = true
}

async function handleSaved(): Promise<void> {
  page.value = 1
  syncQuery()
  await Promise.all([loadPosts(), loadRankings(), loadMessages()])
}

async function removePost(post: ForumPostSummary): Promise<void> {
  try {
    await ElMessageBox.confirm(`确认删除帖子“${post.title}”吗？`, '删除帖子', {
      type: 'warning'
    })
    await deleteForumPost(post.id)
    if (posts.value.length === 1 && page.value > 1) {
      page.value -= 1
    }
    syncQuery()
    await Promise.all([loadPosts(), loadRankings(), loadMessages()])
    ElMessage.success('帖子已删除')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error('帖子删除失败')
    }
  }
}

async function toggleLike(post: ForumPostSummary): Promise<void> {
  try {
    const updated = post.likedByMe
      ? await unlikeForumPost(post.id)
      : await likeForumPost(post.id)
    const index = posts.value.findIndex((item) => item.id === post.id)
    if (index >= 0) {
      posts.value[index] = {
        ...posts.value[index],
        likedByMe: updated.likedByMe,
        likeCount: updated.likeCount
      }
    }
    void loadRankings()
  } catch (error) {
    if (error instanceof Error && error.message.includes('不能点赞自己的帖子')) {
      ElMessage.warning('不能点赞自己的帖子')
    } else {
      ElMessage.error('点赞操作失败')
    }
  }
}

function openReport(targetType: 'POST' | 'REPLY', targetId: number): void {
  reportTargetType.value = targetType
  reportTargetId.value = targetId
  reportVisible.value = true
}

async function openAnnouncementDialog(): Promise<void> {
  announcementForm.classId = undefined
  announcementForm.title = ''
  announcementForm.content = ''
  if (isTeacher.value && !isAdmin.value) {
    try {
      teachingClasses.value = await listTeachingClasses()
    } catch {
      teachingClasses.value = []
      ElMessage.error('云班级加载失败，暂时无法发布公示')
      return
    }
    if (teachingClasses.value.length === 0) {
      ElMessage.warning('还没有负责的云班级，建班后才能发布公示')
      return
    }
    announcementForm.classId = teachingClasses.value[0].id
  }
  announcementDialogVisible.value = true
}

async function submitAnnouncement(): Promise<void> {
  if (!announcementForm.title.trim() || !announcementForm.content.trim()) {
    ElMessage.warning('请填写公示标题和内容')
    return
  }
  if (isTeacher.value && !isAdmin.value && !announcementForm.classId) {
    ElMessage.warning('请选择这条公示面向的云班级')
    return
  }
  announcementSaving.value = true
  try {
    await createForumAnnouncement({
      classId: announcementForm.classId ?? null,
      title: announcementForm.title.trim(),
      content: announcementForm.content.trim()
    })
    ElMessage.success('公示已发布')
    announcementDialogVisible.value = false
    await loadAnnouncements()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '公示发布失败')
  } finally {
    announcementSaving.value = false
  }
}

async function removeAnnouncement(item: ForumAnnouncement): Promise<void> {
  try {
    await ElMessageBox.confirm(`确认删除公示“${item.title}”吗？`, '删除公示', { type: 'warning' })
    await deleteForumAnnouncement(item.id)
    await Promise.all([loadAnnouncements(), loadMessages()])
    ElMessage.success('公示已删除')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error instanceof Error ? error.message : '公示删除失败')
    }
  }
}

function formatDateTime(value?: string | null): string {
  return formatDateTimeText(value, '刚刚')
}
</script>

<template>
  <div v-loading="listLoading" class="ledger-page forum-page">
    <header class="ledger-header">
      <div>
        <div class="ledger-kicker">校园讨论空间</div>
        <h1 class="ledger-title">把问题留在桌面上</h1>
        <p class="ledger-subtitle">
          围绕课程、资源与学习方法展开讨论。把问题说清楚，也把解决过程留给后来的人。
        </p>
      </div>
      <div class="ledger-date">共 {{ total }} 篇帖子</div>
    </header>

    <div class="forum-layout">
      <ForumCategoryRail
        :categories="categories"
        :selected-category-id="filterCategoryId"
        :total="total"
        @select="selectCategory"
      />

      <section class="forum-list-column">
        <div class="forum-list-head">
          <div>
            <div class="forum-list-kicker">讨论桌</div>
            <h2 class="forum-list-title">正在发生的讨论</h2>
          </div>
          <span class="forum-list-count">第 {{ page }} 页 · {{ total }} 篇</span>
        </div>

        <div class="forum-list-toolbar">
          <el-input
            v-model="keyword"
            clearable
            placeholder="搜索标题或内容"
            @keyup.enter="search"
          />
          <el-button @click="search">搜索</el-button>
          <el-button type="primary" @click="openCreatePost">发布帖子</el-button>
        </div>

        <div v-if="posts.length === 0" class="empty-ledger">
          当前筛选下还没有帖子。可以发布第一个问题或经验。
        </div>
        <ForumPostCard
          v-for="post in posts"
          :key="post.id"
          :post="post"
          @open="openDetail"
          @edit="openEditPost"
          @delete="removePost"
          @report="openReport('POST', $event.id)"
          @toggle-like="toggleLike"
          @preview="openPreview"
        />

        <el-pagination
          v-if="total > size"
          class="ledger-pagination forum-pagination"
          layout="total, prev, pager, next, jumper"
          :current-page="page"
          :page-size="size"
          :total="total"
          @current-change="handlePageChange"
        />
      </section>

      <aside class="forum-side-column">
        <ForumRankingCard
          :rankings="rankings"
          :loading="rankingsLoading"
          :ranking-window="rankingWindow"
          :ranking-metric="rankingMetric"
          @update:ranking-window="changeRankingWindow"
          @update:ranking-metric="changeRankingMetric"
          @open="openDetail"
        />

        <section class="forum-side-card">
          <div class="forum-side-head">
            <div>
              <div class="forum-side-eyebrow">课程提醒</div>
              <h2 class="forum-side-title">教师重要公示</h2>
            </div>
            <el-button
              v-if="canPublishAnnouncement"
              link
              type="primary"
              @click="openAnnouncementDialog"
            >
              发布
            </el-button>
          </div>

          <div v-loading="announcementsLoading" class="forum-announcement-body">
            <div v-if="announcements.length === 0" class="forum-side-empty">
              暂时没有新的公示。
            </div>
            <article v-for="item in announcements" :key="item.id" class="forum-announcement">
              <div class="forum-announcement-tags">
                <el-tag size="small" type="warning" effect="plain">教师公示</el-tag>
                <span class="forum-announcement-scope">
                  {{ item.className || item.subjectName || '全平台' }}
                </span>
              </div>
              <h3 class="forum-announcement-title">{{ item.title }}</h3>
              <p class="forum-announcement-content">{{ item.content }}</p>
              <div class="forum-announcement-foot">
                <span>{{ item.authorName }} · {{ formatDateTime(item.createdAt) }}</span>
                <el-button
                  v-if="item.canDelete"
                  link
                  type="danger"
                  size="small"
                  @click="removeAnnouncement(item)"
                >
                  删除
                </el-button>
              </div>
            </article>
          </div>
        </section>

        <ForumMessageFeed
          :messages="forumMessages"
          :loading="messagesLoading"
          :unread-count="unreadMessageCount"
          @open="openMessage"
          @acknowledge="acknowledgeMessage"
        />
      </aside>
    </div>

    <ForumPostEditorDialog
      v-model="editorVisible"
      :categories="categories"
      :post="editingPost"
      @saved="handleSaved"
    />
    <ForumReportDialog
      v-model="reportVisible"
      :target-type="reportTargetType"
      :target-id="reportTargetId"
    />
    <ImagePreview
      v-model="previewVisible"
      v-model:index="previewIndex"
      :items="previewMedia"
    />

    <el-dialog v-model="announcementDialogVisible" title="发布教师公示" width="560px">
      <el-form label-position="top">
        <el-form-item v-if="isTeacher && !isAdmin" label="面向云班级" required>
          <el-select v-model="announcementForm.classId" style="width: 100%">
            <el-option
              v-for="item in teachingClasses"
              :key="item.id"
              :label="item.subjectName ? `${item.name}（${item.subjectName}）` : item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-alert
          v-else
          type="info"
          show-icon
          :closable="false"
          title="管理员发布的公示面向全平台，不带班级范围。"
        />
        <el-form-item label="标题" required>
          <el-input v-model="announcementForm.title" maxlength="255" />
        </el-form-item>
        <el-form-item label="内容" required>
          <el-input
            v-model="announcementForm.content"
            type="textarea"
            :rows="5"
            maxlength="2000"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="announcementDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="announcementSaving" @click="submitAnnouncement">
          发布公示
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.forum-layout {
  display: grid;
  grid-template-columns: 200px minmax(0, 1fr) 316px;
  gap: 22px;
  align-items: start;
}

.forum-list-column {
  min-width: 0;
}

.forum-list-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  padding: 2px 2px 12px;
}

.forum-list-kicker {
  margin-bottom: 4px;
  color: var(--ink-faint);
  font-size: 11px;
  letter-spacing: 0.08em;
}

.forum-list-title {
  margin: 0;
  color: var(--ink);
  font-family: var(--font-serif);
  font-size: 22px;
  line-height: 1.35;
}

.forum-list-count {
  flex: 0 0 auto;
  color: var(--ink-soft);
  font-family: var(--font-numeric);
  font-size: 12px;
}

.forum-list-toolbar {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  gap: 8px;
  align-items: center;
  padding: 10px;
  margin-bottom: 4px;
  background: var(--white);
  border: 1px solid var(--rule);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
}

.forum-list-toolbar :deep(.el-input__wrapper) {
  background: var(--paper);
  box-shadow: none;
}

.forum-side-column {
  position: sticky;
  top: 18px;
  align-self: start;
  max-height: calc(100vh - 120px);
  min-width: 0;
  overflow-y: auto;
  padding-left: 20px;
  border-left: 1px solid var(--rule);
  scrollbar-gutter: stable;
}

.forum-side-card {
  padding: 0 0 18px;
  margin-bottom: 18px;
  border-bottom: 1px solid var(--rule);
}

.forum-side-card:first-child {
  padding-top: 2px;
}

.forum-side-card:last-child,
.forum-side-column > :last-child {
  margin-bottom: 0;
  border-bottom: none;
}

.forum-side-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.forum-side-note {
  flex: 0 0 auto;
  padding-top: 16px;
  color: var(--ink-soft);
  font-size: 12px;
}

.forum-side-eyebrow {
  margin-bottom: 3px;
  color: var(--ink-faint);
  font-size: 11px;
  letter-spacing: 0.08em;
}

.forum-side-title {
  margin: 0;
  color: var(--ink);
  font-family: var(--font-serif);
  font-size: 18px;
  font-weight: 700;
  line-height: 1.35;
}

.forum-side-empty {
  margin: 10px 0 0;
  color: var(--ink-soft);
  font-size: 13px;
  line-height: 1.8;
}

.forum-announcement-body {
  min-height: 48px;
}

.forum-announcement {
  padding: 14px 0;
  border-bottom: 1px dashed var(--rule);
}

.forum-announcement:last-child {
  padding-bottom: 0;
  border-bottom: none;
}

.forum-announcement-tags {
  display: flex;
  gap: 6px;
  align-items: center;
}

.forum-announcement-tags :deep(.el-tag) {
  flex: 0 0 auto;
}

.forum-announcement-scope {
  overflow: hidden;
  color: var(--ink-soft);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.forum-announcement-title {
  margin: 8px 0 4px;
  color: var(--ink);
  font-family: var(--font-serif);
  font-size: 14px;
  line-height: 1.5;
  overflow-wrap: anywhere;
}

.forum-announcement-content {
  display: -webkit-box;
  margin: 0;
  overflow: hidden;
  color: var(--ink-soft);
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}

.forum-announcement-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 6px;
  color: var(--ink-soft);
  font-size: 11px;
}

.forum-announcement-foot > span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.forum-pagination {
  margin-top: 18px;
}

@media (max-width: 1180px) {
  .forum-layout {
    grid-template-columns: 180px minmax(0, 1fr);
  }

  .forum-side-column {
    position: static;
    grid-column: 1 / -1;
    max-height: none;
    padding: 18px 0 0;
    overflow: visible;
    border-top: 1px solid var(--rule);
    border-left: none;
    scrollbar-gutter: auto;
  }
}

@media (max-width: 760px) {
  .forum-layout,
  .forum-list-toolbar {
    grid-template-columns: 1fr;
  }

  .forum-list-head {
    align-items: flex-start;
    flex-direction: column;
    gap: 6px;
  }

  .forum-list-title {
    font-size: 20px;
  }

  .forum-side-column {
    padding-top: 14px;
  }

  .forum-announcement-content {
    -webkit-line-clamp: 4;
  }
}
</style>

