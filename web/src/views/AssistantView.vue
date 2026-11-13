<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'

import { getAiAccess, type AiAccess } from '@/api/aiAccess'
import {
  listAssistantSessions,
  type AssistantFilter,
  type AssistantMessage,
  type AssistantSession
} from '@/api/assistant'
import {
  cancelChatStream,
  createChatSession,
  deleteChatSession,
  listChatMessages,
  type ChatMessage
} from '@/api/chat'
import { listKnowledgePoints, listSubjects, type KnowledgePoint, type Subject } from '@/api/content'
import {
  addFavorite,
  deleteFavorite,
  deleteFavoriteByMessage,
  listFavoriteMessageIds
} from '@/api/favorites'
import { streamChat } from '@/api/sse'
import AssistantComposer from '@/components/assistant/AssistantComposer.vue'
import AssistantConversation from '@/components/assistant/AssistantConversation.vue'
import AssistantSessionRail from '@/components/assistant/AssistantSessionRail.vue'
import PracticeActionBar from '@/components/assistant/PracticeActionBar.vue'

type AiMode = 'STANDARD' | 'EXPERT'

const route = useRoute()

const loading = ref(true)
const sending = ref(false)
const streamStatus = ref<'connecting' | 'streaming' | null>(null)
const sessions = ref<AssistantSession[]>([])
const selectedKey = ref<string | null>(null)
const messages = ref<AssistantMessage[]>([])
const draft = ref('')
const filter = ref<AssistantFilter>('chat')
const conversationRef = ref<InstanceType<typeof AssistantConversation> | null>(null)
const aiMode = ref<AiMode>('STANDARD')
const aiAccess = ref<AiAccess>({
  standardUsed: 0,
  standardLimit: 50,
  standardRemaining: 50,
  expertUsed: 0,
  expertLimit: 3,
  expertRemaining: 3
})
const favoriteByMessage = ref<Record<number, number>>({})

let activeStreamController: AbortController | null = null
let activeStreamMessage: AssistantMessage | null = null
let activeStreamSessionId: number | null = null
let activeStreamRequestId = ''

const dialogVisible = ref(false)
const creating = ref(false)
const subjects = ref<Subject[]>([])
const knowledgePoints = ref<KnowledgePoint[]>([])
const form = reactive({
  subjectId: undefined as number | undefined,
  knowledgePointId: undefined as number | undefined,
  topic: '',
  title: ''
})

const chatSessions = computed(() =>
  sessions.value.filter((session) => session.sessionMode === 'chat')
)
const selectedSession = computed(() =>
  chatSessions.value.find((session) => sessionKey(session) === selectedKey.value) ?? null
)
const favoritedIds = computed(() => Object.keys(favoriteByMessage.value).map(Number))
const composerHint = computed(() =>
  aiMode.value === 'EXPERT'
    ? `专家模式 · 今日剩余 ${aiAccess.value.expertRemaining}/${aiAccess.value.expertLimit} 次 · 按专家模型价格扣除积分`
    : `普通模式 · 今日剩余 ${aiAccess.value.standardRemaining}/${aiAccess.value.standardLimit} 次 · 不扣积分`
)

onMounted(async () => {
  await Promise.all([loadSessions(), loadAiAccess(), loadFavorites()])
  const requestedSession = Number(route.query.session)
  if (Number.isFinite(requestedSession) && requestedSession > 0) {
    const target = chatSessions.value.find((session) => session.id === requestedSession)
    if (target) {
      await selectSession(target)
      return
    }
  }
  await selectFirstVisible()
})

onUnmounted(abortActiveStream)

function sessionKey(session: AssistantSession): string {
  return `${session.sourceType}:${session.id}`
}

async function loadAiAccess(): Promise<void> {
  try {
    aiAccess.value = await getAiAccess()
  } catch {
    // 配额展示失败不影响主流程，后端仍会做原子校验。
  }
}

async function loadFavorites(): Promise<void> {
  try {
    const messageIds = await listFavoriteMessageIds()
    favoriteByMessage.value = Object.fromEntries(
      messageIds.map((messageId) => [messageId, messageId])
    )
  } catch {
    // 收藏状态可以稍后刷新，不阻塞问答。
  }
}

async function loadSessions(): Promise<void> {
  loading.value = true
  try {
    sessions.value = await listAssistantSessions()
  } catch {
    ElMessage.error('AI 问答会话加载失败')
  } finally {
    loading.value = false
  }
}

