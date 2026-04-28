<template>
  <div class="knowledge-layout">
    <!-- Header -->
    <div class="page-header">
      <div>
        <h2>知识库管理</h2>
        <p class="page-desc">上传文档构建知识库，AI 将基于知识库内容进行智能问答</p>
      </div>
      <button class="upload-trigger" @click="showUploadDialog = true">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>
        上传文档
      </button>
    </div>

    <!-- Stats Cards -->
    <div class="stats-row">
      <div class="stat-card">
        <div class="stat-icon blue">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>
        </div>
        <div class="stat-body">
          <div class="stat-value">{{ store.statistics.totalDocuments || 0 }}</div>
          <div class="stat-label">总文档数</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon purple">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/></svg>
        </div>
        <div class="stat-body">
          <div class="stat-value">{{ store.statistics.totalChunks || 0 }}</div>
          <div class="stat-label">总分块数</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon green">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>
        </div>
        <div class="stat-body">
          <div class="stat-value">{{ store.statistics.completedDocuments || 0 }}</div>
          <div class="stat-label">已完成</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon red">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>
        </div>
        <div class="stat-body">
          <div class="stat-value">{{ store.statistics.failedDocuments || 0 }}</div>
          <div class="stat-label">失败</div>
        </div>
      </div>
    </div>

    <!-- Content Area -->
    <div class="content-grid">
      <!-- Documents Table -->
      <div class="card documents-card">
        <div class="card-header">
          <h3>文档列表</h3>
          <button class="icon-btn" @click="refreshDocs" title="刷新">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" :class="{ spinning: refreshing }"><polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/></svg>
          </button>
        </div>
        <div class="card-body">
          <div v-if="store.documents.length === 0" class="empty-state">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" class="empty-icon"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>
            <p>暂无文档</p>
            <span>点击上方按钮上传文档</span>
          </div>
          <table v-else class="doc-table">
            <thead>
              <tr>
                <th>文件名</th>
                <th>类型</th>
                <th>分块数</th>
                <th>状态</th>
                <th>上传时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="doc in store.documents" :key="doc.documentId">
                <td class="doc-name">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>
                  {{ doc.fileName }}
                </td>
                <td><span class="file-type-badge">{{ doc.fileType }}</span></td>
                <td>{{ doc.chunkCount }}</td>
                <td>
                  <span class="status-tag" :class="doc.status">
                    {{ statusLabel(doc.status) }}
                  </span>
                </td>
                <td class="time-cell">{{ formatDate(doc.createdAt) }}</td>
                <td class="action-cell">
                  <button class="action-btn" @click="viewChunks(doc)" title="查看分块">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
                  </button>
                  <button class="action-btn danger" @click="handleDelete(doc)" title="删除">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- Query Test -->
      <div class="card query-card">
        <div class="card-header">
          <h3>查询测试</h3>
        </div>
        <div class="card-body">
          <div class="query-form">
            <textarea v-model="queryText" placeholder="输入查询内容，测试知识库检索效果...\n提示：如果未找到结果，可以尝试降低阈值或增加 Top K 值" rows="4" class="query-input"></textarea>
            <div class="query-params">
              <div class="param-group">
                <label>Top K</label>
                <input type="number" v-model.number="queryTopK" min="1" max="20" class="param-input" />
              </div>
              <div class="param-group">
                <label>阈值</label>
                <input type="number" v-model.number="queryThreshold" min="0" max="1" step="0.1" class="param-input" />
              </div>
              <button class="query-btn" @click="handleQuery" :disabled="store.loading">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
                {{ store.loading ? '查询中...' : '查询' }}
              </button>
            </div>
          </div>

          <div v-if="store.ragQueryResults.length > 0" class="query-results">
            <h4>查询结果 ({{ store.ragQueryResults.length }})</h4>
            <div v-for="(result, index) in store.ragQueryResults" :key="index" class="result-item">
              <div class="result-header">
                <span class="result-index">#{{ index + 1 }}</span>
                <span class="result-similarity" :class="similarityLevel(result.similarity)">
                  {{ (result.similarity * 100).toFixed(1) }}%
                </span>
              </div>
              <div class="result-content">{{ result.content }}</div>
              <div class="result-meta">
                <span>Chunk #{{ result.chunkIndex }}</span>
                <span v-if="result.metadata && result.metadata.fileName" style="margin-left: 12px;">文件: {{ result.metadata.fileName }}</span>
              </div>
            </div>
          </div>
          
          <div v-else-if="queryText.trim() && !store.loading" class="empty-state" style="margin-top: 20px;">
            <p>未找到相关结果</p>
            <span style="font-size: 12px;">尝试降低阈值或增加 Top K 值</span>
          </div>
        </div>
      </div>
    </div>

    <!-- Upload Dialog -->
    <el-dialog v-model="showUploadDialog" title="上传文档" width="480px" :close-on-click-modal="false">
      <div class="upload-area">
        <el-upload
          ref="uploadRef"
          :auto-upload="false"
          :limit="1"
          accept=".txt,.md,.html,.htm,.pdf,.doc,.docx"
          :on-change="handleFileChange"
          drag
        >
          <div class="upload-inner">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" class="upload-icon"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>
            <p>拖拽文件到此处，或 <em>点击上传</em></p>
            <span>支持 .txt, .md, .html, .htm 格式</span>
          </div>
        </el-upload>
      </div>
      <template #footer>
        <el-button @click="showUploadDialog = false">取消</el-button>
        <el-button type="primary" @click="handleUpload" :loading="store.loading">上传</el-button>
      </template>
    </el-dialog>

    <!-- Chunks Dialog -->
    <el-dialog v-model="showChunksDialog" title="文档分块" width="700px">
      <div class="chunks-list">
        <div v-for="chunk in currentChunks" :key="chunk.chunkId" class="chunk-item">
          <div class="chunk-header">
            <span class="chunk-index">Chunk #{{ chunk.chunkIndex }}</span>
          </div>
          <div class="chunk-content">{{ chunk.content }}</div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useAppStore } from '../store'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'

