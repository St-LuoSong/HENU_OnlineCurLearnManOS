import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import AssistantSessionRail from '@/components/assistant/AssistantSessionRail.vue'
import type { AssistantSession } from '@/api/assistant'

const stubs = {
  'el-button': { template: '<button type="button"><slot /></button>' },
  'el-dropdown': { template: '<div><slot /></div>' },
  'el-dropdown-menu': { template: '<div><slot /></div>' },
  'el-dropdown-item': { template: '<div><slot /></div>' },
  'el-radio-group': { template: '<div><slot /></div>' },
  'el-radio-button': { template: '<button type="button"><slot /></button>' },
  'el-tag': { template: '<span class="stub-tag"><slot /></span>' }
}

const chat: AssistantSession = {
  id: 1,
  sourceType: 'CHAT',
  sessionMode: 'chat',
  subjectName: '高等数学',
  knowledgePointName: '导数',
  title: '导数问答',
  state: 'ACTIVE',
  messageCount: 4,
  preview: '导数的几何意义是什么？',
  transferQuestion: false,
  updatedAt: '2026-09-21T10:00:00'
}

const practice: AssistantSession = {
  id: 2,
  sourceType: 'PRACTICE',
  sessionMode: 'practice',
  subjectName: '线性代数',
  knowledgePointName: '特征值',
  title: '特征值练习',
  state: 'ASSESSING',
  currentGrade: 'B',
  messageCount: 6,
  preview: '等级 B · ASSESSING',
  transferQuestion: false,
  updatedAt: '2026-09-21T11:00:00'
}

describe('AssistantSessionRail', () => {
  it('按问答 / 练习筛选统一会话', () => {
    const wrapper = mount(AssistantSessionRail, {
      props: {
        sessions: [chat, practice],
        selectedKey: null,
        filter: 'chat'
      },
      global: { stubs }
    })

    expect(wrapper.text()).toContain('导数问答')
    expect(wrapper.text()).not.toContain('特征值练习')
  })

  it('点击会话条目时抛出统一 sourceType + id', async () => {
    const wrapper = mount(AssistantSessionRail, {
      props: {
        sessions: [chat, practice],
        selectedKey: null,
        filter: 'all'
      },
      global: { stubs }
    })

    const buttons = wrapper.findAll('button.chat-session-item')
    await buttons[1].trigger('click')

    expect(wrapper.emitted('select')?.[0]).toEqual([practice])
  })
})
