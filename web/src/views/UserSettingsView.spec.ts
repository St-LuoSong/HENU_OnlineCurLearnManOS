import { flushPromises, mount } from '@vue/test-utils'
import { ElMessage } from 'element-plus'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({
  getUserProfile: vi.fn(),
  updateUserProfile: vi.fn(),
  getUserAiPreference: vi.fn(),
  updateUserAiPreference: vi.fn(),
  getPendingPasswordRequest: vi.fn(),
  submitPasswordRequest: vi.fn(),
  cancelPasswordRequest: vi.fn()
}))

vi.mock('@/api/settings', () => api)

import UserSettingsView from '@/views/UserSettingsView.vue'

const stubs = {
  'el-button': { template: '<button type="button"><slot /></button>' },
  'el-input': {
    props: {
      modelValue: { type: String, default: '' },
      type: { type: String, default: 'text' },
      placeholder: { type: String, default: '' },
      maxlength: { type: [String, Number], default: undefined },
      rows: { type: [String, Number], default: undefined },
      showPassword: { type: Boolean, default: false },
      autocomplete: { type: String, default: '' }
    },
    emits: ['update:modelValue'],
    template: `
      <textarea
        v-if="type === 'textarea'"
        class="stub-input"
        :placeholder="placeholder"
        :value="modelValue"
        @input="$emit('update:modelValue', $event.target.value)"
      ></textarea>
      <input
        v-else
        class="stub-input"
        :type="type"
        :placeholder="placeholder"
        :value="modelValue"
        @input="$emit('update:modelValue', $event.target.value)"
      />
    `
  },
  'el-switch': {
    props: { modelValue: { type: Boolean, default: false } },
    emits: ['update:modelValue'],
    template: `
      <input
        class="stub-switch"
        type="checkbox"
        :checked="modelValue"
        @change="$emit('update:modelValue', $event.target.checked)"
      />
    `
  },
  'el-tabs': { template: '<div class="stub-tabs"><slot /></div>' },
  'el-tab-pane': { template: '<section class="stub-pane"><slot /></section>' },
  'el-form': { template: '<form><slot /></form>' },
  'el-form-item': { template: '<div><slot /></div>' },
  'el-tag': { template: '<span class="stub-tag"><slot /></span>' }
}

function profileFixture(overrides: Record<string, unknown> = {}) {
  return {
    userId: 31,
    loginId: '2024001',
    displayName: '张三',
    email: '2024001@example.com',
    role: 'STUDENT',
    roles: ['STUDENT'],
    avatarUrl: null,
    department: '计算机学院',
    major: '软件工程',
    gradeLevel: '2024 级',
    phone: null,
    bio: null,
    teacherTitle: null,
    researchArea: null,
    notifyEmail: true,
    notifyForumReply: true,
    updatedAt: '2026-09-20T10:00:00',
    ...overrides
  }
}

function mountSettings() {
  return mount(UserSettingsView, {
    global: {
      stubs,
      directives: { loading: {} }
    }
  })
}

function buttonByText(wrapper: ReturnType<typeof mountSettings>, text: string) {
  return wrapper.findAll('button').find((button) => button.text().includes(text))
}

