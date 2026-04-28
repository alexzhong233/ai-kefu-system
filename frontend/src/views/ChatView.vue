<template>
  <div class="chat-layout">
    <!-- Conversation List Panel -->
    <div class="conv-panel">
      <div class="conv-header">
        <h3>对话列表</h3>
        <el-select v-model="store.currentUser" @change="handleUserChange" size="small" class="user-select">
          <el-option v-for="user in users" :key="user.userId" :label="user.userName || user.userId" :value="user.userId" />
        </el-select>
      </div>
      <button class="new-chat-btn" @click="handleCreateConversation">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
        新建对话
      </button>
      <div class="conv-list">
        <div
          v-for="conv in store.conversations"
          :key="conv.conversationId"
          class="conv-item"
          :class="{ active: conv.conversationId === store.currentConversation }"
          @click="store.switchConversation(conv.conversationId)"
        >
          <div class="conv-item-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
          </div>
          <div class="conv-item-body">
            <div class="conv-item-title">{{ conv.title || '新对话' }}</div>
            <div class="conv-item-meta">{{ conv.messageCount || 0 }} 条消息</div>
          </div>
          <button class="conv-item-delete" @click.stop="handleDelete(conv.conversationId)">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
          </button>
        </div>
        <div v-if="store.conversations.length === 0" class="conv-empty">
          <p>暂无对话</p>
          <p class="conv-empty-hint">点击上方按钮新建</p>
        </div>
      </div>
    </div>

    <!-- Chat Main Area -->
    <div class="chat-main">
      <!-- Messages -->
      <div class="messages-area" ref="messagesArea">
        <div v-if="!store.currentConversation" class="welcome-screen">
          <div class="welcome-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
              <path d="M12 2a7 7 0 0 1 7 7c0 3-2 5.5-4.5 7L12 22l-2.5-6C7 14.5 5 12 5 9a7 7 0 0 1 7-7z"/>
              <circle cx="12" cy="9" r="2"/>
            </svg>
          </div>
          <h2>AI 智能客服</h2>
          <p>基于知识库的智能问答，请选择或新建对话开始</p>
          <div class="welcome-tips">
            <div class="tip-card" @click="quickStart('你好，请介绍一下你自己')">
              <span class="tip-icon">👋</span>
              <span>自我介绍</span>
            </div>
            <div class="tip-card" @click="quickStart('你能帮我做什么？')">
              <span class="tip-icon">💡</span>
              <span>功能介绍</span>
            </div>
            <div class="tip-card" @click="quickStart('常见问题有哪些？')">
              <span class="tip-icon">❓</span>
              <span>常见问题</span>
            </div>
          </div>
        </div>

        <template v-else>
          <div
            v-for="msg in store.messages"
            :key="msg.messageId"
            class="message-row"
            :class="msg.role"
          >
            <div class="message-avatar" v-if="msg.role === 'assistant'">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 2a7 7 0 0 1 7 7c0 3-2 5.5-4.5 7L12 22l-2.5-6C7 14.5 5 12 5 9a7 7 0 0 1 7-7z"/><circle cx="12" cy="9" r="2"/></svg>
            </div>
            <div class="message-bubble" :class="msg.role">
              <div class="message-role-label" v-if="msg.role === 'system'">系统消息</div>
              <div class="message-text">
                {{ msg.content }}
                <span v-if="msg.role === 'assistant' && msg.messageId?.startsWith('temp-ai-') && store.loading" class="typing-cursor">▎</span>
              </div>
              <div class="message-time" v-if="!msg.messageId?.startsWith('temp-')">{{ formatTime(msg.createdAt) }}</div>
            </div>
            <div class="message-avatar user-avatar" v-if="msg.role === 'user'">
              {{ store.currentUser?.charAt(0)?.toUpperCase() || 'U' }}
            </div>
          </div>
        </template>
      </div>

      <!-- Input Area -->
      <div class="input-area" v-if="store.currentConversation">
        <div class="input-wrapper">
          <textarea
            ref="inputRef"
            v-model="chatInput"
            placeholder="输入消息... (Enter 发送，Shift+Enter 换行)"
            @keydown.enter.exact.prevent="sendMessage"
            rows="1"
            class="chat-input"
          ></textarea>
          <button class="send-btn" :class="{ active: chatInput.trim() }" @click="sendMessage" :disabled="store.loading || !chatInput.trim()">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/></svg>
          </button>
        </div>
        <div class="input-hint">模型: DeepSeek-V4-Flash · 按下 Enter 发送</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick, watch } from 'vue'