async function selectFirstVisible(): Promise<void> {
  if (selectedSession.value) {
    return
  }
  const target = chatSessions.value[0]
  if (target) {
    await selectSession(target)
    return
  }
  selectedKey.value = null
  messages.value = []
}

async function selectSession(session: AssistantSession): Promise<void> {
  abortActiveStream()
  selectedKey.value = sessionKey(session)
  messages.value = []
  loading.value = true
  try {
    messages.value = toChatMessages(await listChatMessages(session.id))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '会话加载失败')
  } finally {
    loading.value = false
    await scrollToBottom(true)
  }
}

async function send(): Promise<void> {
  const target = selectedSession.value
  const content = draft.value.trim()
  if (!target || !content || sending.value) {
    return
  }
  draft.value = ''
  await sendChat(target, content, false)
}

async function sendChat(
  session: AssistantSession,
  content: string,
  retry: boolean
): Promise<void> {
  if (!retry) {
    messages.value.push({
      id: -Date.now(),
      role: 'user',
      content,
      createdAt: new Date().toISOString()
    })
  }
  const assistantMessage = reactive<AssistantMessage>({
    id: -Date.now() - 1,
    role: 'assistant',
    content: '',
    reasoning: '',
    streamPhase: 'connecting',
    createdAt: new Date().toISOString()
  })
  messages.value.push(assistantMessage)
  sending.value = true
  streamStatus.value = 'connecting'
  activeStreamMessage = assistantMessage
  activeStreamSessionId = session.id
  activeStreamRequestId = ''
  await scrollToBottom(true)

  const controller = new AbortController()
  activeStreamController = controller
  try {
    await streamChat(
      session.id,
      content,
      {
        onMeta: (payload) => {
          if (controller.signal.aborted || selectedKey.value !== sessionKey(session)) {
            return
          }
          if (payload && typeof payload === 'object' && 'requestId' in payload) {
            activeStreamRequestId = String(
              (payload as { requestId?: unknown }).requestId ?? ''
            )
          }
          assistantMessage.streamPhase = 'streaming'
          streamStatus.value = 'streaming'
        },
        onDelta: (delta) => {
          if (controller.signal.aborted || selectedKey.value !== sessionKey(session)) {
            return
          }
          assistantMessage.streamPhase = 'streaming'
          streamStatus.value = 'streaming'
          if (delta.phase === 'reasoning') {
            assistantMessage.reasoning = `${assistantMessage.reasoning ?? ''}${delta.content}`
          } else {
            assistantMessage.content += delta.content
          }
          void scrollToBottom()
        },
        onDone: () => {
          assistantMessage.streamPhase = undefined
        }
      },
      controller.signal,
      retry,
      aiMode.value
    )
  } catch (error) {
    if (controller.signal.aborted || selectedKey.value !== sessionKey(session)) {
      assistantMessage.streamPhase = undefined
      assistantMessage.stopped = true
      return
    }
    assistantMessage.failed = true
    assistantMessage.streamPhase = undefined
    try {
      await loadSessions()
    } catch {
      // Keep the failure marker visible even if the rail cannot refresh.
    }
    ElMessage.error(error instanceof Error ? error.message : '回答生成失败')
    return
  } finally {
    if (activeStreamController === controller) {
      activeStreamController = null
      activeStreamMessage = null
      activeStreamSessionId = null
      activeStreamRequestId = ''
    }
    sending.value = false
    streamStatus.value = null
    void loadAiAccess()
  }

  if (controller.signal.aborted || selectedKey.value !== sessionKey(session)) {
    return
  }
  assistantMessage.streamPhase = undefined
  await loadSessions()
  const refreshed = sessions.value.find(
    (item) => item.sourceType === 'CHAT' && item.id === session.id
  )
  if (refreshed) {
    await selectSession(refreshed)
  }
}

async function retryMessage(message: AssistantMessage): Promise<void> {
  const target = selectedSession.value
  if (!target) {
    return
  }
  const index = messages.value.findIndex((item) => item.id === message.id)
  const userMessage = index > 0 ? messages.value[index - 1] : null
  if (!userMessage || userMessage.role !== 'user') {
    return
  }
  messages.value.splice(index, 1)
  await sendChat(target, userMessage.content, true)
}

async function handleQuickAction(prompt: string): Promise<void> {
  draft.value = prompt
  await send()
}

