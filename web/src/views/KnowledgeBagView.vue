<script setup lang="ts">
import { Delete, EditPen, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { listSubjects, type Subject } from '@/api/content'
import {
  deleteFavorite,
  listFavorites,
  updateFavoriteNote,
  type KnowledgeFavorite
} from '@/api/favorites'
import MarkdownContent from '@/components/markdown/MarkdownContent.vue'
import { formatMonthDayTime } from '@/utils/date'

const router = useRouter()
const loading = ref(true)
const favorites = ref<KnowledgeFavorite[]>([])
const total = ref(0)
const page = ref(1)
const size = 8
const subjects = ref<Subject[]>([])
const subjectId = ref<number | undefined>()
const keyword = ref('')
const noteVisible = ref(false)
const noteText = ref('')
const editing = ref<KnowledgeFavorite | null>(null)
const savingNote = ref(false)
const expanded = ref<Record<number, boolean>>({})

onMounted(async () => {
  await Promise.all([loadSubjects(), loadFavorites()])
})

async function loadSubjects(): Promise<void> {
  try {
    subjects.value = await listSubjects()
  } catch {
    subjects.value = []
  }
}

async function loadFavorites(): Promise<void> {
  loading.value = true
  try {
    const result = await listFavorites({
      page: page.value,
      size,
      subjectId: subjectId.value,
      keyword: keyword.value.trim() || undefined
    })
    favorites.value = result.records
    total.value = result.total
  } catch {
    ElMessage.error('知识锦囊加载失败')
  } finally {
    loading.value = false
  }
}

async function handleFilterChange(): Promise<void> {
  page.value = 1
  await loadFavorites()
}

async function handleSearch(): Promise<void> {
  page.value = 1
  await loadFavorites()
}

async function handlePageChange(value: number): Promise<void> {
  page.value = value
  await loadFavorites()
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function openNote(item: KnowledgeFavorite): void {
  editing.value = item
  noteText.value = item.note ?? ''
  noteVisible.value = true
}

async function saveNote(): Promise<void> {
  if (!editing.value) {
    return
  }
  savingNote.value = true
  try {
    const updated = await updateFavoriteNote(editing.value.id, noteText.value.trim())
    favorites.value = favorites.value.map((item) => item.id === updated.id ? updated : item)
    noteVisible.value = false
    ElMessage.success('备注已保存')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '备注保存失败')
  } finally {
    savingNote.value = false
  }
}

async function remove(item: KnowledgeFavorite): Promise<void> {
  try {
    await ElMessageBox.confirm('确认从知识锦囊移除这条回答吗？', '移除收藏', {
      type: 'warning'
    })
    await deleteFavorite(item.id)
    favorites.value = favorites.value.filter((favorite) => favorite.id !== item.id)
    total.value = Math.max(0, total.value - 1)
    if (favorites.value.length === 0 && page.value > 1) {
      page.value -= 1
      await loadFavorites()
    }
    ElMessage.success('已移除')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error('移除失败')
    }
  }
}

function openSource(item: KnowledgeFavorite): void {
  void router.push({ name: 'assistant', query: { session: String(item.sessionId) } })
}

function toggle(item: KnowledgeFavorite): void {
  expanded.value = { ...expanded.value, [item.id]: !expanded.value[item.id] }
}
</script>

