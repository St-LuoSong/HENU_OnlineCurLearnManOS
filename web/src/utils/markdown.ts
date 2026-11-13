/**
 * 轻量 Markdown / LaTeX 渲染器。
 *
 * 不依赖第三方运行时：所有文本先转义，再拼装受控标签，
 * 因此模型输出无法注入脚本。数学公式覆盖课程场景常用语法。
 */

const BLOCK_START = /^(#{1,6}\s*|```|~~~|>\s?|[-*+]\s+|\d+\.\s+|\|.*\||---+\s*$|\$\$|\\\[)/

const MATH_SYMBOLS: Record<string, string> = {
  alpha: 'α', beta: 'β', gamma: 'γ', delta: 'δ', epsilon: 'ε', varepsilon: 'ε',
  zeta: 'ζ', eta: 'η', theta: 'θ', iota: 'ι', kappa: 'κ', lambda: 'λ', mu: 'μ',
  nu: 'ν', xi: 'ξ', pi: 'π', rho: 'ρ', sigma: 'σ', tau: 'τ', upsilon: 'υ',
  phi: 'φ', chi: 'χ', psi: 'ψ', omega: 'ω',
  Gamma: 'Γ', Delta: 'Δ', Theta: 'Θ', Lambda: 'Λ', Xi: 'Ξ', Pi: 'Π',
  Sigma: 'Σ', Phi: 'Φ', Psi: 'Ψ', Omega: 'Ω',
  cdot: '·', times: '×', div: '÷', pm: '±', mp: '∓', ast: '∗',
  le: '≤', leq: '≤', ge: '≥', geq: '≥', ne: '≠', neq: '≠',
  approx: '≈', equiv: '≡', sim: '∼', propto: '∝', ll: '≪', gg: '≫',
  infty: '∞', partial: '∂', nabla: '∇', sum: '∑', prod: '∏', int: '∫',
  oint: '∮', forall: '∀', exists: '∃', neg: '¬', land: '∧', lor: '∨',
  to: '→', rightarrow: '→', leftarrow: '←', Rightarrow: '⇒', Leftarrow: '⇐',
  leftrightarrow: '↔', mapsto: '↦', in: '∈', notin: '∉', ni: '∋',
  subset: '⊂', subseteq: '⊆', supset: '⊃', supseteq: '⊇',
  cup: '∪', cap: '∩', emptyset: '∅', varnothing: '∅', setminus: '∖',
  angle: '∠', perp: '⊥', parallel: '∥', triangle: '△', degree: '°',
  ldots: '…', dots: '…', cdots: '⋯', vdots: '⋮', ddots: '⋱',
  quad: '\u2003', qquad: '\u2003\u2003'
}

const MATH_FUNCTIONS = new Set([
  'sin', 'cos', 'tan', 'cot', 'sec', 'csc', 'arcsin', 'arccos', 'arctan',
  'sinh', 'cosh', 'tanh', 'log', 'ln', 'lg', 'exp', 'lim', 'max', 'min',
  'sup', 'inf', 'det', 'dim', 'ker', 'gcd', 'deg', 'arg'
])

const KEYWORDS: Record<string, Set<string>> = {
  default: new Set([
    'if', 'else', 'for', 'while', 'do', 'switch', 'case', 'default', 'break',
    'continue', 'return', 'class', 'struct', 'enum', 'interface', 'public',
    'private', 'protected', 'static', 'final', 'const', 'void', 'int', 'long',
    'short', 'char', 'float', 'double', 'bool', 'boolean', 'true', 'false',
    'null', 'new', 'delete', 'namespace', 'using', 'template', 'typename',
    'auto', 'try', 'catch', 'throw', 'throws', 'extends', 'implements', 'import',
    'package', 'this', 'super', 'in', 'of', 'from', 'as', 'def', 'elif', 'lambda',
    'pass', 'raise', 'with', 'yield', 'async', 'await', 'None', 'True', 'False'
  ])
}

export function renderMarkdown(source: string): string {
  const lines = String(source ?? '').replace(/\r\n?/g, '\n').split('\n')
  return renderBlocks(lines)
}

function renderBlocks(lines: string[]): string {
  const out: string[] = []
  let index = 0

  while (index < lines.length) {
    const line = lines[index]
    const trimmed = line.trim()

    if (!trimmed) {
      index += 1
      continue
    }

    const fence = trimmed.match(/^(```|~~~)\s*([\w+#.-]*)\s*$/)
    if (fence) {
      const marker = fence[1]
      const language = fence[2] || 'text'
      const body: string[] = []
      index += 1
      while (index < lines.length && lines[index].trim() !== marker) {
        body.push(lines[index])
        index += 1
      }
      if (index < lines.length) {
        index += 1
      }
      out.push(renderCodeBlock(body.join('\n'), language))
      continue
    }

    if (trimmed.startsWith('$$') || trimmed.startsWith('\\[')) {
      const close = trimmed.startsWith('$$') ? '$$' : '\\]'
      const opened = trimmed.startsWith('$$') ? trimmed.slice(2) : trimmed.slice(2)
      const singleLine = opened.endsWith(close)
      if (singleLine && opened.length >= 2) {
        out.push(`<div class="md-math-block">${renderMath(opened.slice(0, -close.length))}</div>`)
        index += 1
        continue
      }
      const body: string[] = opened ? [opened] : []
      index += 1
      while (index < lines.length && !lines[index].trim().endsWith(close)) {
        body.push(lines[index])
        index += 1
      }
      if (index < lines.length) {
        body.push(lines[index].trim().slice(0, -close.length))
        index += 1
      }
      out.push(`<div class="md-math-block">${renderMath(body.join('\n'))}</div>`)
      continue
    }

    const heading = trimmed.match(/^(#{1,6})\s*(.*)$/)
    if (heading) {
      const level = heading[1].length
      out.push(`<h${level}>${renderInline(heading[2])}</h${level}>`)
      index += 1
      continue
    }

    if (/^---+$/.test(trimmed) || /^\*\*\*+$/.test(trimmed)) {
      out.push('<hr>')
      index += 1
      continue
    }

    if (trimmed.startsWith('>')) {
      const quote: string[] = []
      while (index < lines.length && lines[index].trim().startsWith('>')) {
        quote.push(lines[index].trim().replace(/^>\s?/, ''))
        index += 1
      }
      out.push(`<blockquote>${renderBlocks(quote)}</blockquote>`)
      continue
    }

    if (isTableStart(lines, index)) {
      const tableLines: string[] = []
      while (index < lines.length && lines[index].trim().startsWith('|')) {
        tableLines.push(lines[index].trim())
        index += 1
      }
      out.push(renderTable(tableLines))
      continue
    }

    if (/^[-*+]\s+/.test(trimmed) || /^\d+\.\s+/.test(trimmed)) {
      const ordered = /^\d+\.\s+/.test(trimmed)
      const items: string[] = []
      while (index < lines.length) {
        const current = lines[index].trim()
        const match = ordered ? current.match(/^\d+\.\s+(.*)$/) : current.match(/^[-*+]\s+(.*)$/)
        if (!match) {
          break
        }
        items.push(match[1])
        index += 1
      }
      const tag = ordered ? 'ol' : 'ul'
      out.push(`<${tag}>${items.map((item) => `<li>${renderInline(item)}</li>`).join('')}</${tag}>`)
      continue
    }

    const paragraph: string[] = [trimmed]
    index += 1
    while (index < lines.length) {
      const next = lines[index].trim()
      if (!next || BLOCK_START.test(next)) {
        break
      }
      paragraph.push(next)
      index += 1
    }
    out.push(`<p>${renderInline(paragraph.join('\n')).replace(/\n/g, '<br>')}</p>`)
  }

  return out.join('\n')
}

function isTableStart(lines: string[], index: number): boolean {
  if (index + 1 >= lines.length) {
    return false
  }
  const header = lines[index].trim()
  const divider = lines[index + 1].trim()
  return header.startsWith('|')
    && /^\|?[\s:|-]+\|?$/.test(divider)
    && divider.includes('-')
}

function renderTable(lines: string[]): string {
  if (lines.length < 2) {
    return ''
  }
  const cellsOf = (line: string): string[] => {
    const trimmed = line.replace(/^\|/, '').replace(/\|$/, '')
    return trimmed.split('|').map((cell) => cell.trim())
  }
  const header = cellsOf(lines[0])
  const rows = lines.slice(2).map(cellsOf)
  const head = header.map((cell) => `<th>${renderInline(cell)}</th>`).join('')
  const body = rows.map((row) => {
    const cells = header.map((_, i) => `<td>${renderInline(row[i] ?? '')}</td>`).join('')
    return `<tr>${cells}</tr>`
  }).join('')
  return `<div class="md-table-wrap"><table><thead><tr>${head}</tr></thead><tbody>${body}</tbody></table></div>`
}

function renderCodeBlock(code: string, language: string): string {
  const safeLanguage = language.replace(/[^\w+#.-]/g, '') || 'text'
  const encoded = encodeURIComponent(code)
  const highlighted = highlightCode(code, safeLanguage)
  return `<div class="md-code">`
    + `<div class="md-code-head"><span>${escapeHtml(safeLanguage)}</span>`
    + `<button type="button" class="md-code-copy" data-copy-code="${encoded}">复制</button></div>`
    + `<pre><code class="language-${escapeHtml(safeLanguage)}">${highlighted}</code></pre></div>`
}

function highlightCode(code: string, language: string): string {
  const keywords = KEYWORDS[language.toLowerCase()] ?? KEYWORDS.default
  const hashComment = ['python', 'py', 'bash', 'sh', 'yaml', 'yml'].includes(language.toLowerCase())
  let out = ''
  let index = 0

  const push = (text: string, cls?: string): void => {
    out += cls ? `<span class="tok-${cls}">${escapeHtml(text)}</span>` : escapeHtml(text)
  }

  while (index < code.length) {
    const rest = code.slice(index)
    const two = rest.slice(0, 2)

    if (two === '//' || (hashComment && rest[0] === '#')) {
      const end = rest.indexOf('\n')
      const comment = end < 0 ? rest : rest.slice(0, end)
      push(comment, 'comment')
      index += comment.length
      continue
    }
    if (two === '/*') {
      const end = rest.indexOf('*/')
      const comment = end < 0 ? rest : rest.slice(0, end + 2)
      push(comment, 'comment')
      index += comment.length
      continue
    }

    const quote = rest[0]
    if (quote === '"' || quote === "'" || quote === '`') {
      let end = 1
      while (end < rest.length) {
        if (rest[end] === '\\') {
          end += 2
          continue
        }
        if (rest[end] === quote) {
          end += 1
          break
        }
        end += 1
      }
      push(rest.slice(0, end), 'string')
      index += end
      continue
    }

    const number = rest.match(/^(?:0x[\da-fA-F]+|\d+(?:\.\d+)?)/)
    if (number) {
      push(number[0], 'number')
      index += number[0].length
      continue
    }

    const word = rest.match(/^[A-Za-z_$][\w$]*/)
    if (word) {
      push(word[0], keywords.has(word[0]) ? 'keyword' : undefined)
      index += word[0].length
      continue
    }

    push(code[index])
    index += 1
  }

  return out
}

function renderInline(text: string): string {
  const tokens: string[] = []
  const stash = (html: string): string => `\u0000${tokens.push(html) - 1}\u0000`

  let value = text.replace(/`([^`\n]+)`/g, (_, code: string) => stash(
    `<code class="md-inline-code">${escapeHtml(code)}</code>`
  ))

  value = value.replace(/(?<!\\)\$\$([^$]+)\$\$/g, (_, math: string) => stash(
    `<span class="md-math-inline">${renderMath(math)}</span>`
  ))
  value = value.replace(/(?<!\\)\$([^$\n]+)\$/g, (_, math: string) => stash(
    `<span class="md-math-inline">${renderMath(math)}</span>`
  ))
  value = value.replace(/\\\((.+?)\\\)/g, (_, math: string) => stash(
    `<span class="md-math-inline">${renderMath(math)}</span>`
  ))
  value = value.replace(/\\\[(.+?)\\\]/g, (_, math: string) => stash(
    `<span class="md-math-inline">${renderMath(math)}</span>`
  ))

  value = escapeHtml(value)
  value = value.replace(/!\[([^\]]*)\]\(([^)\s]+)(?:\s+"[^"]*")?\)/g, (_, alt: string, url: string) => {
    const safe = safeUrl(url)
    return safe ? `<img src="${escapeHtml(safe)}" alt="${alt}" loading="lazy" class="md-image">` : alt
  })
  value = value.replace(/\[([^\]]+)\]\(([^)\s]+)(?:\s+"[^"]*")?\)/g, (_, label: string, url: string) => {
    const safe = safeUrl(url)
    return safe
      ? `<a href="${escapeHtml(safe)}" target="_blank" rel="noopener noreferrer">${label}</a>`
      : label
  })
  value = value.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
  value = value.replace(/__([^_]+)__/g, '<strong>$1</strong>')
  value = value.replace(/(?<!\*)\*([^*\n]+)\*(?!\*)/g, '<em>$1</em>')
  value = value.replace(/(?<!_)_([^_\n]+)_(?!_)/g, '<em>$1</em>')
  value = value.replace(/~~([^~]+)~~/g, '<del>$1</del>')
  value = value.replace(/\u0000(\d+)\u0000/g, (_, token: string) => tokens[Number(token)] ?? '')
  return value
}

function safeUrl(url: string): string {
  const decoded = url.replace(/&amp;/g, '&').trim()
  return /^(https?:\/\/|mailto:|\/|\.\/|#)/i.test(decoded) ? decoded : ''
}

function renderMath(raw: string): string {
  const tex = raw.replace(/\r?\n/g, ' ').trim()
  let index = 0
  let out = ''

  const readGroup = (): string => {
    while (index < tex.length && /\s/.test(tex[index])) {
      index += 1
    }
    if (tex[index] === '{') {
      const start = index + 1
      let depth = 1
      index += 1
      while (index < tex.length && depth > 0) {
        if (tex[index] === '{') depth += 1
        if (tex[index] === '}') depth -= 1
        index += 1
      }
      return tex.slice(start, Math.max(start, index - 1))
    }
    if (index < tex.length) {
      const char = tex[index]
      index += 1
      return char
    }
    return ''
  }

  while (index < tex.length) {
    const char = tex[index]
    if (char === '\\') {
      const rest = tex.slice(index)
      const env = rest.match(/^\\begin\{([^}]+)\}/)
      if (env) {
        const name = env[1]
        const endToken = `\\end{${name}}`
        const end = tex.indexOf(endToken, index + env[0].length)
        const body = end < 0 ? tex.slice(index + env[0].length) : tex.slice(index + env[0].length, end)
        out += renderEnvironment(name, body)
        index = end < 0 ? tex.length : end + endToken.length
        continue
      }
      const word = rest.match(/^\\([A-Za-z]+)\s?/)
      if (word) {
        const name = word[1]
        index += word[0].length
        if (name === 'frac' || name === 'dfrac' || name === 'tfrac') {
          const numerator = readGroup()
          const denominator = readGroup()
          out += `<span class="md-frac"><span class="md-num">${renderMath(numerator)}</span><span class="md-den">${renderMath(denominator)}</span></span>`
          continue
        }
        if (name === 'sqrt') {
          while (index < tex.length && /\s/.test(tex[index])) index += 1
          let degree = ''
          if (tex[index] === '[') {
            const end = tex.indexOf(']', index)
            degree = tex.slice(index + 1, end)
            index = end + 1
          }
          const body = readGroup()
          out += `<span class="md-sqrt">${degree ? `<sup>${renderMath(degree)}</sup>` : ''}<span class="md-radical">√</span><span class="md-radicand">${renderMath(body)}</span></span>`
          continue
        }
        if (['text', 'textrm', 'mathrm', 'operatorname', 'mathbf', 'mathbb', 'mathcal'].includes(name)) {
          const body = readGroup()
          const cls = name === 'mathbf' ? 'md-math-bold'
            : name === 'mathbb' ? 'md-math-blackboard'
            : name === 'mathcal' ? 'md-math-calligraphic'
            : 'md-math-text'
          out += `<span class="${cls}">${escapeHtml(body)}</span>`
          continue
        }
        if (name === 'left' || name === 'right') {
          while (index < tex.length && tex[index] === ' ') index += 1
          if (index < tex.length) {
            out += escapeHtml(tex[index])
            index += 1
          }
          continue
        }
        if (MATH_SYMBOLS[name]) {
          out += MATH_SYMBOLS[name]
          continue
        }
        if (MATH_FUNCTIONS.has(name)) {
          out += `<span class="md-math-func">${name}</span>`
          continue
        }
        if (name === 'overline' || name === 'bar') {
          out += `<span class="md-overline">${renderMath(readGroup())}</span>`
          continue
        }
        if (name === 'vec') {
          out += `<span class="md-vec">${renderMath(readGroup())}</span>`
          continue
        }
        if (name === 'hat') {
          out += `<span class="md-hat">${renderMath(readGroup())}</span>`
          continue
        }
        out += `<span class="md-math-cmd">${escapeHtml(name)}</span>`
        continue
      }
      index += 1
      if (index < tex.length) {
        out += escapeHtml(tex[index])
        index += 1
      }
      continue
    }

    if (char === '^' || char === '_') {
      index += 1
      const body = readGroup()
      const tag = char === '^' ? 'sup' : 'sub'
      out += `<${tag}>${renderMath(body)}</${tag}>`
      continue
    }

    if (char === '{' || char === '}') {
      index += 1
      continue
    }

    if (char === '&') {
      out += '&nbsp;'
      index += 1
      continue
    }

    if (char === '~') {
      out += ' '
      index += 1
      continue
    }

    out += escapeHtml(char)
    index += 1
  }

  return `<span class="md-math">${out}</span>`
}

function renderEnvironment(name: string, body: string): string {
  const rows = body.split(/\\\\/).map((row) => row.trim()).filter(Boolean)
  const rendered = rows.map((row) => `<span class="md-math-row">${row.split('&').map((cell) => renderMath(cell.trim())).join('&nbsp;&nbsp;')}</span>`).join('')
  if (name === 'cases') {
    return `<span class="md-cases"><span class="md-cases-brace">{</span><span class="md-cases-body">${rendered}</span></span>`
  }
  return `<span class="md-matrix">${rendered}</span>`
}

function escapeHtml(value: string): string {
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}
