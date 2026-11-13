<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { computed, onUnmounted, ref, watch } from 'vue'

import { renderMarkdown } from '@/utils/markdown'

const props = defineProps<{
  content: string
  streaming?: boolean
}>()

const renderedSource = ref(props.content)
let frame = 0
let pendingSource = props.content

watch(
  () => props.content,
  (value) => {
    pendingSource = value
    if (frame) {
      return
    }
    frame = requestAnimationFrame(() => {
      frame = 0
      renderedSource.value = pendingSource
    })
  }
)

onUnmounted(() => {
  if (frame) {
    cancelAnimationFrame(frame)
  }
})

const html = computed(() => renderMarkdown(renderedSource.value))

async function handleClick(event: MouseEvent): Promise<void> {
  const target = event.target as HTMLElement | null
  const button = target?.closest<HTMLButtonElement>('[data-copy-code]')
  if (!button) {
    return
  }
  const encoded = button.dataset.copyCode ?? ''
  try {
    await navigator.clipboard.writeText(decodeURIComponent(encoded))
    ElMessage.success('代码已复制')
  } catch {
    ElMessage.error('复制失败，请手动选择内容')
  }
}
</script>

<template>
  <div class="markdown-content" @click="handleClick">
    <div v-html="html" />
    <span v-if="streaming" class="markdown-caret" aria-hidden="true" />
  </div>
</template>

<style scoped>
.markdown-content {
  color: inherit;
  font-size: 15px;
  line-height: 1.85;
  overflow-wrap: anywhere;
  white-space: normal;
}

.markdown-content :deep(p) {
  margin: 0 0 10px;
}

.markdown-content :deep(p:last-child) {
  margin-bottom: 0;
}

.markdown-content :deep(h1),
.markdown-content :deep(h2),
.markdown-content :deep(h3),
.markdown-content :deep(h4) {
  margin: 18px 0 8px;
  font-family: var(--font-display);
  font-weight: 600;
  line-height: 1.4;
}

.markdown-content :deep(h1) { font-size: 21px; }
.markdown-content :deep(h2) { font-size: 19px; }
.markdown-content :deep(h3) { font-size: 17px; }
.markdown-content :deep(h4) { font-size: 16px; }

.markdown-content :deep(ul),
.markdown-content :deep(ol) {
  margin: 6px 0 12px;
  padding-left: 24px;
}

.markdown-content :deep(li) {
  margin: 3px 0;
}

.markdown-content :deep(blockquote) {
  margin: 12px 0;
  padding: 6px 14px;
  color: var(--ink-soft);
  border-left: 3px solid var(--ochre);
  background: var(--paper-deep);
}

.markdown-content :deep(hr) {
  margin: 18px 0;
  border: 0;
  border-top: 1px solid var(--rule);
}

.markdown-content :deep(a) {
  color: var(--primary);
  text-decoration: underline;
  text-underline-offset: 3px;
}

.markdown-content :deep(img) {
  max-width: 100%;
  border: 1px solid var(--rule);
}

.markdown-content :deep(code) {
  font-family: var(--font-mono, Consolas, monospace);
}

.markdown-content :deep(.md-inline-code) {
  padding: 1px 5px;
  color: var(--ink);
  font-size: 0.9em;
  background: var(--paper-deep);
  border: 1px solid var(--rule);
}

.markdown-content :deep(.md-code) {
  margin: 12px 0;
  border: 1px solid var(--rule);
  background: #f7f6f2;
}

.markdown-content :deep(.md-code-head) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 5px 10px;
  color: var(--ink-soft);
  font-family: var(--font-mono, Consolas, monospace);
  font-size: 11px;
  border-bottom: 1px solid var(--rule);
}

.markdown-content :deep(.md-code-copy) {
  padding: 1px 8px;
  color: var(--ink-soft);
  font-size: 11px;
  background: transparent;
  border: 1px solid var(--rule);
  cursor: pointer;
}

.markdown-content :deep(.md-code-copy:hover) {
  color: var(--ink);
  border-color: var(--ink-soft);
}

