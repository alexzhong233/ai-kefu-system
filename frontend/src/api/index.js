import axios from 'axios'

const apiClient = axios.create({
  baseURL: '/api',
  timeout: 60000,
  headers: {
    'Content-Type': 'application/json'
  }
})

apiClient.interceptors.response.use(
  response => response.data,
  error => {
    console.error('API Error:', error)
    return Promise.reject(error)
  }
)

export default {
  rag: {
    upload: (formData) => apiClient.post('/rag/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data; boundary=' + Math.random().toString(36).substring(2) }
    }),
    query: (params) => apiClient.post('/rag/query', params),
    getDocuments: () => apiClient.get('/rag/documents'),
    getDocument: (id) => apiClient.get(`/rag/documents/${id}`),
    getDocumentChunks: (id) => apiClient.get(`/rag/documents/${id}/chunks`),
    deleteDocument: (id) => apiClient.delete(`/rag/documents/${id}`),
    getStatistics: () => apiClient.get('/rag/statistics')
  },
  chat: {
    sendMessage: (params) => apiClient.post('/chat/send', params),
    streamMessage: (params, signal) => {
      return fetch('/api/chat/stream', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(params),
        signal
      })
    },
    getUsers: () => apiClient.get('/chat/users'),
    createConversation: (userId) => apiClient.post('/chat/conversations', { userId }),
    getConversations: (userId) => apiClient.get('/chat/conversations', { params: { userId } }),
    getMessages: (conversationId) => apiClient.get(`/chat/conversations/${conversationId}/messages`),
    deleteConversation: (conversationId) => apiClient.delete(`/chat/conversations/${conversationId}`)
  }
}