import { useAppStore } from '../store'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'

const store = useAppStore()
const chatInput = ref('')
const messagesArea = ref(null)
const inputRef = ref(null)
const users = ref([])

onMounted(async () => {
  try {
    users.value = await store.loadUsers() || []
    await store.loadConversations()
  } catch (e) {
    console.error('Init error:', e)
  }
})

watch(() => store.messages.length, async () => {
  await nextTick()
  scrollToBottom()
})

watch(() => store.currentConversation, () => {
  chatInput.value = ''
})

async function handleUserChange(userId) {
  await store.switchUser(userId)
}

async function handleCreateConversation() {
  await store.createConversation()
}

async function handleDelete(conversationId) {
  try {
    await ElMessageBox.confirm('确定删除该对话？', '确认', { type: 'warning' })
    await store.deleteConversation(conversationId)
  } catch {}
}

async function sendMessage() {
  if (!chatInput.value.trim() || store.loading) return
  const msg = chatInput.value
  chatInput.value = ''
  try {
    await store.sendMessage(msg)
    scrollToBottom()
  } catch {
    ElMessage.error('发送失败')
  }
  nextTick(() => inputRef.value?.focus())
}

async function quickStart(text) {
  await handleCreateConversation()
  chatInput.value = text
}

function scrollToBottom() {
  if (messagesArea.value) {
    messagesArea.value.scrollTop = messagesArea.value.scrollHeight
  }
}

function formatTime(dateStr) {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}
</script>

<style scoped>
.chat-layout {
  display: flex;
  height: 100%;
}

/* Conversation Panel */
.conv-panel {
  width: 280px;
  min-width: 280px;
  background: var(--bg-card);
  border-right: 1px solid var(--border);
  display: flex;
  flex-direction: column;
}

.conv-header {
  padding: 20px 16px 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.conv-header h3 {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.user-select {
  width: 110px;
}

.new-chat-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin: 0 16px 12px;
  padding: 10px;
  border-radius: var(--radius-sm);
  border: 1px dashed var(--primary-light);
  background: rgba(99, 102, 241, 0.04);
  color: var(--primary);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: var(--transition);
}

.new-chat-btn svg {
  width: 16px;
  height: 16px;
}

.new-chat-btn:hover {
  background: rgba(99, 102, 241, 0.1);
  border-color: var(--primary);
}

.conv-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 8px 16px;
}

.conv-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: var(--transition);
  margin-bottom: 2px;
}

.conv-item:hover {
  background: var(--bg-input);
}

.conv-item.active {
  background: rgba(99, 102, 241, 0.08);
}

.conv-item-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: var(--bg-input);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.conv-item.active .conv-item-icon {
  background: rgba(99, 102, 241, 0.12);
  color: var(--primary);
}

.conv-item-icon svg {
  width: 18px;
  height: 18px;
  color: var(--text-secondary);
}

.conv-item.active .conv-item-icon svg {
  color: var(--primary);
}

.conv-item-body {
  flex: 1;
  min-width: 0;
}

.conv-item-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conv-item-meta {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 2px;
}

.conv-item-delete {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  border: none;
  background: transparent;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: var(--transition);
  flex-shrink: 0;
}

.conv-item:hover .conv-item-delete {
  opacity: 1;
}

.conv-item-delete svg {
  width: 14px;
  height: 14px;
  color: var(--text-muted);
}

.conv-item-delete:hover {
  background: rgba(239, 68, 68, 0.1);
}

.conv-item-delete:hover svg {
  color: var(--danger);
}

.conv-empty {
  text-align: center;
  padding: 40px 16px;
  color: var(--text-muted);
}

.conv-empty p {
  margin: 4px 0;
}

