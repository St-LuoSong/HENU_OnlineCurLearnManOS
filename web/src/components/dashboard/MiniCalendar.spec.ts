import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import MiniCalendar from '@/components/dashboard/MiniCalendar.vue'
import { buildMonthGrid, monthRange } from '@/utils/date'

const stubs = {
  'el-icon': { template: "<i class='stub-icon'><slot /></i>" }
}

function mountCalendar(props: Record<string, unknown> = {}) {
  return mount(MiniCalendar, {
    props: {
      modelValue: '2026-09-21',
      ...props
    },
    global: { stubs }
  })
}

describe('MiniCalendar 小型交互日历', () => {
  it('固定渲染 42 格并显示中文日期与秒级时钟', () => {
    const wrapper = mountCalendar()

    expect(wrapper.findAll('button.mini-cell')).toHaveLength(42)
    expect(wrapper.find('button.mini-cell.is-selected').text()).toContain('21')
    expect(wrapper.find('.mini-clock-date').text()).toMatch(/^\d{4}年\d{1,2}月\d{1,2}日$/)
    expect(wrapper.find('.mini-clock-week').text()).toContain('星期')
    expect(wrapper.find('.mini-clock-time').text()).toMatch(/^\d{2}:\d{2}:\d{2}$/)

    wrapper.unmount()
  })

  it('切换月份后抛出覆盖整月的查询区间', async () => {
    const wrapper = mountCalendar()
    const next = wrapper.find('button[aria-label="下一个月"]')

    await next.trigger('click')

    expect(wrapper.find('.mini-month').text()).toBe('2026 年 10 月')
    expect(wrapper.emitted('month-change')?.[0]?.[0]).toEqual(monthRange(2026, 10))

    await wrapper.find('button[aria-label="上一个月"]').trigger('click')
    expect(wrapper.find('.mini-month').text()).toBe('2026 年 9 月')

    wrapper.unmount()
  })

  it('点击日期抛出 update:modelValue 且与网格 key 一致', async () => {
    const wrapper = mountCalendar()
    const cells = wrapper.findAll('button.mini-cell')
    const firstCell = cells[0]

    await firstCell.trigger('click')

    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([buildMonthGrid(2026, 9)[0].key])

    wrapper.unmount()
  })

  it('便签色点按重要优先取色，并标记当天学习事件', () => {
    const wrapper = mountCalendar({
      notes: [
        { id: 1, noteDate: '2026-09-21', content: '普通安排', tone: 'RELAX' },
        { id: 2, noteDate: '2026-09-21', content: '明天交作业', tone: 'IMPORTANT' }
      ],
      events: [{ type: 'TASK', title: '高等数学作业', date: '2026-09-21' }]
    })
    const selected = wrapper.find('button.mini-cell.is-selected')

    expect(selected.find('.mini-dot-note').classes()).toContain('tone-important')
    expect(selected.find('.mini-dot-event').exists()).toBe(true)
    expect(selected.attributes('title')).toContain('高等数学作业')

    wrapper.unmount()
  })

  it('没有便签的日期不显示色点', () => {
    const wrapper = mountCalendar({ notes: [] })

    expect(wrapper.find('button.mini-cell.is-selected .mini-dot-note').exists()).toBe(false)

    wrapper.unmount()
  })

  it('在已签到日期显示勾选标记并补充图例', () => {
    const wrapper = mountCalendar({ checkedDates: ['2026-09-21'] })
    const selected = wrapper.find('button.mini-cell.is-selected')

    expect(selected.find('.mini-checkin').exists()).toBe(true)
    expect(selected.attributes('aria-label')).toContain('已签到')
    expect(wrapper.find('.mini-legend').text()).toContain('已签到')

    wrapper.unmount()
  })
})

