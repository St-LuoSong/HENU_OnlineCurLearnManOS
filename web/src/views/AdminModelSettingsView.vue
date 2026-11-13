<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'

import {
  listAdminModels,
  updateAdminModel,
  type LlmModelConfig
} from '@/api/admin'

const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const models = ref<LlmModelConfig[]>([])
const editing = ref<LlmModelConfig | null>(null)
const form = reactive({
  displayName: '',
  tier: 'STANDARD' as 'STANDARD' | 'EXPERT',
  pointCost: 1,
  enabled: true
})

onMounted(() => {
  void load()
})

async function load(): Promise<void> {
  loading.value = true
  try {
    models.value = await listAdminModels()
  } catch {
    ElMessage.error('模型列表加载失败')
  } finally {
    loading.value = false
  }
}

function openEdit(model: LlmModelConfig): void {
  editing.value = model
  form.displayName = model.displayName
  form.tier = model.tier ?? 'STANDARD'
  form.pointCost = model.pointCost
  form.enabled = model.enabled
  dialogVisible.value = true
}

function asModel(row: unknown): LlmModelConfig {
  return row as LlmModelConfig
}

async function save(): Promise<void> {
  if (!editing.value) {
    return
  }
  saving.value = true
  try {
    const updated = await updateAdminModel(editing.value.id, {
      displayName: form.displayName.trim(),
      tier: form.tier,
      pointCost: form.pointCost,
      enabled: form.enabled
    })
    const index = models.value.findIndex((model) => model.id === updated.id)
    if (index >= 0) {
      models.value[index] = updated
    }
    dialogVisible.value = false
    ElMessage.success('模型价格已更新')
  } catch {
    ElMessage.error('模型配置保存失败')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="ledger-page">
    <header class="ledger-header">
      <div>
        <div class="ledger-kicker">管理后台</div>
        <h1 class="ledger-title">模型与积分价格</h1>
        <p class="ledger-subtitle">
          普通与专家模式各使用对应分级的可用模型；专家模式按模型价格扣积分，每人每天限 3 次。
        </p>
      </div>
      <div class="ledger-date">共 {{ models.length }} 个模型</div>
    </header>

    <el-table v-loading="loading" :data="models" border>
      <el-table-column prop="providerName" label="供应商" width="150" />
      <el-table-column prop="modelCode" label="模型标识" min-width="180" />
      <el-table-column prop="displayName" label="展示名称" min-width="180" />
      <el-table-column label="分级" width="100">
        <template #default="{ row }">
          <el-tag :type="row.tier === 'EXPERT' ? 'warning' : 'success'">
            {{ row.tier === 'EXPERT' ? '专家' : '普通' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="pointCost" label="积分价格" width="110" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'">
            {{ row.enabled ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(asModel(row))">编辑</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" title="编辑模型价格" width="520px">
      <el-form label-width="100px">
        <el-form-item label="模型标识">
          <el-input :model-value="editing?.modelCode" disabled />
        </el-form-item>
        <el-form-item label="展示名称">
          <el-input v-model="form.displayName" maxlength="128" />
        </el-form-item>
        <el-form-item label="积分价格">
          <el-input-number v-model="form.pointCost" :min="1" :max="10000" />
        </el-form-item>
        <el-form-item label="模型分级">
          <el-radio-group v-model="form.tier">
            <el-radio-button label="STANDARD">普通</el-radio-button>
            <el-radio-button label="EXPERT">专家</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="启用状态">
          <el-switch
            v-model="form.enabled"
            inline-prompt
            active-text="启用"
            inactive-text="停用"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