const store = useAppStore()

const showUploadDialog = ref(false)
const showChunksDialog = ref(false)
const uploadRef = ref(null)
const selectedFile = ref(null)
const queryText = ref('')
const queryTopK = ref(10) // 增加默认返回数量
const queryThreshold = ref(0.5) // 降低默认阈值以提高召回率
const currentChunks = ref([])
const refreshing = ref(false)

onMounted(async () => {
  await store.loadDocuments()
  await store.loadStatistics()
})

function handleFileChange(file) {
  selectedFile.value = file.raw
}

async function handleUpload() {
  if (!selectedFile.value) {
    ElMessage.warning('请选择文件')
    return
  }
  try {
    await store.uploadDocument(selectedFile.value)
    ElMessage.success('文档上传成功')
    showUploadDialog.value = false
    selectedFile.value = null
    uploadRef.value?.clearFiles()
  } catch (error) {
    ElMessage.error('上传失败: ' + error.message)
  }
}

async function handleDelete(doc) {
  try {
    await ElMessageBox.confirm(`确定删除 "${doc.fileName}" 吗？`, '确认删除', { type: 'warning' })
    await store.deleteDocument(doc.documentId)
    ElMessage.success('已删除')
  } catch {}
}

async function viewChunks(doc) {
  try {
    const chunks = await api.rag.getDocumentChunks(doc.documentId)
    currentChunks.value = chunks
    showChunksDialog.value = true
  } catch {
    ElMessage.error('获取分块失败')
  }
}

async function handleQuery() {
  if (!queryText.value.trim()) {
    ElMessage.warning('请输入查询内容')
    return
  }
  try {
    await store.queryRag(queryText.value, queryTopK.value, queryThreshold.value)
  } catch {
    ElMessage.error('查询失败')
  }
}

async function refreshDocs() {
  refreshing.value = true
  await store.loadDocuments()
  await store.loadStatistics()
  setTimeout(() => { refreshing.value = false }, 600)
}

