<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <div class="not-found-page">
    <div class="not-found-code">404</div>
    <div class="not-found-text">页面不存在</div>
    <el-button type="primary" @click="router.push('/dashboard')">返回首页</el-button>
  </div>
</template>

<style scoped>
.not-found-page {
  display: grid;
  min-height: 100vh;
  place-items: center;
  padding: 24px;
  background:
    linear-gradient(90deg, rgba(23, 33, 43, 0.05) 1px, transparent 1px) 0 0 / 32px 32px,
    var(--paper);
}

.not-found-code {
  color: var(--cinnabar);
  font-family: var(--font-display);
  font-size: 84px;
  font-weight: 700;
}

.not-found-text {
  margin: 8px 0 20px;
  color: var(--ink-soft);
}
</style>
