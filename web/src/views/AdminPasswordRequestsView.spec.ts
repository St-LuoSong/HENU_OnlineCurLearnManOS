import { flushPromises, mount } from '@vue/test-utils'
import { ElMessage } from 'element-plus'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({
  listPasswordRequests: vi.fn(),
  reviewPasswordRequest: vi.fn()
}))

vi.mock('@/api/settings', () => api)

import AdminPasswordRequestsView from '@/views/AdminPasswordRequestsView.vue'

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
    props: { label: { type: String, default: '' } },
    template: `
      <div class="stub-column">
        <div v-for="(row, index) in stubTableRows" :key="index" class="stub-cell">
          <slot :row="row" />
        </div>
      </div>
    `
  },
  'el-tag': { template: '<span class="stub-tag"><slot /></span>' },
  'el-select': { template: '<div><slot /></div>' },
  'el-option': { template: '<div />' },
  'el-pagination': { template: '<div />' },
  'el-dialog': {
    props: { modelValue: { type: Boolean, default: false }, title: { type: String, default: '' } },
    emits: ['update:modelValue'],
    template: `
      <div v-if="modelValue" class="stub-dialog">
        <div class="stub-dialog-title">{{ title }}</div>
        <slot />
        <div class="stub-dialog-footer"><slot name="footer" /></div>
      </div>
    `
  },
  'el-input': {
    props: {
      modelValue: { type: String, default: '' },
      type: { type: String, default: 'text' },
      placeholder: { type: String, default: '' },
      maxlength: { type: [String, Number], default: undefined },
      rows: { type: [String, Number], default: undefined }
    },
    emits: ['update:modelValue'],
    template: `
      <textarea
        class="stub-input"
        :placeholder="placeholder"
        :value="modelValue"
        @input="$emit('update:modelValue', $event.target.value)"
      ></textarea>
    `
  }
}

const pendingRequest = {
  id: 7,
  userId: 31,
  loginId: '2024001',
  displayName: '张三',
  role: 'STUDENT',
  status: 'PENDING',
  requestedAt: '2026-09-20T10:00:00'
}

function mountView() {
  return mount(AdminPasswordRequestsView, {
    global: {
      stubs,
      directives: { loading: {} }
    }
  })
}

function buttonByText(wrapper: ReturnType<typeof mountView>, text: string) {
  return wrapper.findAll('button').find((button) => button.text().includes(text))
}

describe('AdminPasswordRequestsView 密码变更审批', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    api.listPasswordRequests.mockResolvedValue({
      records: [pendingRequest],
      total: 1,
      page: 1,
      size: 10
    })
    api.reviewPasswordRequest.mockResolvedValue({
      ...pendingRequest,
      status: 'APPROVED',
      reviewerId: 1,
      reviewerName: '管理员',
      reviewedAt: '2026-09-20T11:00:00'
    })
    vi.spyOn(ElMessage, 'error').mockImplementation(() => undefined as never)
    vi.spyOn(ElMessage, 'warning').mockImplementation(() => undefined as never)
    vi.spyOn(ElMessage, 'success').mockImplementation(() => undefined as never)
  })

  it('默认只加载待审批申请并展示申请人信息', async () => {
    const wrapper = mountView()
    await flushPromises()

    expect(api.listPasswordRequests).toHaveBeenCalledWith({
      page: 1,
      size: 10,
      status: 'PENDING'
    })
    expect(wrapper.text()).toContain('张三')
    expect(wrapper.text()).toContain('2024001')
    expect(wrapper.text()).toContain('待审批')
  })

  it('批准申请会提交审批结果并刷新列表', async () => {
    const wrapper = mountView()
    await flushPromises()

    await buttonByText(wrapper, '批准')!.trigger('click')
    await flushPromises()
    expect(wrapper.find('.stub-dialog').exists()).toBe(true)

    await buttonByText(wrapper, '确认提交')!.trigger('click')
    await flushPromises()

    expect(api.reviewPasswordRequest).toHaveBeenCalledWith(7, true, '')
    expect(api.listPasswordRequests).toHaveBeenCalledTimes(2)
  })

  it('驳回申请必须填写说明后才允许提交', async () => {
    const wrapper = mountView()
    await flushPromises()

    await buttonByText(wrapper, '驳回')!.trigger('click')
    await flushPromises()

    await buttonByText(wrapper, '确认提交')!.trigger('click')
    await flushPromises()
    expect(api.reviewPasswordRequest).not.toHaveBeenCalled()
    expect(ElMessage.warning).toHaveBeenCalled()

    await wrapper
      .find('textarea[placeholder="审批说明（驳回时必填，最多 500 字）"]')
      .setValue('材料不充分')
    await buttonByText(wrapper, '确认提交')!.trigger('click')
    await flushPromises()

    expect(api.reviewPasswordRequest).toHaveBeenCalledWith(7, false, '材料不充分')
  })

  it('审批界面不出现任何密码字段', async () => {
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).not.toContain('密码明文')
    expect(wrapper.findAll('input[type="password"]')).toHaveLength(0)
  })
})
