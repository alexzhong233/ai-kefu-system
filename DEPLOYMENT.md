# AI客服系统部署指南

## 系统要求

- JDK 17+
- Node.js 18+
- PostgreSQL 15+ (需要pgvector扩展)
- Maven 3.8+

## 第一步：数据库设置

### 1.1 安装PostgreSQL和PGVector

如果你还没有安装PostgreSQL和pgvector，请先安装：

```bash
# Ubuntu/Debian
sudo apt update
sudo apt install postgresql postgresql-contrib
sudo apt install postgresql-15-pgvector

# macOS (使用Homebrew)
brew install postgresql@15
brew install pgvector

# 或者使用Docker
docker run -d \
  --name postgres-pgvector \
  -e POSTGRES_PASSWORD=your-password \
  -e POSTGRES_DB=aikefu \
  -p 5432:5432 \
  -v pgvector_data:/var/lib/postgresql/data \
  pgvector/pgvector:pg15
```

### 1.2 创建数据库

```bash
# 连接到PostgreSQL
psql -U postgres

# 创建数据库
CREATE DATABASE aikefu;

# 连接到aikefu数据库
\c aikefu

# 创建pgvector扩展
CREATE EXTENSION IF NOT EXISTS vector;

# 退出
\q
```

### 1.3 运行数据库Schema

```bash
# 进入项目目录
cd /path/to/ai-kefu-system

# 执行schema
psql -U postgres -d aikefu -f backend/src/main/resources/schema.sql
```

## 第二步：配置环境

### 2.1 复制环境配置文件

```bash
cd backend
cp .env.example .env
```

### 2.2 编辑.env文件

打开 `backend/.env` 文件，填入你的配置：

```env
# DeepSeek API配置
DEEPSEEK_API_KEY=sk-your-actual-api-key-here
DEEPSEEK_BASE_URL=https://api.deepseek.com

# 数据库配置
DB_HOST=localhost
DB_PORT=5432
DB_NAME=aikefu
DB_USER=postgres
DB_PASSWORD=your-actual-db-password

# 服务端口
SERVER_PORT=8080
```

**重要**：你需要在DeepSeek官网获取API Key：https://platform.deepseek.com/

## 第三步：启动后端

### 3.1 编译项目

```bash
cd backend
mvn clean package -DskipTests
```

### 3.2 启动服务

```bash
mvn spring-boot:run
```

后端将在 http://localhost:8080 启动。

## 第四步：启动前端

### 4.1 安装依赖

```bash
cd frontend
npm install
```

### 4.2 启动开发服务器

```bash
npm run dev
```

前端将在 http://localhost:3000 启动。

## 第五步：访问系统

打开浏览器，访问 http://localhost:3000

你应该能看到AI客服系统的管理界面。

## 使用说明

### 上传RAG文档

1. 在左侧栏选择"RAG管理"标签
2. 点击"上传文档"按钮
3. 选择要上传的文件（支持.txt, .md, .html格式）
4. 系统会自动进行分块和向量化处理
5. 处理完成后，文档会出现在列表中

### 测试RAG查询

1. 在"查询测试"区域输入查询内容
2. 设置Top K和相似度阈值
3. 点击"查询"按钮
4. 查看返回的相似文档片段和相似度分数

### 开始对话

1. 在左侧栏选择"对话管理"标签
2. 从用户下拉框选择用户（user1, user2等）
3. 点击"新建对话"创建新会话
4. 在聊天区域输入消息与AI对话
5. 系统会自动维护对话历史和记忆
6. 当对话超过30条消息时，系统会自动进行记忆压缩

## 故障排除

### 1. 数据库连接失败

检查 `.env` 文件中的数据库配置是否正确：
- DB_HOST 是否正确
- DB_PORT 是否正确
- DB_USER 和 DB_PASSWORD 是否匹配
- 数据库是否已启动

### 2. API调用失败

检查 `.env` 文件中的DeepSeek API配置：
- DEEPSEEK_API_KEY 是否正确
- 网络是否能访问 DeepSeek API

### 3. 前端无法连接后端

检查：
- 后端是否已启动
- Vite代理配置是否正确
- 端口是否被占用

### 4. 向量搜索不工作

检查：
- pgvector扩展是否已启用
- 向量维度是否匹配（text-embedding-v3使用1536维）
- 索引是否正确创建

## 性能优化建议

### 1. 调整Chunk大小

在 `RagService.java` 中可以调整 `CHUNK_SIZE` 参数：

```java
private static final int CHUNK_SIZE = 500; // 调整为更小或更大的值
```

### 2. 调整记忆压缩阈值

在 `ChatService.java` 中调整压缩阈值：

```java
private static final int COMPRESSION_THRESHOLD = 30; // 超过30条消息时压缩
```

### 3. 调整查询参数

在前端页面可以调整：
- Top K: 返回的最相似结果数量
- 阈值: 相似度阈值，低于此值的结果将被过滤

## 生产环境部署

### 1. 构建前端生产版本

```bash
cd frontend
npm run build
```

这将在 `frontend/dist` 目录生成静态文件。

### 2. 配置反向代理

可以使用Nginx作为反向代理：

```nginx
server {
    listen 80;
    server_name your-domain.com;

    location / {
        root /path/to/frontend/dist;
        try_files $uri $uri/ /index.html;
    }

    location /api {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

### 3. 配置HTTPS

建议使用HTTPS，可以配合Let's Encrypt免费证书使用。

### 4. 性能考虑

- 使用连接池管理数据库连接
- 考虑使用Redis缓存热点数据
- 配置合理的超时时间
- 开启日志和监控

## 安全建议

1. **API Key保护**：不要将 `.env` 文件提交到版本控制系统
2. **数据库密码**：使用强密码并定期更换
3. **输入验证**：所有用户输入都应该进行验证
4. **限流**：考虑添加API限流防止滥用
5. **日志审计**：记录关键操作便于审计

## 监控和日志

### 后端日志

Spring Boot日志默认输出到控制台，也可以配置输出到文件：

```yaml
# application.yml
logging:
  file:
    name: logs/app.log
  level:
    com.aikefu: INFO
```

### 前端监控

可以在前端添加错误监控服务，如Sentry。

## 更新和升级

### 后端更新

```bash
cd backend
git pull
mvn clean package -DskipTests
# 重启后端服务
```

### 前端更新

```bash
cd frontend
git pull
npm install
npm run build
```

## 获取帮助

如果遇到问题：

1. 查看控制台日志和错误信息
2. 检查 `.env` 配置文件
3. 验证数据库连接
4. 检查网络连通性

如需更多帮助，请查看项目README或提交Issue。
