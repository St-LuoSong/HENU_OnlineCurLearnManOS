import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const push = vi.fn()
const route = { params: { id: '11' }, query: { from: '/forum' } }

vi.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => ({ push })
}))

const api = vi.hoisted(() => ({
  getForumPost: vi.fn(),
  listForumCategories: vi.fn(),
  createForumReply: vi.fn(),
  updateForumReply: vi.fn(),
  deleteForumReply: vi.fn(),
  deleteForumPost: vi.fn(),
  likeForumPost: vi.fn(),
  unlikeForumPost: vi.fn(),
  createForumPost: vi.fn(),
  updateForumPost: vi.fn(),
  reportForumContent: vi.fn()
}))

vi.mock('@/api/forum', () => api)

import ForumPostDetailView from '@/views/ForumPostDetailView.vue'

const detail = {
  id: 11,
  categoryId: 41,
  categoryName: '课程讨论',
  authorId: 32,
  authorName: '李老师',
  authorRole: 'TEACHER',
  authorBadges: ['教师'],
  title: '极限怎么理解',
  content: '我对极限的定义有点模糊，想听听大家的理解。',
  viewCount: 3,
  likeCount: 1,
  replyCount: 0,
  likedByMe: false,
  canEdit: false,
  canDelete: false,
  canReport: true,
  createdAt: '2026-09-20T10:00:00',
  updatedAt: '2026-09-20T10:00:00',
  replies: []
}

const stubs = {
  'el-button': { template: '<button type="button"><slot /></button>' },
  'el-input': {
    props: { modelValue: { type: String, default: '' }, type: { type: String, default: 'text' }, placeholder: { type: String, default: '' } },
    emits: ['update:modelValue'],
    template: `
      <textarea
        v-if="type === 'textarea'"
        class="stub-input"
        :placeholder="placeholder"
        :value="modelValue"
        @input="$emit('update:modelValue', $event.target.value)"
      ></textarea>
      <input v-else class="stub-input" :placeholder="placeholder" :value="modelValue" @input="$emit('update:modelValue', $event.target.value)" />
    `
  },
  'el-tag': { template: '<span><slot /></span>' },
  ForumPostEditorDialog: true,
  ForumReportDialog: true
}

function mountDetail() {
  return mount(ForumPostDetailView, {
    global: {
      stubs,
      directives: { loading: {} }
    }
  })
}

describe('ForumPostDetailView 回复即时刷新', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    api.getForumPost.mockResolvedValue(detail)
    api.listForumCategories.mockResolvedValue([
      { id: 41, code: 'COURSE', name: '课程讨论', description: '围绕课程内容讨论' }
    ])
  })

  it('发布回复后立即显示，不重新加载整篇帖子', async () => {
    api.createForumReply.mockResolvedValue({
      id: 21,
      postId: 11,
      authorId: 31,
      authorName: '张三',
      authorRole: 'STUDENT',
      authorBadges: [],
      content: '我的回复',
      canEdit: true,
      canDelete: true,
      canReport: false,
      createdAt: '2026-09-20T11:00:00'
    })
    const wrapper = mountDetail()
    await flushPromises()

    await wrapper.find('textarea[placeholder="写下你的回复"]').setValue('我的回复')
    const submit = wrapper
      .findAll('button')
      .find((button) => button.text().includes('发布回复'))
    await submit!.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('我的回复')
    expect(api.getForumPost).toHaveBeenCalledTimes(1)
  })

  it('后端时间字段为空时使用占位文案而不是渲染崩溃', async () => {
    api.createForumReply.mockResolvedValue({
      id: 22,
      postId: 11,
      authorId: 31,
      authorName: '张三',
      authorRole: 'STUDENT',
      authorBadges: [],
      content: '时间缺失的回复',
      canEdit: true,
      canDelete: true,
      canReport: false,
      createdAt: null
    })
    const wrapper = mountDetail()
    await flushPromises()

    await wrapper.find('textarea[placeholder="写下你的回复"]').setValue('时间缺失的回复')
    const submit = wrapper
      .findAll('button')
      .find((button) => button.text().includes('发布回复'))
    await submit!.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('时间缺失的回复')
    expect(wrapper.text()).toContain('刚刚')
  })
})
