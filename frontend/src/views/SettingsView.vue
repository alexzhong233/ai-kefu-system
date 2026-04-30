<template>
  <div class="settings-layout">
    <div class="page-header">
      <div>
        <h2>系统设置</h2>
        <p class="page-desc">配置 AI 模型供应商，支持 DashScope 和 OpenAI 兼容 API</p>
      </div>
    </div>

    <el-tabs v-model="activeTab" class="settings-tabs">
      <!-- 聊天模型 Tab -->
      <el-tab-pane label="聊天模型" name="chat">
        <div class="provider-list">
          <div v-for="p in chatProviders" :key="p.providerId" class="provider-card" :class="{ active: p.isActive }">
            <div class="provider-info">
              <div class="provider-name">
                <span class="status-dot" :class="{ on: p.isActive }"></span>
                {{ p.name }}
              </div>
              <div class="provider-meta">
                <el-tag size="small" :type="p.providerType === 'dashscope' ? '' : 'warning'">{{ p.providerType }}</el-tag>
                <span class="model-label">{{ p.modelName }}</span>
              </div>
            </div>
            <div class="provider-actions">
              <el-button v-if="!p.isActive" type="primary" size="small" @click="activateProvider(p)">激活</el-button>
              <el-button v-if="p.isActive" type="success" size="small" disabled>已激活</el-button>
              <el-button size="small" @click="editProvider(p)">编辑</el-button>
              <el-button size="small" type="danger" @click="deleteProvider(p)">删除</el-button>
            </div>
          </div>
          <el-button class="add-btn" @click="openAddDialog('chat')">+ 添加聊天模型配置</el-button>
        </div>
      </el-tab-pane>

      <!-- Embedding 模型 Tab -->
      <el-tab-pane label="Embedding 模型" name="embedding">
        <div class="provider-list">
          <div v-for="p in embeddingProviders" :key="p.providerId" class="provider-card" :class="{ active: p.isActive }">
            <div class="provider-info">
              <div class="provider-name">
                <span class="status-dot" :class="{ on: p.isActive }"></span>
                {{ p.name }}
              </div>
              <div class="provider-meta">
                <el-tag size="small" :type="p.providerType === 'dashscope' ? '' : 'warning'">{{ p.providerType }}</el-tag>
                <span class="model-label">{{ p.modelName }}</span>
                <span v-if="p.extraConfig?.dimension" class="dim-label">维度: {{ p.extraConfig.dimension }}</span>
              </div>
            </div>
            <div class="provider-actions">
              <el-button v-if="!p.isActive" type="primary" size="small" @click="activateProvider(p)">激活</el-button>
              <el-button v-if="p.isActive" type="success" size="small" disabled>已激活</el-button>
              <el-button size="small" @click="editProvider(p)">编辑</el-button>
              <el-button size="small" type="danger" @click="deleteProvider(p)">删除</el-button>
            </div>
          </div>
          <el-button class="add-btn" @click="openAddDialog('embedding')">+ 添加 Embedding 模型配置</el-button>
        </div>
      </el-tab-pane>

      <!-- Rerank 模型 Tab -->
      <el-tab-pane label="Rerank 模型" name="rerank">
        <div class="provider-list">
          <div v-for="p in rerankProviders" :key="p.providerId" class="provider-card" :class="{ active: p.isActive }">
            <div class="provider-info">
              <div class="provider-name">
                <span class="status-dot" :class="{ on: p.isActive }"></span>
                {{ p.name }}
              </div>
              <div class="provider-meta">
                <el-tag size="small" :type="p.providerType === 'dashscope' ? '' : 'warning'">{{ p.providerType }}</el-tag>
                <span class="model-label">{{ p.modelName }}</span>
              </div>
            </div>
            <div class="provider-actions">
              <el-button v-if="!p.isActive" type="primary" size="small" @click="activateProvider(p)">激活</el-button>
              <el-button v-if="p.isActive" type="success" size="small" disabled>已激活</el-button>
              <el-button size="small" @click="editProvider(p)">编辑</el-button>
              <el-button size="small" type="danger" @click="deleteProvider(p)">删除</el-button>
            </div>
          </div>
          <el-button class="add-btn" @click="openAddDialog('rerank')">+ 添加 Rerank 模型配置</el-button>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 添加/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="isEditing ? '编辑配置' : '添加配置'" width="520px" :close-on-click-modal="false">
      <el-form :model="form" label-width="100px" label-position="left">
        <el-form-item label="配置名称">
          <el-input v-model="form.name" placeholder="如: DashScope 聊天" />
        </el-form-item>
        <el-form-item label="供应商">
          <el-select v-model="form.providerType" style="width: 100%">
            <el-option label="DashScope" value="dashscope" />
            <el-option label="OpenAI 兼容" value="openai" />
          </el-select>
        </el-form-item>
        <el-form-item label="API Key">
          <el-input v-model="form.apiKey" type="password" show-password placeholder="输入 API Key" />
        </el-form-item>
        <el-form-item v-if="form.providerType === 'openai'" label="Base URL">
          <el-input v-model="form.baseUrl" placeholder="https://api.openai.com" />
        </el-form-item>
        <el-form-item label="模型名称">
          <el-input v-model="form.modelName" :placeholder="modelNamePlaceholder" />
        </el-form-item>

        <!-- 聊天模型额外参数 -->
        <template v-if="dialogModelType === 'chat'">
          <el-form-item label="Temperature">
            <el-slider v-model="form.temperature" :min="0" :max="2" :step="0.1" show-input />
          </el-form-item>
        </template>

        <!-- Embedding 模型额外参数 -->
        <template v-if="dialogModelType === 'embedding'">
          <el-form-item label="向量维度">
            <el-input-number v-model="form.dimension" :min="1" :max="8192" />
          </el-form-item>
        </template>
      </el-form>

      <template #footer>
        <el-button @click="testConnection" :loading="testing">测试连接</el-button>
        <el-button type="primary" @click="saveProvider" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'

