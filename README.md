# AI Kefu System

基于 Spring AI Alibaba + Vue + PGVector 的AI客服系统

## 功能特性

### RAG管理
- 上传文档 (支持 .txt, .md, .html, .htm)
- 文档自动分块
- 向量化存储 (使用 PGVector)
- 相似度查询
- 文档管理和删除

### 对话功能
- 多用户支持 (user1, user2...)
- 多会话支持 (session1, session2...)
- 记忆功能 - 基于用户和会话的记忆
- 记忆压缩 - 超过30条消息自动压缩
- DeepSeek API 集成

## 技术栈

- **后端**: Spring Boot 3.2, Spring AI Alibaba, MyBatis-Plus
- **前端**: Vue 3, Element Plus, Pinia, Axios
- **数据库**: PostgreSQL + PGVector
- **Embedding**: text-embedding-v3 (1536维)
- **LLM**: DeepSeek Chat

## 项目结构

```
ai-kefu-system/
├── backend/                 # Spring Boot 后端
│   ├── src/main/java/com/aikefu/
│   │   ├── controller/      # 控制器层
│   │   ├── service/         # 服务层
│   │   ├── mapper/          # 数据访问层
│   │   ├── entity/          # 实体类
│   │   ├── dto/             # 数据传输对象
│   │   └── util/            # 工具类
│   ├── src/main/resources/
│   │   ├── application.yml  # 配置文件
│   │   └── schema.sql       # 数据库schema
│   └── pom.xml
├── frontend/                # Vue 前端
│   ├── src/
│   │   ├── views/           # 页面组件
│   │   ├── api/             # API接口
│   │   ├── store/           # 状态管理
│   │   └── router/          # 路由配置
│   └── package.json
└── README.md
```

## 快速开始

### 1. 环境要求

- JDK 17+
- Node.js 18+
- PostgreSQL 15+ (已安装 pgvector 扩展)
- Maven 3.8+

### 2. 数据库设置

```bash
# 创建数据库
psql -U postgres -c "CREATE DATABASE aikefu;"

# 连接到数据库并执行schema
psql -U postgres -d aikefu -f backend/src/main/resources/schema.sql
```

### 3. 配置

编辑 `backend/.env` 文件，填入你的配置：

```env
DEEPSEEK_API_KEY=sk-your-actual-api-key
DEEPSEEK_BASE_URL=https://api.deepseek.com

DB_HOST=localhost
DB_PORT=5432
DB_NAME=aikefu
DB_USER=postgres
DB_PASSWORD=your-db-password

SERVER_PORT=8080
```

### 4. 启动后端

```bash
cd backend

# 安装依赖
mvn clean install

# 启动应用
mvn spring-boot:run
```

后端将在 http://localhost:8080 启动

### 5. 启动前端

```bash
cd frontend

# 安装依赖
npm install

# 启动开发服务器
npm run dev
```

前端将在 http://localhost:3000 启动

### 6. 访问系统

打开浏览器访问 http://localhost:3000

## 使用说明

### RAG管理

1. 在左侧栏切换到 "RAG管理" 标签
2. 点击 "上传文档" 按钮选择文件
3. 文档会自动分块、向量化并存储
4. 在 "查询测试" 区域输入查询内容进行相似度搜索
5. 可以查看、删除已上传的文档

### 对话功能

1. 在左侧栏切换到 "对话管理" 标签
2. 从用户下拉框选择用户 (user1, user2...)
3. 点击 "新建对话" 创建新会话
4. 在聊天区域与AI助手对话
5. 系统会自动维护对话历史和记忆
6. 当对话超过30条消息时，会自动进行记忆压缩

## API接口

### RAG 接口

- `POST /api/rag/upload` - 上传文档
- `POST /api/rag/query` - 查询相似内容
- `GET /api/rag/documents` - 获取所有文档
- `GET /api/rag/documents/{id}` - 获取单个文档
- `GET /api/rag/documents/{id}/chunks` - 获取文档chunks
- `DELETE /api/rag/documents/{id}` - 删除文档
- `GET /api/rag/statistics` - 获取统计信息

### 聊天接口

- `POST /api/chat/send` - 发送消息
- `GET /api/chat/users` - 获取所有用户
- `POST /api/chat/conversations` - 创建对话
- `GET /api/chat/conversations` - 获取用户的所有对话
- `GET /api/chat/conversations/{id}/messages` - 获取对话消息
- `DELETE /api/chat/conversations/{id}` - 删除对话

## 数据库表结构

- `t_user` - 用户表
- `t_rag_document` - RAG文档表
- `t_rag_chunk` - 文档分块表 (含向量)
- `t_conversation` - 对话表
- `t_conversation_message` - 对话消息表
- `t_memory_compression` - 记忆压缩记录表

## 注意事项

1. 首次使用请先配置 `.env` 文件中的 DeepSeek API Key
2. 确保 PostgreSQL 已启用 pgvector 扩展
3. 文档上传后会自动处理，请耐心等待
4. 记忆压缩会自动触发，但可以在 `ChatService` 中调整阈值

## 开发说明

### 后端技术细节

- 使用 MyBatis-Plus 进行ORM操作
- 向量存储使用 PGVector 的 `vector` 类型
- 相似度计算使用余弦距离 (`<=>` 操作符)
- Embedding 模型使用 text-embedding-v3 (1536维)

### 前端技术细节

- 使用 Pinia 进行状态管理
- 使用 Element Plus 作为UI组件库
- 通过 Axios 与后端API通信
- 支持实时聊天和RAG查询

## License

MIT License
