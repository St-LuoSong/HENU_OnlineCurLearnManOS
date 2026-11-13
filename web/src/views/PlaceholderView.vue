<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const title = computed(() => String(route.meta.title ?? '功能模块'))
</script>

<template>
  <div class="placeholder-page">
    <div class="placeholder-title">{{ title }}</div>
    <div class="placeholder-text">该模块将在后续阶段接入业务接口。</div>
  </div>
</template>

<style scoped>
.placeholder-page {
  padding: 28px;
  background: var(--white);
  border: 1px solid var(--rule);
  border-left: 4px solid var(--jade);
}

.placeholder-title {
  font-family: var(--font-display);
  font-size: 24px;
  font-weight: 700;
}

.placeholder-text {
  margin-top: 10px;
  color: var(--ink-soft);
}
</style>