const activeTab = ref('chat')
const providers = ref([])
const dialogVisible = ref(false)
const isEditing = ref(false)
const editingId = ref(null)
const dialogModelType = ref('chat')
const testing = ref(false)
const saving = ref(false)

const form = ref({
  name: '',
  providerType: 'dashscope',
  apiKey: '',
  baseUrl: '',
  modelName: '',
  temperature: 0.7,
  dimension: 1024
})

const chatProviders = computed(() => providers.value.filter(p => p.modelType === 'chat'))
const embeddingProviders = computed(() => providers.value.filter(p => p.modelType === 'embedding'))
const rerankProviders = computed(() => providers.value.filter(p => p.modelType === 'rerank'))

const modelNamePlaceholder = computed(() => {
  if (dialogModelType.value === 'chat') return form.value.providerType === 'dashscope' ? 'deepseek-v4-flash' : 'gpt-4o'
  if (dialogModelType.value === 'embedding') return form.value.providerType === 'dashscope' ? 'text-embedding-v3' : 'text-embedding-3-small'
  return 'gte-rerank'
})

async function loadProviders() {
  try {
    providers.value = await api.config.getProviders()
  } catch (e) {
    console.error('Failed to load providers:', e)
  }
}

function openAddDialog(modelType) {
  isEditing.value = false
  editingId.value = null
  dialogModelType.value = modelType
  form.value = {
    name: '',
    providerType: 'dashscope',
    apiKey: '',
    baseUrl: '',
    modelName: '',
    temperature: 0.7,
    dimension: 1024
  }
  dialogVisible.value = true
}

function editProvider(provider) {
  isEditing.value = true
  editingId.value = provider.id
  dialogModelType.value = provider.modelType
  form.value = {
    name: provider.name,
    providerType: provider.providerType,
    apiKey: provider.apiKey,
    baseUrl: provider.baseUrl || '',
    modelName: provider.modelName,
    temperature: provider.extraConfig?.temperature ?? 0.7,
    dimension: provider.extraConfig?.dimension ?? 1024
  }
  dialogVisible.value = true
}

