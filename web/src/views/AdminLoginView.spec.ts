import { flushPromises, mount } from '@vue/test-utils'
import { ElMessage } from 'element-plus'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const push = vi.fn()
const auth = {
  user: null as { roles: string[] } | null,
  loginAdmin: vi.fn(),
  logout: vi.fn()
}

vi.mock('vue-router', () => ({
  useRouter: () => ({ push })
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => auth
}))

import AdminLoginView from '@/views/AdminLoginView.vue'

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

const stubs = {
  'el-form': { template: '<form><slot /></form>' },
  'el-form-item': { template: '<div><slot /></div>' },
  'el-button': { template: '<button type="submit"><slot /></button>' },
  'el-input': inputStub,
  'router-link': { template: '<a><slot /></a>' }
}

function mountAdminLogin() {
  return mount(AdminLoginView, { global: { stubs } })
}

function inputs(wrapper: ReturnType<typeof mountAdminLogin>) {
  return wrapper.findAll('input.stub-input')
}

describe('AdminLoginView 管理端专属登录', () => {
  beforeEach(() => {
    push.mockReset()
    auth.loginAdmin.mockReset()
    auth.logout.mockReset()
    auth.user = null
    vi.spyOn(ElMessage, 'warning').mockImplementation(() => undefined as never)
    vi.spyOn(ElMessage, 'error').mockImplementation(() => undefined as never)
  })

  it('管理员登录成功进入管理后台', async () => {
    auth.loginAdmin.mockImplementation(async () => {
      auth.user = { roles: ['ADMIN'] }
    })
    const wrapper = mountAdminLogin()

    await inputs(wrapper)[0].setValue('admin')
    await inputs(wrapper)[1].setValue('123456')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(auth.loginAdmin).toHaveBeenCalledWith('admin', '123456')
    expect(push).toHaveBeenCalledWith('/admin/dashboard')
    expect(auth.logout).not.toHaveBeenCalled()
  })

  it('非管理员账号登录会被拒绝并登出', async () => {
    auth.loginAdmin.mockImplementation(async () => {
      auth.user = { roles: ['STUDENT'] }
    })
    const wrapper = mountAdminLogin()

    await inputs(wrapper)[0].setValue('2024001')
    await inputs(wrapper)[1].setValue('Passw0rd123')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(auth.logout).toHaveBeenCalledTimes(1)
    expect(push).not.toHaveBeenCalled()
    expect((ElMessage.error as unknown as { mock: { calls: string[][] } }).mock.calls[0][0])
      .toContain('不是管理员账号')
  })

  it('未填写账号或密码时不发起请求', async () => {
    const wrapper = mountAdminLogin()

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(auth.loginAdmin).not.toHaveBeenCalled()
    expect(ElMessage.warning).toHaveBeenCalled()
  })

  it('提供返回普通登录入口的说明', () => {
    const wrapper = mountAdminLogin()

    expect(wrapper.text()).toContain('管理员登录')
    expect(wrapper.text()).toContain('普通登录入口')
  })
})
