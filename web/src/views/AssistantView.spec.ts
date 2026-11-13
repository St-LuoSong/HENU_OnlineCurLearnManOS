import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { computed, defineComponent, nextTick } from 'vue'

const route = { query: {} as Record<string, unknown>, fullPath: '/assistant' }

vi.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() })
}))

const assistantApi = vi.hoisted(() => ({
  listAssistantSessions: vi.fn()
}))
vi.mock('@/api/assistant', () => assistantApi)

const chatApi = vi.hoisted(() => ({
  createChatSession: vi.fn(),
  deleteChatSession: vi.fn(),
  listChatMessages: vi.fn()
}))
vi.mock('@/api/chat', () => chatApi)

vi.mock('@/api/content', () => ({
  listKnowledgePoints: vi.fn(),
  listSubjects: vi.fn()
}))

vi.mock('@/api/aiAccess', () => ({
  getAiAccess: vi.fn().mockResolvedValue({
    standardUsed: 0,
    standardLimit: 50,
    standardRemaining: 50,
    expertUsed: 0,
    expertLimit: 3,
    expertRemaining: 3
  })
}))

vi.mock('@/api/favorites', () => ({
  addFavorite: vi.fn(),
  deleteFavorite: vi.fn(),
  deleteFavoriteByMessage: vi.fn(),
  listFavoriteMessageIds: vi.fn().mockResolvedValue([])
}))

vi.mock('@/api/practice', () => ({
  createPracticeSession: vi.fn(),
  deletePracticeSession: vi.fn(),
  getPracticeSession: vi.fn(),
  requestPracticeHint: vi.fn(),
  sendPracticeMessage: vi.fn(),
  startPracticeQuestion: vi.fn()
}))

type StreamHandlers = {
  onMeta?: (data: unknown) => void
  onDelta: (delta: { phase: 'reasoning' | 'answer'; content: string }) => void
  onDone?: (data: unknown) => void
  onError?: (error: Error) => void
}

const streamRuntime = vi.hoisted(() => ({
  handlers: null as StreamHandlers | null,
  resolve: null as (() => void) | null
}))

vi.mock('@/api/sse', () => ({
  streamChat: vi.fn(
    async (
      _sessionId: number | string,
      _content: string,
      handlers: StreamHandlers
    ) => {
      streamRuntime.handlers = handlers
      await new Promise<void>((resolve) => {
        streamRuntime.resolve = resolve
      })
    }
  )
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn() },
  ElMessageBox: { confirm: vi.fn() }
}))

import AssistantView from '@/views/AssistantView.vue'

const session = {
  id: 9,
  sourceType: 'CHAT' as const,
  sessionMode: 'chat' as const,
  title: '导数学习问答',
  messageCount: 0,
  transferQuestion: false,
  updatedAt: '2026-09-21T10:00:00'
}

const AssistantConversationStub = defineComponent({
  name: 'AssistantConversation',
  props: {
    session: { type: Object, default: null },
    messages: { type: Array, default: () => [] }
  },
  setup(props) {
    const text = computed(() =>
      (props.messages as Array<{ role: string; content: string }>)
        .map((message) => `${message.role}:${message.content}`)
        .join('|')
    )
    return {
      text,
      scrollToBottom: async () => undefined
    }
  },
  template: '<div class="conversation">{{ text }}</div>'
})

const AssistantComposerStub = defineComponent({
  name: 'AssistantComposer',
  props: {
    modelValue: { type: String, default: '' },
    sending: Boolean,
    disabled: Boolean,
    mode: String,
    placeholder: String
  },
  emits: ['update:modelValue', 'send', 'stop'],
  template: '<button class="composer-send" type="button" @click="$emit(\'send\')">发送</button>'
})

const stubs = {
  AssistantSessionRail: true,
  AssistantConversation: AssistantConversationStub,
  AssistantComposer: AssistantComposerStub,
  PracticeActionBar: true,
  'el-tag': { template: '<span><slot /></span>' },
  'el-dialog': { props: ['modelValue'], template: '<div />' },
  'el-form': { template: '<div><slot /></div>' },
  'el-form-item': { template: '<div><slot /></div>' },
  'el-select': { template: '<div><slot /></div>' },
  'el-option': { template: '<div />' },
  'el-input': { template: '<input />' },
  'el-button': { template: '<button type="button"><slot /></button>' },
  'el-radio-group': { template: '<div><slot /></div>' },
  'el-radio-button': { template: '<button type="button"><slot /></button>' }
}

function mountAssistant() {
  return mount(AssistantView, {
    global: {
      stubs,
      directives: { loading: {} }
    }
  })
}

describe('AssistantView 的 SSE 实时反馈', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    route.query = {}
    streamRuntime.handlers = null
    streamRuntime.resolve = null
    assistantApi.listAssistantSessions.mockResolvedValue([session])
    chatApi.listChatMessages.mockResolvedValue([])
  })

  it('收到 meta 与 delta 后立即切换状态并渲染增量正文', async () => {
    const wrapper = mountAssistant()
    await flushPromises()

    const composer = wrapper.findComponent({ name: 'AssistantComposer' })
    composer.vm.$emit('update:modelValue', '什么是导数')
    await nextTick()
    composer.vm.$emit('send')
    await nextTick()
    await flushPromises()

    expect(streamRuntime.handlers).not.toBeNull()
    expect(wrapper.text()).toContain('正在建立实时连接')

    streamRuntime.handlers?.onMeta?.({ sessionId: 9, requestId: 'req-1' })
    streamRuntime.handlers?.onDelta({ phase: 'answer', content: '导数是' })
    await nextTick()

    expect(wrapper.text()).toContain('已连接，回答正在实时生成')
    expect(wrapper.find('.conversation').text()).toContain('导数是')

    streamRuntime.resolve?.()
    await flushPromises()
    wrapper.unmount()
  })
})