describe('UserSettingsView 个人中心', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    api.getUserProfile.mockResolvedValue(profileFixture())
    api.updateUserProfile.mockImplementation(async (payload: Record<string, unknown>) =>
      profileFixture(payload)
    )
    api.getUserAiPreference.mockResolvedValue({
      selectedModelId: 5,
      availableModels: [
        {
          id: 5,
          providerCode: 'deepseek',
          providerName: 'DeepSeek',
          displayName: 'DeepSeek Chat',
          modelCode: 'deepseek-chat',
          pointCost: 1
        }
      ]
    })
    api.getPendingPasswordRequest.mockResolvedValue(null)
    vi.spyOn(ElMessage, 'error').mockImplementation(() => undefined as never)
    vi.spyOn(ElMessage, 'warning').mockImplementation(() => undefined as never)
    vi.spyOn(ElMessage, 'success').mockImplementation(() => undefined as never)
  })

  it('加载个人资料并把登录标识展示为只读信息', async () => {
    const wrapper = mountSettings()
    await flushPromises()

    expect(api.getUserProfile).toHaveBeenCalledTimes(1)
    expect(wrapper.find('.identity-value').text()).toBe('2024001')
    expect(wrapper.find('.identity-hint').text()).toContain('只读')
    expect(
      (wrapper.find('input[placeholder="请输入姓名"]').element as HTMLInputElement).value
    ).toBe('张三')
  })

  it('教师身份展示职称与研究方向，学生身份展示年级', async () => {
    const student = mountSettings()
    await flushPromises()
    expect(student.find('input[placeholder="例如：2024 级"]').exists()).toBe(true)
    expect(student.find('input[placeholder="例如：副教授"]').exists()).toBe(false)
    expect(student.find('input[placeholder="例如：分布式系统"]').exists()).toBe(false)

    api.getUserProfile.mockResolvedValue(
      profileFixture({ role: 'TEACHER', roles: ['TEACHER'], loginId: 'T2026001' })
    )
    const teacher = mountSettings()
    await flushPromises()
    expect(teacher.find('input[placeholder="例如：副教授"]').exists()).toBe(true)
    expect(teacher.find('input[placeholder="例如：分布式系统"]').exists()).toBe(true)
    expect(teacher.find('input[placeholder="例如：2024 级"]').exists()).toBe(false)
    expect(teacher.find('.identity-value').text()).toBe('T2026001')
  })

  it('保存基本资料会提交完整资料与通知开关', async () => {
    const wrapper = mountSettings()
    await flushPromises()

    await wrapper.find('.stub-switch').setValue(false)
    await buttonByText(wrapper, '保存基本资料')!.trigger('click')
    await flushPromises()

    expect(api.updateUserProfile).toHaveBeenCalledTimes(1)
    expect(api.updateUserProfile.mock.calls[0][0]).toMatchObject({
      displayName: '张三',
      department: '计算机学院',
      major: '软件工程',
      notifyEmail: false,
      notifyForumReply: true
    })
  })

  it('姓名清空时不发起保存请求', async () => {
    const wrapper = mountSettings()
    await flushPromises()

    await wrapper.find('input[placeholder="请输入姓名"]').setValue('   ')
    await buttonByText(wrapper, '保存基本资料')!.trigger('click')
    await flushPromises()

    expect(api.updateUserProfile).not.toHaveBeenCalled()
    expect(ElMessage.warning).toHaveBeenCalled()
  })

  it('两次新密码不一致时不提交，一致后提交申请并进入待审批状态', async () => {
    api.submitPasswordRequest.mockResolvedValue({
      id: 77,
      userId: 31,
      loginId: '2024001',
      displayName: '张三',
      role: 'STUDENT',
      status: 'PENDING',
      requestedAt: '2026-09-20T11:00:00'
    })
    const wrapper = mountSettings()
    await flushPromises()

    await wrapper.find('input[placeholder="请输入当前密码"]').setValue('OldPass123')
    await wrapper.find('input[placeholder="至少 8 位，含字母和数字"]').setValue('NewPass456')
    await wrapper.find('input[placeholder="请再次输入新密码"]').setValue('NewPass789')
    await buttonByText(wrapper, '提交变更申请')!.trigger('click')
    await flushPromises()
    expect(api.submitPasswordRequest).not.toHaveBeenCalled()
    expect(ElMessage.warning).toHaveBeenCalled()

    await wrapper.find('input[placeholder="请再次输入新密码"]').setValue('NewPass456')
    await buttonByText(wrapper, '提交变更申请')!.trigger('click')
    await flushPromises()

    expect(api.submitPasswordRequest).toHaveBeenCalledWith('OldPass123', 'NewPass456')
    expect(wrapper.text()).toContain('已有待审批的密码变更申请')
  })

  it('存在待审批申请时提供撤销入口', async () => {
    api.getPendingPasswordRequest.mockResolvedValue({
      id: 66,
      userId: 31,
      loginId: '2024001',
      displayName: '张三',
      role: 'STUDENT',
      status: 'PENDING',
      requestedAt: '2026-09-20T11:00:00'
    })
    api.cancelPasswordRequest.mockResolvedValue(undefined)
    const wrapper = mountSettings()
    await flushPromises()

    expect(wrapper.text()).toContain('已有待审批的密码变更申请')
    expect(wrapper.text()).not.toContain('提交变更申请')

    await buttonByText(wrapper, '撤销申请')!.trigger('click')
    await flushPromises()

    expect(api.cancelPasswordRequest).toHaveBeenCalledWith(66)
    expect(wrapper.text()).not.toContain('已有待审批的密码变更申请')
  })
})
