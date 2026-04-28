# 🚀 AI客服系统 - 快速入门

## 📋 项目概览

这是一个基于 **Spring AI Alibaba + Vue 3 + PGVector** 的AI客服系统，具有以下核心功能：

### ✅ 已实现功能

#### RAG管理
- ✅ 文档上传（支持.txt, .md, .html格式）
- ✅ 自动分块（每块约500字符）
- ✅ 向量化存储（使用text-embedding-v3，1536维）
- ✅ 相似度查询
- ✅ 文档管理和删除
- ✅ 查看统计信息

#### 对话功能
- ✅ 多用户支持（user1, user2...）
- ✅ 多会话支持
- ✅ 基于用户+会话的记忆管理
- ✅ 记忆压缩（超过30条消息自动压缩）
- ✅ RAG增强回答
- ✅ DeepSeek API集成

## 🎯 5分钟快速启动

### 步骤1：配置API Key

编辑 `backend/.env` 文件：

```env
DEEPSEEK_API_KEY=sk-your-actual-api-key
DEEPSEEK_BASE_URL=https://api.deepseek.com
DB_PASSWORD=your-db-password
```

### 步骤2：初始化数据库（可选）

```bash
# macOS/Linux
./init-db.sh

# Windows
init-db.bat
```

或者手动执行：

```bash
psql -U postgres -d postgres -c "CREATE DATABASE aikefu;"
psql -U postgres -d aikefu -c "CREATE EXTENSION IF NOT EXISTS vector;"
psql -U postgres -d aikefu -f backend/src/main/resources/schema.sql
```

### 步骤3：启动后端

```bash
cd backend
mvn spring-boot:run
```

### 步骤4：启动前端

```bash
cd frontend
npm install
npm run dev
```

### 步骤5：访问系统

打开浏览器访问：http://localhost:3000

## 📖 使用指南

### 上传RAG文档

1. 在左侧栏选择 **"RAG管理"** 标签
2. 点击 **"上传文档"** 按钮
3. 选择文件（.txt, .md, .html）
4. 等待处理完成
5. 在 **"查询测试"** 区域测试查询

### 开始对话

1. 在左侧栏选择 **"对话管理"** 标签
2. 从下拉框选择用户（user1, user2...）
3. 点击 **"新建对话"**
4. 在聊天区域输入消息
5. AI助手会基于RAG知识库回答

### 切换用户/会话

- **切换用户**：从用户下拉框选择不同用户
- **新建会话**：点击"新建对话"按钮
- **切换会话**：点击会话卡片切换
- **删除会话**：点击删除图标

## 🔧 项目结构

```
ai-kefu-system/
├── backend/                          # Spring Boot后端
│   ├── src/main/java/com/aikefu/
│   │   ├── controller/              # API控制器
│   │   │   ├── ChatController.java  # 对话API
│   │   │   └── RagController.java   # RAG管理API
│   │   ├── service/                 # 业务逻辑
│   │   │   ├── ChatService.java     # 对话服务
│   │   │   └── RagService.java      # RAG服务
│   │   ├── mapper/                   # 数据访问层
│   │   ├── entity/                   # 实体类
│   │   └── dto/                      # 数据传输对象
│   └── src/main/resources/
│       ├── application.yml          # 配置文件
│       └── schema.sql               # 数据库schema
├── frontend/                         # Vue前端
│   └── src/
│       ├── views/
│       │   └── AdminView.vue        # 管理页面
│       ├── store/
│       │   └── index.js             # 状态管理
│       └── api/
│           └── index.js             # API封装
├── README.md                         # 项目说明
├── DEPLOYMENT.md                     # 部署指南
└── QUICKSTART.md                     # 快速入门
```

## 📊 数据库表结构

| 表名 | 用途 |
|------|------|
| `t_user` | 用户表 |
| `t_rag_document` | RAG文档表 |
| `t_rag_chunk` | 文档分块表（含向量） |
| `t_conversation` | 对话表 |
| `t_conversation_message` | 消息表 |
| `t_memory_compression` | 记忆压缩记录 |

## 🔌 API接口

### RAG接口
- `POST /api/rag/upload` - 上传文档
- `POST /api/rag/query` - 查询相似内容
- `GET /api/rag/documents` - 获取文档列表
- `GET /api/rag/statistics` - 获取统计

### 聊天接口
- `POST /api/chat/send` - 发送消息
- `GET /api/chat/users` - 获取用户列表
- `POST /api/chat/conversations` - 创建对话
- `GET /api/chat/conversations` - 获取对话列表

## ⚙️ 配置说明

### 环境变量 (.env)

```env
# DeepSeek API
DEEPSEEK_API_KEY=sk-xxx          # 必填：API Key
DEEPSEEK_BASE_URL=https://api.deepseek.com

# 数据库
DB_HOST=localhost
DB_PORT=5432
DB_NAME=aikefu
DB_USER=postgres
DB_PASSWORD=xxx                  # 必填：数据库密码

# 服务
SERVER_PORT=8080
```

### 可调参数

在代码中可以调整的参数：

```java
// RagService.java
private static final int CHUNK_SIZE = 500;           // 文本分块大小
private static final float SIMILARITY_THRESHOLD = 0.7f;  // 相似度阈值

// ChatService.java
private static final int MAX_HISTORY_MESSAGES = 20;      // 最大历史消息数
private static final int COMPRESSION_THRESHOLD = 30;     // 记忆压缩阈值
```

## 🐛 常见问题

### Q1: 数据库连接失败
**A**: 检查 `.env` 文件中的数据库配置，确保PostgreSQL正在运行。

### Q2: API调用失败
**A**: 检查 `DEEPSEEK_API_KEY` 是否正确，网络是否能访问DeepSeek API。

### Q3: 向量搜索不工作
**A**: 确保pgvector扩展已启用，向量维度匹配（1536维）。

### Q4: 前端无法连接后端
**A**: 检查后端是否启动，端口8080是否被占用。

## 📚 更多资源

- [详细部署指南](./DEPLOYMENT.md)
- [完整项目说明](./README.md)
- [DeepSeek API文档](https://platform.deepseek.com/docs)
- [PGVector文档](https://github.com/pgvector/pgvector)

## 🎉 祝你使用愉快！

如果遇到问题，请检查：
1. `.env` 配置是否正确
2. 数据库是否正常启动
3. API Key是否有效
4. 网络连接是否正常

---

**版本**: 1.0.0  
**创建日期**: 2024-04-28  
**技术栈**: Spring Boot 3.2 + Vue 3 + PGVector + MyBatis-Plus + Spring AI Alibaba
