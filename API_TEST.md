# 前后端接口测试指南

## 测试步骤

### 1. 启动后端服务
```bash
cd backend
mvn spring-boot:run
```
后端应该运行在 `http://localhost:8080`

### 2. 启动前端服务
```bash
cd frontend
npm run dev
```
前端应该运行在 `http://localhost:3000`

### 3. 验证代理配置

前端通过 Vite 代理访问后端 API：
- 前端请求: `http://localhost:3000/api/*`
- 代理转发: `http://localhost:8080/api/*`

### 4. 测试接口

#### 测试 1: 获取所有用户
```bash
curl http://localhost:8080/api/chat/users
```
预期返回: 用户列表数组

#### 测试 2: 创建对话
```bash
curl -X POST http://localhost:8080/api/chat/conversations \
  -H "Content-Type: application/json" \
  -d '{"userId": "user1"}'
```
预期返回: `{"conversationId": "xxx"}`

#### 测试 3: 发送消息（非流式）
```bash
curl -X POST http://localhost:8080/api/chat/send \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user1",
    "conversationId": "your-conversation-id",
    "message": "你好"
  }'
```
预期返回: AI 回复的完整内容

#### 测试 4: 发送消息（流式）
```bash
curl -N -X POST http://localhost:8080/api/chat/stream \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user1",
    "conversationId": "your-conversation-id",
    "message": "你好"
  }'
```
预期返回: SSE 格式的数据流，包含 `event: token` 等事件

#### 测试 5: 上传文档
```bash
curl -X POST http://localhost:8080/api/rag/upload \
  -F "file=@test.txt"
```

#### 测试 6: RAG 查询
```bash
curl -X POST http://localhost:8080/api/rag/query \
  -H "Content-Type: application/json" \
  -d '{
    "query": "测试问题",
    "topK": 5,
    "threshold": 0.7
  }'
```

### 5. 前端浏览器测试

1. 打开浏览器访问 `http://localhost:3000`
2. 打开开发者工具 (F12)
3. 切换到 Network 标签
4. 执行以下操作并观察网络请求：
   - 选择用户
   - 创建新对话
   - 发送消息
   - 查看 SSE 流式响应

### 6. 常见问题排查

#### 问题 1: CORS 错误
**现象**: 浏览器控制台显示 CORS 相关错误

**解决**: 
- 检查后端 Controller 是否有 `@CrossOrigin` 注解
- 确认 `allowedHeaders` 和 `origins` 配置正确

#### 问题 2: 404 Not Found
**现象**: API 返回 404

**检查**:
- 后端是否正确启动
- 请求路径是否正确（应该是 `/api/chat/*` 或 `/api/rag/*`）
- Vite 代理配置是否正确

#### 问题 3: SSE 流式响应不工作
**现象**: 消息一次性返回，不是流式

**检查**:
- 后端是否使用 `/api/chat/stream` 端点
- Content-Type 是否为 `text/event-stream`
- 前端是否正确解析 SSE 事件

#### 问题 4: API Key 错误
**现象**: 返回 401 InvalidApiKey

**解决**:
- 检查 `.env` 文件中 `AI_DASHSCOPE_API_KEY` 是否配置
- 确认 `spring-dotenv` 依赖已添加
- 重启后端服务

### 7. 接口清单

| 方法 | 路径 | 说明 | 参数 |
|------|------|------|------|
| GET | /api/chat/users | 获取所有用户 | 无 |
| POST | /api/chat/conversations | 创建对话 | {userId} |
| GET | /api/chat/conversations?userId=xxx | 获取对话列表 | userId |
| GET | /api/chat/conversations/{id}/messages | 获取消息列表 | conversationId |
| POST | /api/chat/send | 发送消息（非流式） | {userId, conversationId, message} |
| POST | /api/chat/stream | 发送消息（流式） | {userId, conversationId, message} |
| DELETE | /api/chat/conversations/{id} | 删除对话 | conversationId |
| POST | /api/rag/upload | 上传文档 | file (multipart) |
| POST | /api/rag/query | RAG 查询 | {query, topK, threshold} |
| GET | /api/rag/documents | 获取文档列表 | 无 |
| GET | /api/rag/documents/{id} | 获取文档详情 | documentId |
| GET | /api/rag/documents/{id}/chunks | 获取文档分块 | documentId |
| DELETE | /api/rag/documents/{id} | 删除文档 | documentId |
| GET | /api/rag/statistics | 获取统计信息 | 无 |

### 8. SSE 事件格式

流式响应使用 Server-Sent Events 格式：

```
event: meta
data: {"conversationId": "xxx"}

event: token
data: 你

event: token
data: 好

event: done
data: {"messageId": "xxx", "conversationId": "xxx"}
```

或在发生错误时：
```
event: error
data: 错误信息
```