.conv-empty-hint {
  font-size: 12px;
}

/* Chat Main */
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: var(--bg-body);
  min-width: 0;
}

.messages-area {
  flex: 1;
  overflow-y: auto;
  padding: 24px 32px;
}

/* Welcome Screen */
.welcome-screen {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  text-align: center;
  padding: 40px;
}

.welcome-icon {
  width: 72px;
  height: 72px;
  border-radius: 20px;
  background: linear-gradient(135deg, var(--primary), var(--accent));
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 24px;
}

.welcome-icon svg {
  width: 40px;
  height: 40px;
  color: #fff;
}

.welcome-screen h2 {
  font-size: 28px;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 8px;
}

.welcome-screen > p {
  font-size: 15px;
  color: var(--text-secondary);
  margin-bottom: 32px;
}

.welcome-tips {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  justify-content: center;
}

.tip-card {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 20px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  cursor: pointer;
  font-size: 14px;
  color: var(--text-secondary);
  transition: var(--transition);
}

.tip-card:hover {
  border-color: var(--primary-light);
  color: var(--primary);
  box-shadow: var(--shadow);
}

.tip-icon {
  font-size: 18px;
}

/* Message Row */
.message-row {
  display: flex;
  gap: 12px;
  margin-bottom: 24px;
  animation: fadeInUp 0.3s ease;
}

.message-row.user {
  justify-content: flex-end;
}

.message-avatar {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: linear-gradient(135deg, var(--primary), var(--accent));
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.message-avatar svg {
  width: 20px;
  height: 20px;
  color: #fff;
}

.message-avatar.user-avatar {
  background: linear-gradient(135deg, #64748b, #475569);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
}

.message-bubble {
  max-width: 65%;
  padding: 12px 16px;
  border-radius: var(--radius);
  line-height: 1.7;
  font-size: 14px;
}

.message-bubble.user {
  background: linear-gradient(135deg, var(--primary), var(--primary-dark));
  color: #fff;
  border-bottom-right-radius: 4px;
}

.message-bubble.assistant {
  background: var(--bg-card);
  border: 1px solid var(--border);
  color: var(--text-primary);
  border-bottom-left-radius: 4px;
}

.message-bubble.system {
  background: var(--bg-input);
  border: 1px solid var(--border-light);
  color: var(--text-secondary);
  font-size: 13px;
  max-width: 80%;
}

.message-role-label {
  font-size: 12px;
  font-weight: 500;
  color: var(--text-muted);
  margin-bottom: 4px;
}

.message-text {
  white-space: pre-wrap;
  word-break: break-word;
}

.typing-cursor {
  animation: blink 0.8s infinite;
  color: var(--primary);
  font-weight: bold;
}

@keyframes blink {
  0%, 50% { opacity: 1; }
  51%, 100% { opacity: 0; }
}

.message-time {
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 6px;
  opacity: 0.7;
}

.message-bubble.user .message-time {
  color: rgba(255,255,255,0.6);
  text-align: right;
}

/* Input Area */
.input-area {
  padding: 16px 32px 20px;
  background: var(--bg-card);
  border-top: 1px solid var(--border);
}

.input-wrapper {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  background: var(--bg-input);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 8px 12px;
  transition: var(--transition);
}

.input-wrapper:focus-within {
  border-color: var(--primary-light);
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.1);
}

.chat-input {
  flex: 1;
  border: none;
  outline: none;
  resize: none;
  background: transparent;
  font-size: 14px;
  line-height: 1.6;
  color: var(--text-primary);
  max-height: 120px;
  font-family: inherit;
}

.chat-input::placeholder {
  color: var(--text-muted);
}

.send-btn {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  border: none;
  background: var(--border);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: not-allowed;
  transition: var(--transition);
  flex-shrink: 0;
}

.send-btn svg {
  width: 18px;
  height: 18px;
  color: #fff;
}

.send-btn.active {
  background: var(--primary);
  cursor: pointer;
}

.send-btn.active:hover {
  background: var(--primary-dark);
}

.input-hint {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 8px;
  text-align: center;
}
</style>