.markdown-content :deep(.md-code pre) {
  margin: 0;
  padding: 12px 14px;
  overflow-x: auto;
  font-size: 13px;
  line-height: 1.7;
}

.markdown-content :deep(.md-code code) {
  white-space: pre;
}

.markdown-content :deep(.tok-keyword) { color: #8a3b52; font-weight: 600; }
.markdown-content :deep(.tok-string) { color: #2f6b4f; }
.markdown-content :deep(.tok-comment) { color: #8b8779; font-style: italic; }
.markdown-content :deep(.tok-number) { color: #8a5a2b; }

.markdown-content :deep(.md-table-wrap) {
  margin: 12px 0;
  overflow-x: auto;
}

.markdown-content :deep(table) {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}

.markdown-content :deep(th),
.markdown-content :deep(td) {
  padding: 7px 10px;
  text-align: left;
  border: 1px solid var(--rule);
}

.markdown-content :deep(th) {
  background: var(--paper-deep);
  font-weight: 600;
}

.markdown-content :deep(.md-math) {
  display: inline-block;
  font-family: 'Cambria Math', 'Times New Roman', serif;
  font-size: 1.04em;
  line-height: 1.5;
  white-space: nowrap;
}

.markdown-content :deep(.md-math-block) {
  margin: 12px 0;
  padding: 8px 0;
  overflow-x: auto;
  text-align: center;
}

.markdown-content :deep(.md-math-block .md-math) {
  display: inline-block;
  font-size: 1.18em;
  white-space: normal;
}

.markdown-content :deep(.md-frac) {
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  margin: 0 2px;
  vertical-align: middle;
  line-height: 1.15;
}

.markdown-content :deep(.md-frac .md-num) {
  padding: 0 3px 1px;
  border-bottom: 1px solid currentColor;
}

.markdown-content :deep(.md-frac .md-den) {
  padding-top: 1px;
}

.markdown-content :deep(.md-sqrt) {
  display: inline-flex;
  align-items: flex-start;
}

.markdown-content :deep(.md-radicand) {
  padding: 0 1px;
  border-top: 1px solid currentColor;
}

.markdown-content :deep(.md-overline) {
  border-top: 1px solid currentColor;
}

.markdown-content :deep(.md-vec) {
  position: relative;
  padding-top: 5px;
}

.markdown-content :deep(.md-vec::before) {
  position: absolute;
  top: -2px;
  left: 50%;
  content: '→';
  font-size: 0.7em;
  transform: translateX(-50%);
}

.markdown-content :deep(.md-hat::before) {
  content: '^';
}

.markdown-content :deep(.md-math-func),
.markdown-content :deep(.md-math-text) {
  font-family: 'Times New Roman', serif;
  font-style: normal;
}

.markdown-content :deep(.md-math-bold) { font-weight: 700; }
.markdown-content :deep(.md-math-blackboard) { font-family: 'Cambria Math', serif; font-weight: 600; }
.markdown-content :deep(.md-math-calligraphic) { font-family: 'Segoe Script', 'Cambria Math', serif; }
.markdown-content :deep(.md-math-cmd) { font-style: italic; }

.markdown-content :deep(.md-cases) {
  display: inline-flex;
  align-items: center;
  vertical-align: middle;
}

.markdown-content :deep(.md-cases-brace) {
  margin-right: 3px;
  font-size: 2.2em;
  font-weight: 300;
  line-height: 0.9;
}

.markdown-content :deep(.md-cases-body),
.markdown-content :deep(.md-matrix) {
  display: inline-flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
}

.markdown-content :deep(.md-math-row) {
  display: block;
}

.markdown-caret {
  display: inline-block;
  width: 7px;
  height: 16px;
  margin-left: 3px;
  vertical-align: -2px;
  background: var(--ink-soft);
  animation: markdown-blink 1s steps(2, start) infinite;
}

@keyframes markdown-blink {
  50% { opacity: 0; }
}

@media (prefers-reduced-motion: reduce) {
  .markdown-caret { animation: none; }
}
</style>