async function favorite(message: AssistantMessage): Promise<void> {
  const existingId = favoriteByMessage.value[message.id]
  try {
    if (existingId) {
      await deleteFavoriteByMessage(message.id)
      const next = { ...favoriteByMessage.value }
      delete next[message.id]
      favoriteByMessage.value = next
      ElMessage.success('已从知识锦囊移除')
      return
    }
    const created = await addFavorite(message.id)
    favoriteByMessage.value = {
      ...favoriteByMessage.value,
      [message.id]: created.id
    }
    ElMessage.success('已收藏到知识锦囊')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '收藏失败')
  }
}

async function removeSession(session: AssistantSession): Promise<void> {
  try {
    await ElMessageBox.confirm(`确认删除会话“${session.title}”吗？`, '删除会话', {
      type: 'warning'
    })
    if (selectedKey.value === sessionKey(session)) {
      abortActiveStream()
    }
    await deleteChatSession(session.id)
    await loadSessions()
    await selectFirstVisible()
    ElMessage.success('会话已删除')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error('会话删除失败')
    }
  }
}

async function openCreateDialog(): Promise<void> {
  form.subjectId = undefined
  form.knowledgePointId = undefined
  form.topic = ''
  form.title = ''
  knowledgePoints.value = []
  dialogVisible.value = true
  if (subjects.value.length === 0) {
    try {
      subjects.value = await listSubjects()
    } catch {
      ElMessage.error('课程列表加载失败')
    }
  }
}

async function handleSubjectChange(subjectId?: number): Promise<void> {
  form.knowledgePointId = undefined
  try {
    knowledgePoints.value = subjectId ? await listKnowledgePoints(subjectId) : []
  } catch {
    knowledgePoints.value = []
    ElMessage.error('知识点加载失败')
  }
}

async function createSession(): Promise<void> {
  if (!form.subjectId && !form.topic.trim()) {
    ElMessage.warning('请选择课程，或填写自定义主题')
    return
  }
  creating.value = true
  try {
    const created = await createChatSession({
      subjectId: form.subjectId,
      knowledgePointId: form.knowledgePointId,
      topic: form.topic.trim() || undefined,
      title: form.title.trim() || undefined
    })
    dialogVisible.value = false
    await loadSessions()
    const target = sessions.value.find(
      (session) => session.sourceType === 'CHAT' && session.id === created.id
    )
    if (target) {
      await selectSession(target)
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '会话创建失败')
  } finally {
    creating.value = false
  }
}

function selectMode(value: string | number | boolean | undefined): void {
  const next = value === 'EXPERT' ? 'EXPERT' : 'STANDARD'
  if (next === 'EXPERT' && aiAccess.value.expertRemaining <= 0) {
    ElMessage.warning('今日专家模式次数已用完')
    aiMode.value = 'STANDARD'
    return
  }
  aiMode.value = next
}

function abortActiveStream(): void {
  const sessionId = activeStreamSessionId
  const requestId = activeStreamRequestId
  if (activeStreamController) {
    activeStreamController.abort()
    activeStreamController = null
  }
  if (sessionId && requestId) {
    void cancelChatStream(sessionId, requestId).catch(() => undefined)
  }
  if (activeStreamMessage) {
    activeStreamMessage.streamPhase = undefined
    activeStreamMessage.stopped = true
  }
  activeStreamMessage = null
  activeStreamSessionId = null
  activeStreamRequestId = ''
  sending.value = false
  streamStatus.value = null
}

function toChatMessages(items: ChatMessage[]): AssistantMessage[] {
  return items.map((message) => ({
    id: message.id,
    role: message.role,
    content: message.content,
    createdAt: message.createdAt,
    failed: message.role === 'assistant' && message.providerCode === 'SYSTEM',
    providerCode: message.providerCode,
    model: message.model,
    totalTokens: message.totalTokens
  }))
}

async function scrollToBottom(force = false): Promise<void> {
  await conversationRef.value?.scrollToBottom(force)
}
</script>

