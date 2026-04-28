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

  async function sendMessage(content) {
    if (!content.trim()) return

    loading.value = true
    streamingMessage.value = ''

    // 立即添加用户消息到界面
    const tempUserMsg = {
      messageId: 'temp-' + Date.now(),
      conversationId: currentConversation.value,
      role: 'user',
      content: content,
      createdAt: new Date().toISOString()
    }
    messages.value.push(tempUserMsg)

    // 添加 AI 占位消息
    const tempAssistantMsg = {
      messageId: 'temp-ai-' + Date.now(),
      conversationId: currentConversation.value,
      role: 'assistant',
      content: '',
      createdAt: new Date().toISOString()
    }
    messages.value.push(tempAssistantMsg)

    try {
      const response = await api.chat.streamMessage({
        userId: currentUser.value,
        conversationId: currentConversation.value,
        message: content
      })

      if (!response.ok) {
        throw new Error('Stream request failed')
      }

      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      let currentEvent = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        for (const line of lines) {
          const trimmedLine = line.trim()
          if (!trimmedLine) continue

          if (trimmedLine.startsWith('event:')) {
            currentEvent = trimmedLine.slice(6).trim()
          } else if (trimmedLine.startsWith('data:')) {
            const data = trimmedLine.slice(5).trim()
            if (!data) continue

            try {
              if (currentEvent === 'meta') {
                const parsed = JSON.parse(data)
                if (parsed.conversationId) {
                  currentConversation.value = parsed.conversationId
                }
              } else if (currentEvent === 'token') {
                streamingMessage.value += data
                tempAssistantMsg.content = streamingMessage.value
              } else if (currentEvent === 'done') {
                // 流结束，刷新消息列表
                await loadMessages()
                await loadConversations()
              } else if (currentEvent === 'error') {
                tempAssistantMsg.content = data || '发生错误'
                throw new Error(data)
              }
            } catch (parseError) {
              // JSON 解析失败时，将数据作为 token 处理
              if (currentEvent === 'token' || !currentEvent) {
                streamingMessage.value += data
                tempAssistantMsg.content = streamingMessage.value
              } else {
                console.warn('Failed to parse event data:', currentEvent, data)
              }
            }
          }
        }
      }

      // 处理残留 buffer
      if (buffer.trim()) {
        const remainingLines = buffer.split('\n')
        for (const line of remainingLines) {
          const trimmedLine = line.trim()
          if (trimmedLine.startsWith('data:') && currentEvent === 'token') {
            const data = trimmedLine.slice(5).trim()
            if (data) {
              streamingMessage.value += data
              tempAssistantMsg.content = streamingMessage.value
            }
          }
        }
      }
    } catch (error) {
      console.error('Error sending message:', error)
      tempAssistantMsg.content = '发送失败，请重试'
      throw error
    } finally {
      loading.value = false
      streamingMessage.value = ''
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
    deleteConversation,
    loadDocuments,
    uploadDocument,
    deleteDocument,
    queryRag,
    loadStatistics
  }
})
