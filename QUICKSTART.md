# 快速入门

5 分钟启动 AI 客服系统。

## 前置条件

- JDK 17+、Maven 3.8+、Node.js 18+
- PostgreSQL 15+ 已安装 pgvector 扩展
- DashScope API Key（[获取地址](https://dashscope.console.aliyun.com/)）

## 第 1 步：配置环境变量

```bash
cd backend
cp .env.example .env
```

编辑 `backend/.env`，填入必填项：

```env
# 必填：DashScope API Key
AI_DASHSCOPE_API_KEY=sk-your-actual-key

# 必填：数据库密码
DB_PASSWORD=your-db-password

# 以下可保持默认
DB_HOST=localhost
DB_PORT=5432
DB_NAME=aikefu
DB_USER=postgres
SERVER_PORT=8080

# 可选：自定义模型
DASHSCOPE_CHAT_MODEL=deepseek-v4-flash
DASHSCOPE_EMBEDDING_MODEL=text-embedding-v3
```

## 第 2 步：初始化数据库

```bash
# 方式一：使用脚本
./init-db.sh

# 方式二：手动执行
psql -U postgres -c "CREATE DATABASE aikefu;"
psql -U postgres -d aikefu -c "CREATE EXTENSION IF NOT EXISTS vector;"
psql -U postgres -d aikefu -f backend/src/main/resources/schema.sql
```

## 第 3 步：启动后端

```bash
cd backend
mvn spring-boot:run
```

启动成功后可看到：`Started AiKefuApplication in X.XXX seconds`

验证：`curl http://localhost:8080/api/chat/users`

## 第 4 步：启动前端

```bash
cd frontend
npm install
npm run dev
```

## 第 5 步：访问系统

打开浏览器访问 http://localhost:3000

- `/chat` — 对话页面（默认）
- `/knowledge` — 知识库管理页面

## 使用说明

### 上传知识文档

1. 访问 `/knowledge` 页面
2. 点击"上传文档"，支持 .txt, .md, .html, .pdf, .doc, .docx
3. 等待文档处理完成（自动分块 + 向量化）
4. 在查询测试区输入问题，验证检索效果

### 开始对话

1. 访问 `/chat` 页面
2. 选择或输入用户 ID
3. 点击"新建对话"
4. 输入消息，AI 将基于知识库内容回答
5. 支持流式响应，文字实时逐字显示

## 常见问题

| 问题 | 解决方案 |
|------|----------|
| 数据库连接失败 | 检查 PostgreSQL 是否运行，确认 .env 中 DB_HOST/DB_PASSWORD |
| API 调用返回 401 | 检查 AI_DASHSCOPE_API_KEY 是否正确 |
| 向量搜索无结果 | 确认 pgvector 扩展已启用，检查 embedding 维度匹配 |
| 前端无法连接后端 | 确认后端在 8080 端口运行，检查 Vite 代理配置 |
| SSE 流式响应不工作 | 确认使用 `/api/chat/stream` 端点，Content-Type 为 text/event-stream |
| 端口被占用 | 修改 .env 中 SERVER_PORT 或停止占用进程 |

## 更多文档

- [完整说明](./README.md)
- [部署指南](./DEPLOYMENT.md)
- [接口测试](./API_TEST.md)
