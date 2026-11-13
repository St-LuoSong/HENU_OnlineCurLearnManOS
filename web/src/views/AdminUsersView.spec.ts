import { flushPromises, mount } from '@vue/test-utils'
import { ElMessage } from 'element-plus'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({
  listAdminUsers: vi.fn(),
  updateAdminUserStatus: vi.fn(),
  deleteAdminUser: vi.fn()
}))

vi.mock('@/api/admin', () => api)

import AdminUsersView from '@/views/AdminUsersView.vue'

const stubs = {
  'el-button': { template: '<button type="button"><slot /></button>' },
  'el-table': {
    props: { data: { type: Array, default: () => [] as unknown[] } },
    provide(this: { data: unknown[] }): Record<string, unknown> {
      return { stubTableRows: this.data }
    },
    template: '<div class="stub-table"><slot /></div>'
  },
  'el-table-column': {
    inject: ['stubTableRows'],
    props: { prop: { type: String, default: '' } },
    template: `
      <div class="stub-column">
        <div v-for="(row, index) in stubTableRows" :key="index" class="stub-cell">
          <span v-if="prop">{{ row[prop] }}</span>
          <slot :row="row" />
        </div>
      </div>
    `
  },
  'el-tag': { template: '<span class="stub-tag"><slot /></span>' },
  'el-switch': { template: '<span class="stub-switch" />' },
  'el-empty': { template: '<div class="stub-empty"><slot /></div>' },
  'el-select': { template: '<div><slot /></div>' },
  'el-option': { template: '<div />' },
  'el-pagination': { template: '<div />' },
  'el-dialog': {
    props: { modelValue: { type: Boolean, default: false }, title: { type: String, default: '' } },
    template: `
      <div v-if="modelValue" class="stub-dialog">
        <div class="stub-dialog-title">{{ title }}</div>
        <slot />
        <div class="stub-dialog-footer"><slot name="footer" /></div>
      </div>
    `
  },
  'el-alert': {
    props: { title: { type: String, default: '' } },
    template: '<div class="stub-alert">{{ title }}</div>'
  },
  'el-descriptions': { template: '<div><slot /></div>' },
  'el-descriptions-item': {
    props: { label: { type: String, default: '' } },
    template: '<div class="stub-description">{{ label }}<slot /></div>'
  },
  'el-form': { template: '<form><slot /></form>' },
  'el-form-item': {
    props: { label: { type: String, default: '' } },
    template: '<div class="stub-form-item">{{ label }}<slot /></div>'
  },
  'el-input': {
    props: {
      modelValue: { type: String, default: '' },
      type: { type: String, default: 'text' },
      placeholder: { type: String, default: '' }
    },
    emits: ['update:modelValue'],
    template: `
      <textarea
        v-if="type === 'textarea'"
        class="stub-textarea"
        :placeholder="placeholder"
        :value="modelValue"
        @input="$emit('update:modelValue', $event.target.value)"
      ></textarea>
      <input
        v-else
        class="stub-input"
        :placeholder="placeholder"
        :value="modelValue"
        @input="$emit('update:modelValue', $event.target.value)"
      />
    `
  }
}

const student = {
  id: 7,
  loginId: '2024001',
  displayName: '张三',
  email: 'zhangsan@example.com',
  enabled: true,
  status: 1,
  roles: ['STUDENT'],
  createdAt: '2026-09-21T10:00:00'
}

function mountView() {
  return mount(AdminUsersView, {
    global: {
      stubs,
      directives: { loading: {} }
    }
  })
}

function buttonByText(wrapper: ReturnType<typeof mountView>, text: string) {
  return wrapper.findAll('button').find((button) => button.text().includes(text))
}

describe('AdminUsersView 账号删除', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    api.listAdminUsers.mockResolvedValue({
      records: [student],
      total: 1,
      page: 1,
      size: 10
    })
    api.deleteAdminUser.mockResolvedValue(undefined)
    vi.spyOn(ElMessage, 'warning').mockImplementation(() => undefined as never)
    vi.spyOn(ElMessage, 'success').mockImplementation(() => undefined as never)
    vi.spyOn(ElMessage, 'error').mockImplementation(() => undefined as never)
  })

  it('加载账号并显示删除入口', async () => {
    const wrapper = mountView()
    await flushPromises()

    expect(api.listAdminUsers).toHaveBeenCalledWith({
      page: 1,
      size: 10,
      role: undefined,
      enabled: undefined,
      keyword: undefined
    })
    expect(wrapper.text()).toContain('2024001')
    expect(wrapper.text()).toContain('删除账号')
  })

  it('删除前展示影响说明并要求输入完整学号 / 工号', async () => {
    const wrapper = mountView()
    await flushPromises()

    await buttonByText(wrapper, '删除账号')!.trigger('click')
    expect(wrapper.find('.stub-dialog').exists()).toBe(true)
    expect(wrapper.text()).toContain('旧登录凭证')

    await buttonByText(wrapper, '确认删除')!.trigger('click')
    await flushPromises()
    expect(api.deleteAdminUser).not.toHaveBeenCalled()
    expect(ElMessage.warning).toHaveBeenCalled()

    await wrapper
      .find('textarea[placeholder="请说明删除原因，该内容会写入审计日志"]')
      .setValue('毕业离校')
    await wrapper.find('input[placeholder="2024001"]').setValue('2024001')
    await buttonByText(wrapper, '确认删除')!.trigger('click')
    await flushPromises()

    expect(api.deleteAdminUser).toHaveBeenCalledWith(7, {
      reason: '毕业离校',
      confirmLoginId: '2024001'
    })
    expect(wrapper.text()).not.toContain('2024001')
  })
})
