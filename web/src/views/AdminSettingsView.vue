<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import {
  listLlmProviders,
  testLlmProvider,
  updateLlmProvider,
  type LlmProviderConfig,
  type LlmProviderTestResponse
} from '@/api/admin'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const loading = ref(false)
const saving = ref(false)
const testing = ref(false)
const providers = ref<LlmProviderConfig[]>([])
const selectedId = ref<number | null>(null)
const testResult = ref<LlmProviderTestResponse | null>(null)
const form = reactive({
  baseUrl: '',
  defaultModel: '',
  apiKey: '',
  enabled: true
})

const canManage = computed(() => auth.user?.roles.includes('ADMIN') === true)
const selectedProvider = computed(() => {
  return providers.value.find((provider) => provider.id === selectedId.value) ?? null
})

onMounted(async () => {
  if (!canManage.value) {
    return
  }
  loading.value = true
  try {
    await loadProviders()
  } catch {
    ElMessage.error('LLM 供应商列表加载失败')
  } finally {
    loading.value = false
  }
})

async function loadProviders(): Promise<void> {
  providers.value = await listLlmProviders()
  const current = providers.value.find((item) => item.id === selectedId.value)
  if (current) {
    selectProvider(current)
  } else if (providers.value.length > 0) {
    selectProvider(providers.value[0])
  }
}

function selectProvider(provider: LlmProviderConfig): void {
  selectedId.value = provider.id
  form.baseUrl = provider.baseUrl
  form.defaultModel = provider.defaultModel
  form.apiKey = ''
  form.enabled = provider.enabled
  testResult.value = null
}

async function save(): Promise<void> {
  const provider = selectedProvider.value
  if (!provider) {
    return
  }
  if (!form.baseUrl.trim() || !form.defaultModel.trim()) {
    ElMessage.warning('请填写 API 请求地址和默认模型')
    return
  }
  if (!form.enabled && provider.enabled && provider.affectedUserCount > 0) {
    try {
      await ElMessageBox.confirm(
        `停用后，当前选择该供应商模型的 ${provider.affectedUserCount} 名用户会自动回退到其他可用模型。确认停用吗？`,
        '停用供应商',
        { type: 'warning' }
      )
    } catch (error) {
      if (error === 'cancel' || error === 'close') {
        return
      }
    }
  }
  saving.value = true
  try {
    await updateLlmProvider(provider.id, {
      baseUrl: form.baseUrl.trim(),
      defaultModel: form.defaultModel.trim(),
      apiKey: form.apiKey.trim() || undefined,
      enabled: form.enabled
    })
    form.apiKey = ''
    await loadProviders()
    ElMessage.success('LLM 供应商配置已保存，API Key 已加密存储')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'LLM 供应商配置保存失败')
  } finally {
    saving.value = false
  }
}

function formatDateTime(value?: string): string {
  return value ? value.replace('T', ' ').slice(0, 16) : '未记录'
}

async function test(): Promise<void> {
  if (!selectedProvider.value) {
    return
  }
  testing.value = true
  testResult.value = null
  try {
    testResult.value = await testLlmProvider(selectedProvider.value.id)
    ElMessage.success('供应商连接测试完成')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '供应商连接测试失败')
  } finally {
    testing.value = false
  }
}
</script>