function formatDate(dateStr) {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

function statusLabel(s) {
  const map = { completed: '已完成', processing: '处理中', failed: '失败', pending: '待处理' }
  return map[s] || s
}

function similarityLevel(s) {
  if (s >= 0.85) return 'high'
  if (s >= 0.7) return 'medium'
  return 'low'
}
</script>

<style scoped>
.knowledge-layout {
  height: 100%;
  overflow-y: auto;
  padding: 28px 32px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
}

.page-header h2 {
  font-size: 22px;
  font-weight: 700;
  color: var(--text-primary);
}

.page-desc {
  font-size: 14px;
  color: var(--text-secondary);
  margin-top: 4px;
}

.upload-trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  border-radius: var(--radius-sm);
  border: none;
  background: var(--primary);
  color: #fff;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: var(--transition);
}

.upload-trigger svg {
  width: 16px;
  height: 16px;
}

.upload-trigger:hover {
  background: var(--primary-dark);
}

/* Stats Row */
.stats-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

.stat-card {
  background: var(--bg-card);
  border-radius: var(--radius);
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  box-shadow: var(--shadow-sm);
  border: 1px solid var(--border-light);
}

.stat-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-icon svg {
  width: 22px;
  height: 22px;
}

.stat-icon.blue { background: rgba(59, 130, 246, 0.1); color: var(--info); }
.stat-icon.purple { background: rgba(139, 92, 246, 0.1); color: var(--accent); }
.stat-icon.green { background: rgba(16, 185, 129, 0.1); color: var(--success); }
.stat-icon.red { background: rgba(239, 68, 68, 0.1); color: var(--danger); }

.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: var(--text-primary);
}

.stat-label {
  font-size: 13px;
  color: var(--text-secondary);
  margin-top: 2px;
}

/* Content Grid */
.content-grid {
  display: grid;
  grid-template-columns: 1fr 380px;
  gap: 20px;
  align-items: start;
}

.card {
  background: var(--bg-card);
  border-radius: var(--radius);
  border: 1px solid var(--border-light);
  box-shadow: var(--shadow-sm);
  overflow: hidden;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-light);
}

.card-header h3 {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

.card-body {
  padding: 16px 20px;
}

/* Documents Table */
.doc-table {
  width: 100%;
  border-collapse: collapse;
}

.doc-table th {
  text-align: left;
  padding: 10px 12px;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  border-bottom: 1px solid var(--border-light);
}

.doc-table td {
  padding: 12px 12px;
  font-size: 14px;
  color: var(--text-primary);
  border-bottom: 1px solid var(--border-light);
}

.doc-name {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 500;
}

.doc-name svg {
  width: 16px;
  height: 16px;
  color: var(--primary-light);
  flex-shrink: 0;
}

.file-type-badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
  background: var(--bg-input);
  color: var(--text-secondary);
  text-transform: uppercase;
}

.status-tag {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 500;
}

.status-tag.completed { background: rgba(16, 185, 129, 0.1); color: var(--success); }
.status-tag.processing { background: rgba(245, 158, 11, 0.1); color: var(--warning); }
.status-tag.failed { background: rgba(239, 68, 68, 0.1); color: var(--danger); }
.status-tag.pending { background: var(--bg-input); color: var(--text-muted); }

.time-cell {
  color: var(--text-muted);
  font-size: 13px;
}

.action-cell {
  display: flex;
  gap: 6px;
}

.action-btn {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  border: none;
  background: transparent;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: var(--transition);
}

.action-btn svg {
  width: 16px;
  height: 16px;
  color: var(--text-muted);
}

.action-btn:hover {
  background: var(--bg-input);
}

.action-btn:hover svg {
  color: var(--primary);
}

.action-btn.danger:hover {
  background: rgba(239, 68, 68, 0.1);
}

.action-btn.danger:hover svg {
  color: var(--danger);
}

.icon-btn {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  border: none;
  background: transparent;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: var(--transition);
}

