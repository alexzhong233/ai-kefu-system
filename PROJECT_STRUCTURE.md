# 项目文件清单

## 📦 AI客服系统完整项目结构

```
ai-kefu-system/
│
├── 📄 文档文件
│   ├── README.md                 # 项目完整说明
│   ├── DEPLOYMENT.md             # 详细部署指南
│   └── QUICKSTART.md            # 快速入门指南
│
├── 📄 配置文件
│   ├── .gitignore               # Git忽略文件
│   ├── start.sh                  # Linux/Mac启动脚本
│   ├── start.bat                 # Windows启动脚本
│   ├── init-db.sh                # Linux/Mac数据库初始化
│   └── init-db.bat               # Windows数据库初始化
│
├── 📂 backend/                   # Spring Boot后端
│   ├── 📄 pom.xml                # Maven配置文件
│   ├── 📄 .env                   # 环境变量配置
│   ├── 📄 .env.example           # 环境变量示例
│   │
│   └── 📂 src/main/
│       ├── 📄 application.yml    # Spring Boot配置
│       ├── 📄 schema.sql         # 数据库Schema
│       │
│       └── 📂 java/com/aikefu/
│           │
│           ├── 📄 AiKefuApplication.java  # 主应用类
│           │
│           ├── 📂 controller/     # 控制器层
│           │   ├── ChatController.java   # 对话API
│           │   └── RagController.java    # RAG管理API
│           │
│           ├── 📂 service/        # 业务逻辑层
│           │   ├── ChatService.java      # 对话服务
│           │   │   └── 多用户/会话管理
│           │   │   └── 记忆压缩
│           │   │   └── RAG增强回答
│           │   └── RagService.java       # RAG服务
│           │       └── 文档上传
│           │       └── 文本分块
│           │       └── 向量化
│           │       └── 相似度查询
│           │
│           ├── 📂 mapper/          # 数据访问层
│           │   ├── UserMapper.java
│           │   ├── RagDocumentMapper.java
│           │   ├── RagChunkMapper.java
│           │   ├── ConversationMapper.java
│           │   ├── ConversationMessageMapper.java
│           │   └── MemoryCompressionMapper.java
│           │
│           ├── 📂 entity/          # 实体类
│           │   ├── User.java
│           │   ├── RagDocument.java
│           │   ├── RagChunk.java
│           │   ├── Conversation.java
│           │   ├── ConversationMessage.java
│           │   └── MemoryCompression.java
│           │
│           ├── 📂 dto/             # 数据传输对象
│           │   ├── ChatRequest.java
│           │   ├── ChatResponse.java
│           │   ├── QueryRequest.java
│           │   └── DocumentUploadResponse.java
│           │
│           └── 📂 util/            # 工具类
│               └── VectorTypeHandler.java  # PGVector类型处理器
│
└── 📂 frontend/                   # Vue 3前端
    ├── 📄 package.json            # npm配置
    ├── 📄 vite.config.js          # Vite配置
    ├── 📄 index.html              # HTML入口
    │
    └── 📂 src/
        ├── 📄 main.js             # Vue入口
        ├── 📄 App.vue             # 根组件
        │
        ├── 📂 router/             # 路由配置
        │   └── index.js
        │
        ├── 📂 store/              # 状态管理
        │   └── index.js
        │
        ├── 📂 api/                # API接口封装
        │   └── index.js
        │
        └── 📂 views/              # 页面组件
            └── AdminView.vue      # 管理页面（主页面）
                ├── RAG管理标签页
                │   ├── 文档上传
                │   ├── 文档列表
                │   ├── 统计信息
                │   ├── 查询测试
                │   └── 查看Chunks
                │
                └── 对话管理标签页
                    ├── 用户选择
                    ├── 会话列表
                    ├── 新建对话
                    └── 聊天界面
```

## 🎯 核心功能文件映射

### 后端核心功能

| 功能 | 主要文件 | 说明 |
|------|---------|------|
| **应用入口** | `AiKefuApplication.java` | Spring Boot启动类 |
| **RAG管理** | `RagController.java` + `RagService.java` | 文档上传、查询 |
| **对话管理** | `ChatController.java` + `ChatService.java` | 消息发送、记忆 |
| **数据库Schema** | `schema.sql` | 6个表，含向量字段 |
| **配置** | `application.yml` + `.env` | 应用配置和密钥 |

### 前端核心功能

