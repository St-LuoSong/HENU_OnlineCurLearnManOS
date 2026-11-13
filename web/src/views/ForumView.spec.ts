import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const push = vi.fn()
const replace = vi.fn()
const route = { query: {} as Record<string, unknown>, fullPath: '/forum' }

vi.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => ({ push, replace })
}))

const api = vi.hoisted(() => ({
  listForumCategories: vi.fn(),
  listForumPosts: vi.fn(),
  getForumPost: vi.fn(),
  createForumPost: vi.fn(),
  updateForumPost: vi.fn(),
  deleteForumPost: vi.fn(),
  likeForumPost: vi.fn(),
  unlikeForumPost: vi.fn(),
  createForumReply: vi.fn(),
  updateForumReply: vi.fn(),
  deleteForumReply: vi.fn(),
  reportForumContent: vi.fn(),
  listForumRankings: vi.fn(),
  listForumAnnouncements: vi.fn(),
  createForumAnnouncement: vi.fn(),
  updateForumAnnouncement: vi.fn(),
  deleteForumAnnouncement: vi.fn(),
  listAdminForumAnnouncements: vi.fn(),
  updateForumAnnouncementStatus: vi.fn()
}))

vi.mock('@/api/forum', () => api)

const auth = vi.hoisted(() => ({ user: null as { roles: string[] } | null }))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => auth
}))

import ForumView from '@/views/ForumView.vue'

const post = {
  id: 11,
  categoryId: 41,
  categoryName: '课程讨论',
  authorId: 32,
  authorName: '李老师',
  authorRole: 'TEACHER',
  authorBadges: ['教师'],
  title: '极限怎么理解',
  contentPreview: '我对极限的定义有点模糊',
  viewCount: 3,
  likeCount: 1,
  replyCount: 0,
  likedByMe: false,
  canEdit: false,
  canDelete: false,
  canReport: true,
  createdAt: '2026-09-20T10:00:00'
}

const rankings = {
  window: 'ALL',
  mostViewed: [post],
  mostLiked: [],
  mostReplied: []
}

const stubs = {
  'el-button': { template: '<button type="button"><slot /></button>' },
  'el-input': {
    props: { modelValue: { type: String, default: '' }, placeholder: { type: String, default: '' } },
    emits: ['update:modelValue', 'keyup'],
    template: '<input class="stub-input" :placeholder="placeholder" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />'
  },
  'el-select': { template: '<div><slot /></div>' },
  'el-option': { template: '<div />' },
  'el-tag': { template: '<span><slot /></span>' },
  'el-dialog': {
    props: { modelValue: { type: Boolean, default: false } },
    template: '<div class="stub-dialog"><slot /><slot name="footer" /></div>'
  },
  'el-form': { template: '<form><slot /></form>' },
  'el-form-item': { template: '<div><slot /></div>' },
  'el-alert': { template: '<div />' },
  'el-pagination': {
    props: { currentPage: Number, pageSize: Number, total: Number },
    emits: ['current-change'],
    template: '<button class="stub-pagination" type="button" @click="$emit(\'current-change\', 2)">分页</button>'
  },
  ForumCategoryRail: true,
  ForumPostEditorDialog: true,
  ForumReportDialog: true,
  ImagePreview: true,
  ForumPostCard: {
    props: { post: { type: Object, required: true } },
    emits: ['open'],
    template: '<article class="stub-post"><button class="forum-post-title" type="button" @click="$emit(\'open\', post)">{{ post.title }}</button></article>'
  }
}

function mountForum() {
  return mount(ForumView, {
    global: {
      stubs,
      directives: { loading: {} }
    }
  })
}

describe('ForumView 列表与分页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    route.query = {}
    api.listForumCategories.mockResolvedValue([
      { id: 41, code: 'COURSE', name: '课程讨论', description: '围绕课程内容讨论' }
    ])
    api.listForumPosts.mockResolvedValue({
      records: [post],
      total: 25,
      page: 1,
      size: 10
    })
    api.listForumRankings.mockResolvedValue(rankings)
    api.listForumAnnouncements.mockResolvedValue([])
    auth.user = { roles: ['STUDENT'] }
  })

  it('列表固定按每页 10 条请求', async () => {
    const wrapper = mountForum()
    await flushPromises()

    expect(api.listForumPosts).toHaveBeenCalledWith({
      page: 1,
      size: 10,
      categoryId: undefined,
      keyword: undefined
    })
    expect(wrapper.text()).toContain('极限怎么理解')
  })

  it('翻页会请求对应页码', async () => {
    const wrapper = mountForum()
    await flushPromises()

    await wrapper.find('.stub-pagination').trigger('click')
    await flushPromises()

    expect(api.listForumPosts).toHaveBeenLastCalledWith({
      page: 2,
      size: 10,
      categoryId: undefined,
      keyword: undefined
    })
  })

  it('点击帖子进入独立详情路由并携带返回地址', async () => {
    const wrapper = mountForum()
    await flushPromises()

    await wrapper.find('.forum-post-title').trigger('click')

    expect(push).toHaveBeenCalledWith({
      name: 'forum-post-detail',
      params: { id: 11 },
      query: { from: '/forum' }
    })
  })

  it('论坛不再提供 AI 助手入口', async () => {
    const wrapper = mountForum()
    await flushPromises()

    expect(wrapper.text()).not.toContain('AI 助手')
  })

  it('右侧改为排行榜，不再重复分类栏里的讨论公约', async () => {
    const wrapper = mountForum()
    await flushPromises()

    expect(api.listForumRankings).toHaveBeenCalledWith('ALL', 5)
    expect(wrapper.find('.forum-rank-list').text()).toContain('极限怎么理解')
    expect(wrapper.text()).not.toContain('讨论公约')
  })

  it('教师公示展示教师标记、课程范围与标题', async () => {
    api.listForumAnnouncements.mockResolvedValue([
      {
        id: 7,
        authorId: 32,
        authorName: '李老师',
        authorBadges: ['教师公示'],
        classId: 5,
        className: '高等数学云班',
        subjectId: 9,
        subjectName: '高等数学',
        title: '期中安排',
        content: '周五随堂测',
        status: 'PUBLISHED',
        canEdit: false,
        canDelete: false,
        createdAt: '2026-09-21T09:00:00'
      }
    ])
    const wrapper = mountForum()
    await flushPromises()

    const block = wrapper.find('.forum-announcement')
    expect(block.text()).toContain('教师公示')
    expect(block.text()).toContain('高等数学云班')
    expect(block.text()).toContain('期中安排')
    expect(block.text()).toContain('李老师')
  })
})
