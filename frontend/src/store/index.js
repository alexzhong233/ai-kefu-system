import { defineStore } from 'pinia'
import { ref } from 'vue'
import api from '../api'

export const useAppStore = defineStore('app', () => {
  const currentUser = ref('user1')
  const currentConversation = ref(null)
  const conversations = ref([])
  const messages = ref([])
  const documents = ref([])
  const ragQueryResults = ref([])
  const loading = ref(false)
  const streamingMessage = ref('')
  const statistics = ref({
    totalDocuments: 0,
    totalChunks: 0,
    completedDocuments: 0,
    failedDocuments: 0
  })

  // 用于取消流式请求
  let abortController = null

  // 逐字显示队列
  const tokenQueue = []
  let isFlushing = false
  const TOKEN_FLUSH_INTERVAL = 100 // 每个字符的显示间隔(ms)，可调大看更慢的效果

  async function loadUsers() {
    try {
      const users = await api.chat.getUsers()
      return users
    } catch (error) {
      console.error('Error loading users:', error)
      return []
    }
  }

  async function switchUser(userId) {
    currentUser.value = userId
    currentConversation.value = null
    messages.value = []
    await loadConversations()
  }

  async function loadConversations() {
    try {
      conversations.value = await api.chat.getConversations(currentUser.value)
    } catch (error) {
      console.error('Error loading conversations:', error)
    }
  }

  async function createConversation() {
    try {
      const result = await api.chat.createConversation(currentUser.value)
      await loadConversations()
      await switchConversation(result.conversationId)
    } catch (error) {
      console.error('Error creating conversation:', error)
    }
  }

  async function switchConversation(conversationId) {
    currentConversation.value = conversationId
    await loadMessages()
  }

  async function loadMessages() {
    if (!currentConversation.value) {
      messages.value = []
      return
    }
    try {
      messages.value = await api.chat.getMessages(currentConversation.value)
    } catch (error) {
      console.error('Error loading messages:', error)
    }
  }

  /**
   * 逐字消费 token 队列
   */
  function flushTokenQueue(tempAssistantMsg) {
    if (isFlushing) return
    isFlushing = true
    const tick = () => {
      if (tokenQueue.length > 0) {
        const token = tokenQueue.shift()
        streamingMessage.value += token
        tempAssistantMsg.content = streamingMessage.value
        setTimeout(tick, TOKEN_FLUSH_INTERVAL)
      } else {
        isFlushing = false
      }
    }
    tick()
  }

  /**
   * 取消当前流式请求
   */
  function cancelStream() {
    if (abortController) {
      abortController.abort()
      abortController = null
    }
    // 清空 token 队列
    tokenQueue.length = 0
    isFlushing = false
    loading.value = false
    streamingMessage.value = ''
  }

  /**
   * 解析 SSE 文本流，正确处理跨 chunk 的事件边界
   */
  function parseSSEChunks(chunk, state) {
    state.buffer += chunk
    const events = []
    const lines = state.buffer.split('\n')
    // 最后一个元素可能是不完整的行，保留在 buffer 中
    state.buffer = lines.pop() || ''

    let currentEvent = state.currentEvent

    for (const line of lines) {
      const trimmed = line.trim()
      if (!trimmed) {
        // 空行表示事件结束
        if (state.currentData.length > 0) {
          events.push({ event: currentEvent, data: state.currentData.join('\n') })
          state.currentData = []
          currentEvent = ''
        }
        continue
      }
      if (trimmed.startsWith('event:')) {
        currentEvent = trimmed.slice(6).trim()
        state.currentEvent = currentEvent
      } else if (trimmed.startsWith('data:')) {
        state.currentData.push(trimmed.slice(5).trim())
      } else if (trimmed.startsWith('id:')) {
        state.lastEventId = trimmed.slice(3).trim()
      }
      // 忽略其他 SSE 字段（retry:, comment 等）
    }
    state.currentEvent = currentEvent
    return events
  }

  async function sendMessage(content) {
    if (!content.trim()) return

    loading.value = true
    streamingMessage.value = ''

    // 取消之前的流式请求
    if (abortController) {
      abortController.abort()
    }
    abortController = new AbortController()

    // 立即添加用户消息到界面
    const tempUserMsgId = 'temp-' + Date.now()
    messages.value.push({
      messageId: tempUserMsgId,
      conversationId: currentConversation.value,
      role: 'user',
      content: content,
      createdAt: new Date().toISOString()
    })
    // 从响应式数组中取回代理引用，确保后续修改能触发视图更新
    const tempUserMsg = messages.value[messages.value.length - 1]

    // 添加 AI 占位消息
    const tempAiMsgId = 'temp-ai-' + Date.now()
    messages.value.push({
      messageId: tempAiMsgId,
      conversationId: currentConversation.value,
      role: 'assistant',
      content: '',
      createdAt: new Date().toISOString()
    })
    const tempAssistantMsg = messages.value[messages.value.length - 1]

    try {
      const response = await api.chat.streamMessage({
        userId: currentUser.value,
        conversationId: currentConversation.value,
        message: content
      }, abortController.signal)

      if (!response.ok) {
        throw new Error(`Stream request failed: ${response.status}`)
      }

      const reader = response.body.getReader()
      const decoder = new TextDecoder()

      // SSE 解析状态
      const sseState = {
        buffer: '',
        currentEvent: '',
        currentData: [],
        lastEventId: ''
      }

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        const textChunk = decoder.decode(value, { stream: true })
        const events = parseSSEChunks(textChunk, sseState)

        for (const evt of events) {
          try {
            if (evt.event === 'meta') {
              const parsed = JSON.parse(evt.data)
              if (parsed.conversationId) {
                // 更新所有临时消息的 conversationId
                tempUserMsg.conversationId = parsed.conversationId
                tempAssistantMsg.conversationId = parsed.conversationId
                currentConversation.value = parsed.conversationId
              }
            } else if (evt.event === 'rag_sources') {
              // RAG 引用来源，可扩展显示
              // const sources = JSON.parse(evt.data)
            } else if (evt.event === 'token') {
              // token 数据可能是纯文本，不需要 JSON 解析
              const token = evt.data
              tokenQueue.push(token)
              flushTokenQueue(tempAssistantMsg)
            } else if (evt.event === 'done') {
              // 等待 token 队列消费完毕
              const waitForFlush = () => {
                return new Promise(resolve => {
                  if (tokenQueue.length === 0 && !isFlushing) {
                    resolve()
                  } else {
                    const check = () => {
                      if (tokenQueue.length === 0 && !isFlushing) {
                        resolve()
                      } else {
                        setTimeout(check, 20)
                      }
                    }
                    setTimeout(check, 20)
                  }
                })
              }
              await waitForFlush()
              const parsed = JSON.parse(evt.data)
              // 用真实消息替换临时 AI 消息
              const idx = messages.value.findIndex(m => m.messageId === tempAiMsgId)
              if (idx !== -1) {
                messages.value[idx] = {
                  messageId: parsed.messageId,
                  conversationId: parsed.conversationId,
                  role: 'assistant',
                  content: parsed.content || streamingMessage.value,
                  createdAt: new Date().toISOString()
                }
              }
              // 替换临时用户消息
              const userIdx = messages.value.findIndex(m => m.messageId === tempUserMsg.messageId)
              if (userIdx !== -1) {
                // 重新加载以获取真实的 messageId
                loadMessages()
              }
              // 刷新对话列表（标题可能更新了）
              loadConversations()
            } else if (evt.event === 'error') {
              const errorMsg = evt.data || '发生错误'
              tempAssistantMsg.content = errorMsg
              throw new Error(errorMsg)
            }
          } catch (parseError) {
            if (parseError.message && parseError.message !== evt.data) {
              throw parseError
            }
            // 如果 JSON 解析失败且是 token 事件，作为纯文本处理
            if (evt.event === 'token' || !evt.event) {
              tokenQueue.push(evt.data)
              flushTokenQueue(tempAssistantMsg)
            }
          }
        }
      }

      // 处理 buffer 中残留的未完成事件
      if (sseState.buffer.trim()) {
        const remainingText = sseState.buffer.trim()
        if (remainingText.startsWith('data:') && sseState.currentEvent === 'token') {
          const data = remainingText.slice(5).trim()
          if (data) {
            tokenQueue.push(data)
            flushTokenQueue(tempAssistantMsg)
          }
        }
      }
    } catch (error) {
      if (error.name === 'AbortError') {
        // 用户主动取消，不显示错误
        return
      }
      console.error('Error sending message:', error)
      tempAssistantMsg.content = '发送失败，请重试'
      throw error
    } finally {
      tokenQueue.length = 0
      isFlushing = false
      loading.value = false
      streamingMessage.value = ''
      abortController = null
    }
  }

  async function deleteConversation(conversationId) {
    try {
      await api.chat.deleteConversation(conversationId)
      if (currentConversation.value === conversationId) {
        currentConversation.value = null
        messages.value = []
      }
      await loadConversations()
    } catch (error) {
      console.error('Error deleting conversation:', error)
    }
  }

  async function loadDocuments() {
    try {
      documents.value = await api.rag.getDocuments()
    } catch (error) {
      console.error('Error loading documents:', error)
    }
  }

  async function uploadDocument(file) {
    loading.value = true
    try {
      const formData = new FormData()
      formData.append('file', file)
      const result = await api.rag.upload(formData)
      await loadDocuments()
      await loadStatistics()
      return result
    } catch (error) {
      console.error('Error uploading document:', error)
      throw error
    } finally {
      loading.value = false
    }
  }

  async function deleteDocument(documentId) {
    try {
      await api.rag.deleteDocument(documentId)
      await loadDocuments()
      await loadStatistics()
    } catch (error) {
      console.error('Error deleting document:', error)
    }
  }

  async function queryRag(query, topK = 5, threshold = 0.7) {
    try {
      ragQueryResults.value = await api.rag.query({
        query,
        topK,
        threshold
      })
      return ragQueryResults.value
    } catch (error) {
      console.error('Error querying RAG:', error)
      throw error
    }
  }

  async function loadStatistics() {
    try {
      statistics.value = await api.rag.getStatistics()
    } catch (error) {
      console.error('Error loading statistics:', error)
    }
  }

  return {
    currentUser,
    currentConversation,
    conversations,
    messages,
    documents,
    ragQueryResults,
    loading,
    streamingMessage,
    statistics,
    loadUsers,
    switchUser,
    loadConversations,
    createConversation,
    switchConversation,
    loadMessages,
    sendMessage,
    cancelStream,
    deleteConversation,
    loadDocuments,
    uploadDocument,
    deleteDocument,
    queryRag,
    loadStatistics
  }
})