| 功能 | 文件 | 说明 |
|------|------|------|
| **主页面** | `AdminView.vue` | 完整的管理界面 |
| **API调用** | `api/index.js` | axios封装 |
| **状态管理** | `store/index.js` | Pinia store |
| **路由** | `router/index.js` | Vue Router |

## 📊 数据库表

```sql
-- 用户表
CREATE TABLE t_user (
    user_id VARCHAR(100) PRIMARY KEY,
    user_name VARCHAR(200),
    description TEXT
);

-- RAG文档表
CREATE TABLE t_rag_document (
    document_id VARCHAR(100) PRIMARY KEY,
    file_name VARCHAR(500),
    file_type VARCHAR(50),
    content_text TEXT,
    status VARCHAR(20),
    chunk_count INTEGER
);

-- RAG分块表（含向量）
CREATE TABLE t_rag_chunk (
    chunk_id VARCHAR(100) PRIMARY KEY,
    document_id VARCHAR(100),
    content TEXT,
    chunk_index INTEGER,
    embedding VECTOR(1536)  -- 向量维度
);

-- 对话表
CREATE TABLE t_conversation (
    conversation_id VARCHAR(100) PRIMARY KEY,
    user_id VARCHAR(100),
    title VARCHAR(500),
    status VARCHAR(20),
    message_count INTEGER
);

-- 消息表
CREATE TABLE t_conversation_message (
    message_id VARCHAR(100) PRIMARY KEY,
    conversation_id VARCHAR(100),
    role VARCHAR(20),
    content TEXT,
    metadata JSONB
);

-- 记忆压缩表
CREATE TABLE t_memory_compression (
    compression_id VARCHAR(100) PRIMARY KEY,
    conversation_id VARCHAR(100),
    original_message_count INTEGER,
    compressed_message_count INTEGER,
    summary TEXT
);
```

## 🔧 启动脚本说明

| 脚本 | 平台 | 用途 |
|------|------|------|
| `start.sh` | Linux/Mac | 一键启动前后端 |
| `start.bat` | Windows | 一键启动前后端 |
| `init-db.sh` | Linux/Mac | 初始化数据库 |
| `init-db.bat` | Windows | 初始化数据库 |

## 📝 配置文件说明

| 文件 | 位置 | 必填 | 说明 |
|------|------|------|------|
| `.env` | backend/ | ✅ | API密钥、数据库密码等 |
| `.env.example` | backend/ | - | 环境变量示例 |
| `application.yml` | backend/src/main/resources/ | 内置 | Spring Boot配置 |
| `schema.sql` | backend/src/main/resources/ | 执行一次 | 数据库表结构 |

## 🚀 快速启动流程

1. **配置密钥**
   ```bash
   # 编辑 backend/.env
   DEEPSEEK_API_KEY=sk-your-key
   DB_PASSWORD=your-db-password
   ```

2. **初始化数据库**（如果PostgreSQL已就绪）
   ```bash
   ./init-db.sh
   # 或
   psql -U postgres -d postgres -c "CREATE DATABASE aikefu;"
   psql -U postgres -d aikefu -f backend/src/main/resources/schema.sql
   ```

3. **启动后端**
   ```bash
   cd backend
   mvn spring-boot:run
   ```

4. **启动前端**
   ```bash
   cd frontend
   npm install
   npm run dev
   ```

5. **访问系统**
   打开 http://localhost:3000

## 📦 技术栈版本

| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.2.0 | 后端框架 |
| Spring AI Alibaba | 1.0.0.0 | AI集成 |
| MyBatis-Plus | 3.5.5 | ORM框架 |
| PostgreSQL | 15+ | 关系数据库 |
| PGVector | latest | 向量数据库 |
| Vue 3 | 3.4.0 | 前端框架 |
| Element Plus | 2.4.4 | UI组件库 |
| Pinia | 2.1.7 | 状态管理 |
| Vite | 5.0.0 | 构建工具 |

## 🎓 学习资源

- Spring Boot 3.2: https://spring.io/projects/spring-boot
- Spring AI: https://spring.io/projects/spring-ai
- Vue 3: https://vuejs.org/
- PGVector: https://github.com/pgvector/pgvector
- DeepSeek API: https://platform.deepseek.com/

---

**创建时间**: 2024-04-28  
**项目版本**: 1.0.0  
**总文件数**: 30+  
**代码行数**: ~3000+ 行
