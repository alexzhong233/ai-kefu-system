# 接口测试指南

## 启动服务

```bash
# 后端
cd backend && mvn spring-boot:run

# 前端（可选，仅浏览器测试需要）
cd frontend && npm run dev
```

## API 测试

### 1. 获取用户列表

```bash
curl http://localhost:8080/api/chat/users
```

### 2. 创建对话

```bash
curl -X POST http://localhost:8080/api/chat/conversations \
  -H "Content-Type: application/json" \
  -d '{"userId": "user1"}'
```

### 3. 发送消息（非流式）

```bash
curl -X POST http://localhost:8080/api/chat/send \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user1",
    "conversationId": "your-conversation-id",
    "message": "你好"
  }'
```

### 4. 发送消息（SSE 流式）

```bash
curl -N -X POST http://localhost:8080/api/chat/stream \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user1",
    "conversationId": "your-conversation-id",
    "message": "你好"
  }'
```

SSE 事件格式：

```
event: meta
data: {"conversationId":"xxx","isNew":true}

event: token
data: 你

event: token
data: 好

event: done
data: {"messageId":"xxx","conversationId":"xxx","content":"你好！有什么可以帮你的？"}

event: error
data: 错误信息（仅在异常时出现）
```

### 5. 上传文档

```bash
curl -X POST http://localhost:8080/api/rag/upload \
  -F "file=@test.txt"
```

支持的文件格式：.txt, .md, .html, .pdf, .doc, .docx 等（通过 Apache Tika 解析）。

### 6. RAG 查询

```bash
curl -X POST http://localhost:8080/api/rag/query \
  -H "Content-Type: application/json" \
  -d '{
    "query": "测试问题",
    "topK": 5,
    "threshold": 0.5
  }'
```

### 7. 获取文档列表

```bash
curl http://localhost:8080/api/rag/documents
```

### 8. 获取统计信息

```bash
curl http://localhost:8080/api/rag/statistics
```

### 9. 删除文档

```bash
curl -X DELETE http://localhost:8080/api/rag/documents/{documentId}
```

### 10. 删除对话

```bash
curl -X DELETE http://localhost:8080/api/chat/conversations/{conversationId}
```

## 完整接口清单

### RAG 管理

| 方法 | 路径 | 说明 | 参数 |
|------|------|------|------|
| POST | `/api/rag/upload` | 上传文档 | file (multipart) |
| POST | `/api/rag/query` | 相似度查询 | {query, topK, threshold} |
| GET | `/api/rag/documents` | 文档列表 | 无 |
| GET | `/api/rag/documents/{id}` | 文档详情 | documentId |
| GET | `/api/rag/documents/{id}/chunks` | 文档分块 | documentId |
| DELETE | `/api/rag/documents/{id}` | 删除文档 | documentId |
| GET | `/api/rag/statistics` | 统计信息 | 无 |

### 对话管理

| 方法 | 路径 | 说明 | 参数 |
|------|------|------|------|
| POST | `/api/chat/send` | 发送消息（非流式） | {userId, conversationId, message} |
| POST | `/api/chat/stream` | 发送消息（SSE 流式） | {userId, conversationId, message} |
| GET | `/api/chat/users` | 用户列表 | 无 |
| POST | `/api/chat/conversations` | 创建对话 | {userId} |
| GET | `/api/chat/conversations` | 对话列表 | ?userId=xxx |
| GET | `/api/chat/conversations/{id}/messages` | 消息列表 | conversationId |
| DELETE | `/api/chat/conversations/{id}` | 删除对话 | conversationId |

## 前端调试

1. 打开 http://localhost:3000
2. 按 F12 打开开发者工具
3. 切换到 Network 标签
4. 操作页面并观察 API 请求

### SSE 流式响应调试

在浏览器 Network 标签中，SSE 请求会显示为 `EventStream` 类型，可查看实时推送的 token。

## 常见问题

| 问题 | 原因 | 解决方案 |
|------|------|----------|
| CORS 错误 | 跨域配置 | 开发环境通过 Vite 代理解决，生产环境配置 Nginx |
| 404 Not Found | 路径错误 | 确认 API 路径以 `/api/` 开头 |
| SSE 一次性返回 | 代理缓冲 | Vite 已针对 SSE 配置无缓冲代理 |
| 401 认证失败 | API Key 错误 | 检查 .env 中 AI_DASHSCOPE_API_KEY |
| 文档上传失败 | 文件过大 | 默认限制 50MB，可修改 application.yml |