<template>
  <div v-loading="loading" class="ledger-page">
    <header class="ledger-header">
      <div>
        <div class="ledger-kicker">平台设置</div>
        <h1 class="ledger-title">LLM 供应商</h1>
        <p class="ledger-subtitle">
          在管理端配置模型供应商。API Key 加密保存，前端只显示是否已配置，不回显明文。
        </p>
      </div>
      <div class="ledger-date">
        {{ providers.length }} 个供应商
      </div>
    </header>

    <el-alert
      v-if="!canManage"
      title="只有管理员可以访问 LLM 供应商设置"
      type="warning"
      show-icon
      :closable="false"
      class="content-notice"
    />

    <div v-else class="admin-settings-layout">
      <aside class="admin-provider-rail">
        <div class="chat-rail-title">供应商</div>
        <button
          v-for="provider in providers"
          :key="provider.id"
          class="admin-provider-item"
          :class="{ active: provider.id === selectedId }"
          @click="selectProvider(provider)"
        >
          <span class="chat-session-title">{{ provider.name }}</span>
          <span class="chat-session-preview">
            {{ provider.code }} · {{ provider.apiKeyConfigured ? 'Key 已配置' : 'Key 未配置' }} ·
            今日 {{ provider.callsToday }} 次 / 失败 {{ provider.failuresToday }}
          </span>
        </button>
      </aside>

      <section v-if="selectedProvider" class="admin-provider-panel">
        <div class="admin-provider-heading">
          <div>
            <h2>{{ selectedProvider?.name }}</h2>
            <p>
              Adapter：{{ selectedProvider?.adapterCode }} ·
              {{ selectedProvider?.apiKeyConfigured ? 'API Key 已加密保存' : '尚未配置 API Key' }}
            </p>
            <p>
              最近配置更新：{{ formatDateTime(selectedProvider?.updatedAt) }} ·
              今日调用 {{ selectedProvider?.callsToday ?? 0 }} 次 ·
              失败 {{ selectedProvider?.failuresToday ?? 0 }} 次 ·
              影响用户 {{ selectedProvider?.affectedUserCount ?? 0 }} 人
            </p>
          </div>
          <el-switch
            v-model="form.enabled"
            inline-prompt
            active-text="启用"
            inactive-text="停用"
          />
        </div>

        <el-form label-position="top">
          <el-form-item label="API 请求地址">
            <el-input v-model="form.baseUrl" placeholder="https://your-api-endpoint.com/v1" />
            <div class="admin-field-note">填写兼容 OpenAI Chat Completions 格式的服务地址。</div>
          </el-form-item>
          <el-form-item label="API Key">
            <el-input
              v-model="form.apiKey"
              type="password"
              show-password
              autocomplete="new-password"
              :placeholder="
                selectedProvider?.apiKeyConfigured
                  ? '已配置。留空表示保持现有 Key'
                  : '请输入 API Key'
              "
            />
            <div class="admin-field-note">保存时会使用 AES-GCM 加密，不会以明文写入数据库。</div>
          </el-form-item>
          <el-form-item label="默认模型">
            <el-input v-model="form.defaultModel" placeholder="例如 gpt-4o-mini" />
          </el-form-item>
          <div class="admin-provider-actions">
            <el-button type="primary" :loading="saving" @click="save">保存配置</el-button>
            <el-button :loading="testing" @click="test">测试连接</el-button>
          </div>
        </el-form>

        <el-alert
          v-if="testResult"
          :title="`测试成功：${testResult.model}`"
          type="success"
          show-icon
          :closable="false"
          class="admin-test-result"
        >
          <template #default>
            <div class="admin-test-content">{{ testResult.content || '供应商已返回响应。' }}</div>
          </template>
        </el-alert>

        <el-alert
          v-if="selectedProvider?.lastErrorMessage"
          :title="`今日最近一次失败：${selectedProvider?.lastErrorMessage}`"
          type="error"
          show-icon
          :closable="false"
          class="admin-test-result"
        />
      </section>
    </div>
  </div>
</template>

<style scoped>
.admin-settings-layout {
  display: grid;
  grid-template-columns: minmax(240px, 0.32fr) minmax(0, 1fr);
  min-height: 620px;
  border-bottom: 1px solid var(--rule);
}

.admin-provider-rail {
  padding: 20px 18px 20px 0;
  border-right: 1px solid var(--rule);
}

.admin-provider-item {
  display: grid;
  width: 100%;
  gap: 5px;
  padding: 13px 12px;
  color: var(--ink);
  text-align: left;
  background: transparent;
  border: none;
  border-left: 3px solid transparent;
  border-bottom: 1px solid var(--rule);
  cursor: pointer;
}

.admin-provider-item:hover,
.admin-provider-item.active {
  background: var(--paper-deep);
  border-left-color: var(--cinnabar);
}

.admin-provider-panel {
  padding: 26px 0 26px 26px;
}

.admin-provider-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 26px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--rule-strong);
}

.admin-provider-heading h2 {
  margin: 0;
  font-family: var(--font-display);
  font-size: 26px;
}

.admin-provider-heading p {
  margin: 7px 0 0;
  color: var(--ink-soft);
  font-size: 13px;
}

.admin-field-note {
  margin-top: 6px;
  color: var(--ink-soft);
  font-size: 12px;
  line-height: 1.7;
}

.admin-provider-actions {
  display: flex;
  gap: 10px;
  margin-top: 8px;
}

.admin-test-result {
  margin-top: 22px;
}

.admin-test-content {
  margin-top: 6px;
  line-height: 1.8;
  white-space: pre-wrap;
}

@media (max-width: 900px) {
  .admin-settings-layout {
    grid-template-columns: 1fr;
  }

  .admin-provider-rail {
    padding-right: 0;
    border-right: none;
    border-bottom: 1px solid var(--rule);
  }

  .admin-provider-panel {
    padding-left: 0;
  }
}
</style>
