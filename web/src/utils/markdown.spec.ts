import { describe, expect, it } from 'vitest'

import { renderMarkdown } from './markdown'

describe('renderMarkdown', () => {
  it('renders headings, lists and fenced code', () => {
    const html = renderMarkdown('## 要点\n\n- 第一点\n- 第二点\n\n```cpp\nint main() { return 0; }\n```')

    expect(html).toContain('<h2>要点</h2>')
    expect(html).toContain('<li>第一点</li>')
    expect(html).toContain('class="md-code"')
    expect(html).toContain('language-cpp')
  })

  it('renders common latex constructs', () => {
    const html = renderMarkdown('复杂度 \\(O((n+m)\\log m)\\)，分数 $\\frac{a}{b}$')

    expect(html).toContain('md-math')
    expect(html).toContain('md-frac')
    expect(html).toContain('md-math-func')
    expect(html).toContain('O((n+m)')
  })

  it('keeps math in fenced code blocks untouched', () => {
    const html = renderMarkdown('```cpp\n// $100\nint a = b;\n```')

    expect(html).toContain('// $100')
    expect(html).not.toContain('md-math')
  })

  it('escapes raw html from model output', () => {
    const html = renderMarkdown('<script>alert(1)</script> **安全**')

    expect(html).not.toContain('<script>')
    expect(html).toContain('&lt;script&gt;')
    expect(html).toContain('<strong>安全</strong>')
  })

  it('renders tables', () => {
    const html = renderMarkdown('| 名称 | 值 |\n| --- | --- |\n| 复杂度 | O(n) |')

    expect(html).toContain('md-table-wrap')
    expect(html).toContain('<th>名称</th>')
    expect(html).toContain('<td>O(n)</td>')
  })
})
