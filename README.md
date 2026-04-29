# AI 客服系统

基于 Spring Boot + Spring AI Alibaba + Vue 3 + PGVector 的智能客服系统，支持 RAG 知识库检索增强和流式对话。

## 功能特性

### RAG 知识库管理

- 文档上传（支持 .txt, .md, .html, .pdf, .doc, .docx 等格式）
- 智能分块（按段落/标题/句子分割，支持中英文断句，chunk 间保留重叠）
- 向量化存储（DashScope text-embedding-v3，1024 维）
- 相似度检索（余弦距离 + IVFFlat 索引加速）
- 文档管理和统计

### 智能对话

- 多用户 / 多会话隔离
- SSE 流式响应（token 级实时推送）
- RAG 增强回答（自动检索知识库注入上下文）
- 对话记忆总结（首轮及每 10 轮自动生成摘要和标题）
- 上下文窗口管理（摘要替代旧消息，保留最近 20 条）

## 技术栈


| 层级     | 技术                                      | 版本            |
| -------- | ----------------------------------------- | --------------- |
| 后端框架 | Spring Boot                               | 3.4.5           |
| AI 集成  | Spring AI + Spring AI Alibaba (DashScope) | 1.0.0 / 1.0.0.2 |
| ORM      | MyBatis-Plus                              | 3.5.5           |
| 数据库   | PostgreSQL + PGVector                     | 15+             |
| 文件解析 | Apache Tika                               | 2.9.2           |
| 前端框架 | Vue 3                                     | 3.4.0           |
| UI 组件  | Element Plus                              | 2.4.4           |
| 状态管理 | Pinia                                     | 2.1.7           |
| 构建工具 | Vite                                      | 5.0.0           |

## 项目结构

```
ai-kefu-system/
├── backend/                          # Spring Boot 后端
│   ├── src/main/java/com/aikefu/
│   │   ├── AiKefuApplication.java    # 启动类
│   │   ├── config/
│   │   │   ├── SpringAiConfig.java   # Spring AI 配置（DashScope 自动配置）
│   │   │   ├── WebMvcConfig.java      # MVC 配置（SSE 超时）
│   │   │   └── MyBatisMetaObjectHandler.java  # 自动填充 createdAt/updatedAt
│   │   ├── controller/
│   │   │   ├── ChatController.java   # 对话 API（含 SSE 流式端点）
│   │   │   └── RagController.java    # RAG 管理 API
│   │   ├── service/
│   │   │   ├── ChatService.java      # 对话服务（记忆总结 + RAG 增强）
│   │   │   └── RagService.java       # RAG 服务（文档解析 + 分块 + 向量化）
│   │   ├── mapper/                   # 6 个 MyBatis-Plus Mapper
│   │   ├── entity/                   # 6 个实体类
│   │   ├── dto/                      # 4 个 DTO
│   │   └── util/
│   │       ├── VectorTypeHandler.java # PGVector 类型处理器
│   │       └── MapTypeHandler.java    # JSONB Map 类型处理器
│   ├── src/main/resources/
│   │   ├── application.yml           # 应用配置
│   │   └── schema.sql                # 数据库 Schema
│   ├── .env.example                  # 环境变量示例
│   └── pom.xml
├── frontend/                         # Vue 3 前端
│   ├── src/
│   │   ├── views/
│   │   │   ├── ChatView.vue          # 对话页面
│   │   │   └── KnowledgeView.vue     # 知识库管理页面
│   │   ├── api/index.js              # API 封装（含 SSE fetch）
│   │   ├── store/index.js            # Pinia Store
│   │   └── router/index.js           # 路由（/chat, /knowledge）
│   ├── vite.config.js                # Vite 配置（SSE 代理优化）
│   └── package.json
├── uploads/                          # 上传文件存储目录
├── start.sh / start.bat              # 一键启动脚本
├── init-db.sh / init-db.bat          # 数据库数据库初始化脚本
├── README.md                         # 本文档
├── QUICKSTART.md                     # 快速入门
├── DEPLOYMENT.md                     # 部署指南
└── API_TEST.md                       # 接口测试指南
```

## 数据库表结构


| 表名                     | 说明           | 关键字段                                          |
| ------------------------ | -------------- | ------------------------------------------------- |
| `t_user`                 | 用户表         | user_id, user_name                                |
| `t_rag_document`         | RAG 文档表     | document_id, file_name, file_path, status         |
| `t_rag_chunk`            | 文档分块表     | chunk_id, document_id, content, embedding(1024维) |
| `t_conversation`         | 对话表         | conversation_id, user_id, title, summary          |
| `t_conversation_message` | 消息表         | message_id, conversation_id, role, content        |
| `t_memory_compression`   | 记忆压缩历史表 | compression_id, conversation_id, summary          |

## API 接口

### RAG 管理


| 方法   | 路径                             | 说明                  |
| ------ | -------------------------------- | --------------------- |
| POST   | `/api/rag/upload`                | 上传文档（multipart） |
| POST   | `/api/rag/query`                 | 相似度查询            |
| GET    | `/api/rag/documents`             | 文档列表              |
| GET    | `/api/rag/documents/{id}`        | 文档详情              |
| GET    | `/api/rag/documents/{id}/chunks` | 文档分块              |
| DELETE | `/api/rag/documents/{id}`        | 删除文档（逻辑删除）  |
| GET    | `/api/rag/statistics`            | 统计信息              |

### 对话管理


| 方法   | 路径                                    | 说明                 |
| ------ | --------------------------------------- | -------------------- |
| POST   | `/api/chat/send`                        | 发送消息（非流式）   |
| POST   | `/api/chat/stream`                      | 发送消息（SSE 流式） |
| GET    | `/api/chat/users`                       | 用户列表             |
| POST   | `/api/chat/conversations`               | 创建对话             |
| GET    | `/api/chat/conversations?userId=xxx`    | 对话列表             |
| GET    | `/api/chat/conversations/{id}/messages` | 消息列表             |
| DELETE | `/api/chat/conversations/{id}`          | 删除对话（逻辑删除） |

## 环境要求

- JDK 17+
- Maven 3.8+
- Node.js 18+
- PostgreSQL 15+（需安装 pgvector 扩展）
- DashScope API Key（[获取地址](https://dashscope.console.aliyun.com/)）

## 快速开始

详见 [QUICKSTART.md](./QUICKSTART.md)，或按以下步骤：

```bash
# 1. 配置环境变量
cd backend && cp .env.example .env
# 编辑 .env 填入 AI_DASHSCOPE_API_KEY 和 DB_PASSWORD

# 2. 初始化数据库
./init-db.sh

# 3. 启动后端
cd backend && mvn spring-boot:run

# 4. 启动前端
cd frontend && npm install && npm run dev

# 5. 访问 http://localhost:3000
```

## 可调参数

```java
// RagService.java
CHUNK_SIZE = 800          // 文本分块大小（字符数）
CHUNK_OVERLAP = 200       // 相邻 chunk 重叠字符数
MIN_CHUNK_SIZE = 100      // 最小 chunk 大小
SIMILARITY_THRESHOLD = 0.5 // 相似度阈值

// ChatService.java
MAX_HISTORY_MESSAGES = 20  // 最大历史消息数
SUMMARY_ROUND_INTERVAL = 10 // 每 N 轮自动总结
```

## 相关文档

- [快速入门](./QUICKSTART.md) — 5 分钟启动系统
- [部署指南](./DEPLOYMENT.md) — 生产环境部署与配置
- [接口测试](./API_TEST.md) — API 接口测试与调试

## License

MIT License