<template>
  <div v-loading="loading" class="ledger-page">
    <header class="ledger-header">
      <div>
        <div class="ledger-kicker">知识锦囊</div>
        <h1 class="ledger-title">把值得回看的回答留下来</h1>
        <p class="ledger-subtitle">
          收藏时保存回答快照，删除原会话也不会丢失；可以补充备注，按课程和关键词重新找到它。
        </p>
      </div>
      <div class="ledger-date">{{ total }} 条收藏</div>
    </header>

    <div class="favorite-toolbar">
      <el-select
        v-model="subjectId"
        clearable
        placeholder="全部课程"
        style="width: 200px"
        @change="handleFilterChange"
      >
        <el-option
          v-for="subject in subjects"
          :key="subject.id"
          :label="subject.name"
          :value="subject.id"
        />
      </el-select>
      <el-input
        v-model="keyword"
        clearable
        placeholder="搜索问题、回答或备注"
        style="max-width: 360px"
        @keydown.enter="handleSearch"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-button type="primary" plain @click="handleSearch">搜索</el-button>
    </div>

    <div v-if="total === 0" class="empty-ledger">
      还没有收藏。回到问答页，把值得反复查看的回答收进锦囊。
    </div>

    <template v-else>
      <div class="favorite-list">
        <article v-for="item in favorites" :key="item.id" class="favorite-item">
        <header class="favorite-head">
          <div>
            <div class="favorite-meta">
              {{ item.subjectName ?? '自定义主题' }}
              <template v-if="item.knowledgePointName"> · {{ item.knowledgePointName }}</template>
              · {{ formatMonthDayTime(item.createdAt, '') }}
            </div>
            <h2 class="favorite-question">{{ item.question ?? '这条回答没有关联问题' }}</h2>
          </div>
          <div class="favorite-actions">
            <el-button link type="primary" @click="openSource(item)">回到会话</el-button>
            <el-button link :icon="EditPen" @click="openNote(item)">备注</el-button>
            <el-button link type="danger" :icon="Delete" @click="remove(item)">移除</el-button>
          </div>
        </header>
        <div class="favorite-answer" :class="{ expanded: expanded[item.id] }">
          <MarkdownContent :content="item.answer" />
        </div>
        <button type="button" class="favorite-toggle" @click="toggle(item)">
          {{ expanded[item.id] ? '收起回答' : '展开完整回答' }}
        </button>
        <div v-if="item.note" class="favorite-note">备注：{{ item.note }}</div>
        </article>
      </div>

      <el-pagination
        v-if="total > size"
        class="favorite-pagination"
        layout="prev, pager, next, jumper"
        :current-page="page"
        :page-size="size"
        :total="total"
        :pager-count="5"
        @current-change="handlePageChange"
      />
    </template>

    <el-dialog v-model="noteVisible" title="补充备注" width="480px">
      <el-input
        v-model="noteText"
        type="textarea"
        :rows="4"
        maxlength="500"
        show-word-limit
        placeholder="记录这条回答为什么值得收藏"
      />
      <template #footer>
        <el-button @click="noteVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingNote" @click="saveNote">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.favorite-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  padding: 16px 0;
  border-bottom: 1px solid var(--rule);
}

.favorite-list {
  display: flex;
  flex-direction: column;
}

.favorite-pagination {
  justify-content: center;
  padding: 22px 0;
}

.favorite-item {
  padding: 20px 0;
  border-bottom: 1px solid var(--rule);
}

.favorite-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.favorite-meta {
  color: var(--ink-soft);
  font-size: 12px;
}

.favorite-question {
  margin: 6px 0 0;
  font-family: var(--font-display);
  font-size: 18px;
  line-height: 1.5;
}

.favorite-actions {
  display: flex;
  flex: 0 0 auto;
  gap: 4px;
}

.favorite-answer {
  position: relative;
  max-height: 180px;
  margin-top: 14px;
  overflow: hidden;
}

.favorite-answer.expanded {
  max-height: none;
}

.favorite-answer::after {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  height: 48px;
  content: '';
  background: linear-gradient(transparent, var(--paper));
}

.favorite-answer.expanded::after {
  display: none;
}

.favorite-toggle {
  margin-top: 8px;
  padding: 0;
  color: var(--primary);
  font-size: 12px;
  background: none;
  border: 0;
  cursor: pointer;
}

.favorite-note {
  margin-top: 12px;
  padding: 10px 12px;
  color: var(--ink-soft);
  font-size: 13px;
  background: var(--ochre-soft);
  border-left: 3px solid var(--ochre);
}

@media (max-width: 760px) {
  .favorite-head {
    flex-direction: column;
  }
}
</style>