<template>
  <div v-loading="loading" class="ledger-page assistant-page">
    <header class="ledger-header">
      <div>
        <div class="ledger-kicker">AI 学习助手 · 问答</div>
        <h1 class="ledger-title">把问题问到懂为止</h1>
        <p class="ledger-subtitle">
          选择课程或自定义主题，逐段生成可渲染公式与代码的回答，并把高价值回答收进知识锦囊。
        </p>
      </div>
      <div class="ledger-date assistant-mode-legend">
        <el-tag type="success">普通模式免费</el-tag>
        <el-tag type="warning">专家模式每日 3 次</el-tag>
      </div>
    </header>

    <div class="assistant-layout">
      <AssistantSessionRail
        :sessions="sessions"
        :selected-key="selectedKey"
        :filter="filter"
        mode-only="chat"
        :loading="loading"
        @select="selectSession"
        @create="openCreateDialog"
      />

      <section class="assistant-main">
        <AssistantConversation
          ref="conversationRef"
          :session="selectedSession"
          :messages="messages"
          :loading="loading"
          :favorited-ids="favoritedIds"
          @remove="removeSession"
          @retry="retryMessage"
          @favorite="favorite"
        />
        <template v-if="selectedSession">
          <PracticeActionBar
            :session="selectedSession"
            :busy="sending"
            :can-answer="false"
            @quick="handleQuickAction"
          />
          <div class="assistant-mode-bar">
            <el-radio-group
              :model-value="aiMode"
              size="small"
              @update:model-value="selectMode"
            >
              <el-radio-button label="STANDARD">普通</el-radio-button>
              <el-radio-button label="EXPERT">专家</el-radio-button>
            </el-radio-group>
            <span class="assistant-mode-hint">{{ composerHint }}</span>
          </div>
          <div class="assistant-billing-hint">
            {{ sending
              ? (streamStatus === 'streaming' ? '已连接，回答正在实时生成…' : '已发送，正在建立实时连接…')
              : '停止生成会立即中断本轮回答，并退回本轮预占的积分与次数。' }}
          </div>
          <AssistantComposer
            v-model="draft"
            placeholder="输入问题，Enter 发送，Shift + Enter 换行"
            :sending="sending"
            :disabled="!selectedSession"
            mode="chat"
            @send="send"
            @stop="abortActiveStream"
          />
        </template>
      </section>
    </div>

    <el-dialog v-model="dialogVisible" title="新建问答会话" width="560px">
      <el-form label-position="top">
        <el-form-item label="课程">
          <el-select
            v-model="form.subjectId"
            clearable
            placeholder="不选课程时请填写自定义主题"
            style="width: 100%"
            @change="handleSubjectChange"
          >
            <el-option
              v-for="subject in subjects"
              :key="subject.id"
              :label="subject.name"
              :value="subject.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="知识点（可选）">
          <el-select
            v-model="form.knowledgePointId"
            clearable
            :disabled="!form.subjectId"
            placeholder="不选则在课程范围内进行"
            style="width: 100%"
          >
            <el-option
              v-for="point in knowledgePoints"
              :key="point.id"
              :label="point.name"
              :value="point.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="自定义主题">
          <el-input
            v-model="form.topic"
            maxlength="255"
            placeholder="例如：动态规划入门、论文写作结构"
          />
        </el-form-item>
        <el-form-item label="标题（可选）">
          <el-input v-model="form.title" maxlength="255" placeholder="留空使用课程或主题生成" />
        </el-form-item>
      </el-form>
      <div class="career-notice">
        普通模式不扣积分；专家模式按专家模型价格扣积分，每人每天 3 次。
      </div>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="createSession">
          创建会话
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.assistant-page {
  display: flex;
  height: 100%;
  min-height: 0;
  flex-direction: column;
}

.assistant-page .ledger-header {
  flex: 0 0 auto;
}

.assistant-layout {
  display: grid;
  flex: 1 1 auto;
  grid-template-columns: minmax(280px, 0.34fr) minmax(0, 1fr);
  min-height: 0;
  border-bottom: 1px solid var(--rule);
}

.assistant-main {
  display: flex;
  min-width: 0;
  min-height: 0;
  flex-direction: column;
}

.assistant-mode-legend {
  display: flex;
  gap: 8px;
}

.assistant-mode-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  padding: 10px 0 4px;
  border-top: 1px solid var(--rule);
}

.assistant-mode-hint {
  color: var(--ink-soft);
  font-size: 12px;
}

.assistant-billing-hint {
  padding-bottom: 8px;
  color: var(--ink-soft);
  font-size: 12px;
}

@media (max-width: 900px) {
  .assistant-layout {
    grid-template-columns: 1fr;
  }
}
</style>