async function saveProvider() {
  if (!form.value.name || !form.value.apiKey || !form.value.modelName) {
    ElMessage.warning('请填写必填项：名称、API Key、模型名称')
    return
  }

  saving.value = true
  try {
    const extraConfig = {}
    if (dialogModelType.value === 'chat') {
      extraConfig.temperature = form.value.temperature
    } else if (dialogModelType.value === 'embedding') {
      extraConfig.dimension = form.value.dimension
    }

    const payload = {
      name: form.value.name,
      providerType: form.value.providerType,
      modelType: dialogModelType.value,
      apiKey: form.value.apiKey,
      baseUrl: form.value.baseUrl || null,
      modelName: form.value.modelName,
      extraConfig
    }

    if (isEditing.value) {
      await api.config.updateProvider(editingId.value, payload)
      ElMessage.success('配置已更新')
    } else {
      await api.config.createProvider(payload)
      ElMessage.success('配置已添加')
    }

    dialogVisible.value = false
    await loadProviders()
  } catch (e) {
    ElMessage.error('保存失败: ' + (e.response?.data?.error || e.message))
  } finally {
    saving.value = false
  }
}

async function activateProvider(provider) {
  try {
    await api.config.activateProvider(provider.id)
    ElMessage.success(`已激活: ${provider.name}`)
    await loadProviders()
  } catch (e) {
    ElMessage.error('激活失败: ' + (e.response?.data?.error || e.message))
  }
}

async function deleteProvider(provider) {
  try {
    await ElMessageBox.confirm(`确定删除配置 "${provider.name}"？`, '确认删除', { type: 'warning' })
    await api.config.deleteProvider(provider.id)
    ElMessage.success('已删除')
    await loadProviders()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('删除失败: ' + (e.response?.data?.error || e.message))
    }
  }
}

async function testConnection() {
  testing.value = true
  try {
    const result = await api.config.testProvider({
      providerType: form.value.providerType,
      modelType: dialogModelType.value,
      apiKey: form.value.apiKey,
      baseUrl: form.value.baseUrl,
      modelName: form.value.modelName
    })
    if (result.success) {
      ElMessage.success('连接成功: ' + (result.response || result.message || ''))
    } else {
      ElMessage.error('连接失败: ' + result.error)
    }
  } catch (e) {
    ElMessage.error('测试失败: ' + (e.response?.data?.error || e.message))
  } finally {
    testing.value = false
  }
}

onMounted(loadProviders)
</script>

<style scoped>
.settings-layout {
  height: 100%;
  overflow-y: auto;
  padding: 28px 32px;
}

.page-header {
  margin-bottom: 24px;
}

.page-header h2 {
  font-size: 22px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 4px 0;
}

.page-desc {
  font-size: 14px;
  color: var(--text-secondary);
  margin: 0;
}

.settings-tabs {
  background: var(--bg-card);
  border-radius: var(--radius);
  padding: 20px 24px;
  border: 1px solid var(--border);
}

.provider-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 8px 0;
}

.provider-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border);
  background: var(--bg-body);
  transition: var(--transition);
}

.provider-card.active {
  border-color: var(--primary);
  background: rgba(99, 102, 241, 0.05);
}

.provider-name {
  font-size: 15px;
  font-weight: 500;
  color: var(--text-primary);
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--text-secondary);
}

.status-dot.on {
  background: var(--success);
  box-shadow: 0 0 6px rgba(16, 185, 129, 0.4);
}

.provider-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--text-secondary);
}

.model-label {
  font-family: monospace;
}

.dim-label {
  color: var(--text-secondary);
}

.provider-actions {
  display: flex;
  gap: 8px;
}

.add-btn {
  margin-top: 8px;
  width: 100%;
  border-style: dashed;
}
</style>