.icon-btn svg {
  width: 16px;
  height: 16px;
  color: var(--text-muted);
}

.icon-btn:hover svg {
  color: var(--primary);
}

.spinning {
  animation: spin 0.6s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.empty-state {
  text-align: center;
  padding: 48px 20px;
  color: var(--text-muted);
}

.empty-icon {
  width: 48px;
  height: 48px;
  margin: 0 auto 12px;
}

.empty-state p {
  font-size: 15px;
  font-weight: 500;
  margin-bottom: 4px;
  color: var(--text-secondary);
}

.empty-state span {
  font-size: 13px;
}

/* Query Card */
.query-input {
  width: 100%;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  padding: 10px 14px;
  font-size: 14px;
  resize: none;
  background: var(--bg-input);
  color: var(--text-primary);
  font-family: inherit;
  line-height: 1.6;
  transition: var(--transition);
}

.query-input:focus {
  outline: none;
  border-color: var(--primary-light);
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.1);
}

.query-params {
  display: flex;
  gap: 12px;
  margin-top: 12px;
  align-items: flex-end;
}

.param-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.param-group label {
  font-size: 12px;
  font-weight: 500;
  color: var(--text-secondary);
}

.param-input {
  width: 70px;
  padding: 6px 10px;
  border: 1px solid var(--border);
  border-radius: 6px;
  font-size: 14px;
  color: var(--text-primary);
  background: var(--bg-input);
}

.param-input:focus {
  outline: none;
  border-color: var(--primary-light);
}

.query-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 18px;
  border-radius: var(--radius-sm);
  border: none;
  background: var(--primary);
  color: #fff;
  font-size: 14px;
  cursor: pointer;
  transition: var(--transition);
  margin-left: auto;
}

.query-btn svg {
  width: 16px;
  height: 16px;
}

.query-btn:hover:not(:disabled) {
  background: var(--primary-dark);
}

.query-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* Query Results */
.query-results {
  margin-top: 20px;
  border-top: 1px solid var(--border-light);
  padding-top: 16px;
}

.query-results h4 {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 12px;
}

.result-item {
  padding: 12px;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-sm);
  margin-bottom: 10px;
  background: var(--bg-input);
}

.result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.result-index {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-muted);
}

.result-similarity {
  font-size: 12px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 10px;
}

.result-similarity.high { background: rgba(16, 185, 129, 0.1); color: var(--success); }
.result-similarity.medium { background: rgba(245, 158, 11, 0.1); color: var(--warning); }
.result-similarity.low { background: rgba(239, 68, 68, 0.1); color: var(--danger); }

.result-content {
  font-size: 13px;
  line-height: 1.6;
  color: var(--text-primary);
  max-height: 80px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.result-meta {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 6px;
}

/* Upload Dialog */
.upload-area :deep(.el-upload-dragger) {
  border: 2px dashed var(--border);
  border-radius: var(--radius);
  background: var(--bg-input);
  padding: 32px;
}

.upload-inner {
  text-align: center;
}

.upload-icon {
  width: 40px;
  height: 40px;
  color: var(--primary-light);
  margin-bottom: 12px;
}

.upload-inner p {
  font-size: 14px;
  color: var(--text-secondary);
}

.upload-inner em {
  color: var(--primary);
  font-style: normal;
}

.upload-inner span {
  font-size: 12px;
  color: var(--text-muted);
  display: block;
  margin-top: 6px;
}

/* Chunks */
.chunks-list {
  max-height: 500px;
  overflow-y: auto;
}

.chunk-item {
  padding: 12px 0;
  border-bottom: 1px solid var(--border-light);
}

.chunk-item:last-child {
  border-bottom: none;
}

.chunk-header {
  margin-bottom: 6px;
}

.chunk-index {
  font-size: 12px;
  font-weight: 600;
  color: var(--primary);
}

.chunk-content {
  font-size: 13px;
  line-height: 1.6;
  color: var(--text-primary);
  white-space: pre-wrap;
  max-height: 120px;
  overflow: hidden;
}
</style>
