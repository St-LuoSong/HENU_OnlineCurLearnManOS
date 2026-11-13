import { mount, type VueWrapper } from '@vue/test-utils'
import { ElMessage } from 'element-plus'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const push = vi.fn()
const route: { query: Record<string, string> } = { query: {} }
const auth = {
  user: null as { roles: string[] } | null,
  login: vi.fn(),
  register: vi.fn(),
  logout: vi.fn()
}

vi.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => ({ push })
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => auth
}))

import LoginView from '@/views/LoginView.vue'

const inputStub = {
  props: {
    modelValue: { type: String, default: '' },
    placeholder: { type: String, default: '' },
    type: { type: String, default: 'text' },
    prefixIcon: { type: Object, default: null },
    showPassword: { type: Boolean, default: false },
    autocomplete: { type: String, default: '' }
  },
  emits: ['update:modelValue', 'keyup'],
  template: `
    <input
      class="stub-input"
      :type="type"
      :placeholder="placeholder"
      :value="modelValue"
      @input="$emit('update:modelValue', $event.target.value)"
    />
  `
}

function mountLogin(): VueWrapper {
  return mount(LoginView, {
    global: {
      stubs: {
        'el-form': { template: '<form><slot /></form>' },
        'el-form-item': { template: '<div><slot /></div>' },
        'el-alert': true,
        'el-button': { template: '<button type="submit"><slot /></button>' },
        'el-input': inputStub
      }
    }
  })
}

function roleButton(wrapper: VueWrapper, index: number) {
  return wrapper.findAll('.auth-role-grid button')[index]
}

function loginInputs(wrapper: VueWrapper) {
  return wrapper.findAll('input.stub-input')
}

describe('LoginView 登录 / 注册状态机', () => {
  beforeEach(() => {
    push.mockReset()
    auth.login.mockReset()
    auth.register.mockReset()
    auth.logout.mockReset()
    auth.user = null
    route.query = {}
    vi.spyOn(ElMessage, 'warning').mockImplementation(() => undefined as never)
    vi.spyOn(ElMessage, 'error').mockImplementation(() => undefined as never)
    vi.spyOn(ElMessage, 'success').mockImplementation(() => undefined as never)
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('学生与教师各自持有独立表单，来回切换不会互相带入', async () => {
    const wrapper = mountLogin()

    await loginInputs(wrapper)[0].setValue('2024001')
    await roleButton(wrapper, 1).trigger('click')

    expect((loginInputs(wrapper)[0].element as HTMLInputElement).value).toBe('')

    await loginInputs(wrapper)[0].setValue('T2026001')
    await roleButton(wrapper, 0).trigger('click')

    expect((loginInputs(wrapper)[0].element as HTMLInputElement).value).toBe('2024001')
    expect((loginInputs(wrapper)[0].element as HTMLInputElement).value).not.toBe('T2026001')
  })

  it('普通登录页只提供学生与教师入口，不出现任何管理员入口', async () => {
    const wrapper = mountLogin()

    const roleButtons = wrapper.findAll('.auth-role-grid button')
    expect(roleButtons).toHaveLength(2)
    expect(roleButtons.map((button) => button.text())).toEqual(['学生', '教师'])
    expect(wrapper.text()).not.toContain('管理员')
    expect(wrapper.find('input[placeholder="请输入 admin"]').exists()).toBe(false)
  })

  it('身份与账号角色不匹配时登出并提示，不跳转', async () => {
    const wrapper = mountLogin()
    auth.login.mockImplementation(async () => {
      auth.user = { roles: ['TEACHER'] }
    })

    await loginInputs(wrapper)[0].setValue('T2026001')
    await loginInputs(wrapper)[1].setValue('Passw0rd123')
    await wrapper.find('form').trigger('submit')

    expect(auth.logout).toHaveBeenCalledTimes(1)
    expect(push).not.toHaveBeenCalled()
    expect((ElMessage.error as unknown as { mock: { calls: string[][] } }).mock.calls[0][0])
      .toContain('不是学生账号')
  })

  it('学生登录成功走学习概览，且不会被 redirect 带进管理后台', async () => {
    const wrapper = mountLogin()
    route.query = { redirect: '/admin/users' }
    auth.login.mockImplementation(async () => {
      auth.user = { roles: ['STUDENT'] }
    })

    await loginInputs(wrapper)[0].setValue('2024001')
    await loginInputs(wrapper)[1].setValue('Passw0rd123')
    await wrapper.find('form').trigger('submit')

    expect(push).toHaveBeenCalledWith('/dashboard')
  })

  it('学生登录成功后可以使用可访问的 redirect', async () => {
    const wrapper = mountLogin()
    route.query = { redirect: '/points' }
    auth.login.mockImplementation(async () => {
      auth.user = { roles: ['STUDENT'] }
    })

    await loginInputs(wrapper)[0].setValue('2024001')
    await loginInputs(wrapper)[1].setValue('Passw0rd123')
    await wrapper.find('form').trigger('submit')

    expect(push).toHaveBeenCalledWith('/points')
  })

  it('提交登录时会带上所选身份，交给服务端二次校验', async () => {
    const wrapper = mountLogin()
    auth.login.mockImplementation(async () => {
      auth.user = { roles: ['TEACHER'] }
    })

    await roleButton(wrapper, 1).trigger('click')
    await loginInputs(wrapper)[0].setValue('T2026001')
    await loginInputs(wrapper)[1].setValue('Passw0rd123')
    await wrapper.find('form').trigger('submit')

    expect(auth.login).toHaveBeenCalledWith('T2026001', 'Passw0rd123', 'TEACHER')
  })
})
