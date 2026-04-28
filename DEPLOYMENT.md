# 部署指南

## 系统要求

| 组件 | 最低版本 | 说明 |
|------|----------|------|
| JDK | 17+ | 后端运行环境 |
| Maven | 3.8+ | 后端构建工具 |
| Node.js | 18+ | 前端构建工具 |
| PostgreSQL | 15+ | 需安装 pgvector 扩展 |

## 数据库设置

### 安装 PostgreSQL + pgvector

```bash
# macOS
brew install postgresql@15 pgvector

# Ubuntu/Debian
sudo apt install postgresql-15 postgresql-15-pgvector

# Docker（推荐）
docker run -d \
  --name postgres-pgvector \
  -e POSTGRES_PASSWORD=your-password \
  -e POSTGRES_DB=aikefu \
  -p 5432:5432 \
  -v pgvector_data:/var/lib/postgresql/data \
  pgvector/pgvector:pg15
```

### 初始化数据库

```bash
# 使用脚本（推荐）
./init-db.sh

# 或手动执行
psql -U postgres -c "CREATE DATABASE aikefu;"
psql -U postgres -d aikefu -c "CREATE EXTENSION IF NOT EXISTS vector;"
psql -U postgres -d aikefu -f backend/src/main/resources/schema.sql
```

## 环境配置

```bash
cd backend
cp .env.example .env
```

编辑 `.env` 文件：

```env
# DashScope API 配置（必填）
AI_DASHSCOPE_API_KEY=sk-your-actual-key

# 数据库配置
DB_HOST=localhost
DB_PORT=5432
DB_NAME=aikefu
DB_USER=postgres
DB_PASSWORD=your-password

# 服务端口
SERVER_PORT=8080

# 模型配置（可选，有默认值）
DASHSCOPE_CHAT_MODEL=deepseek-v4-flash
DASHSCOPE_EMBEDDING_MODEL=text-embedding-v3
```

> **重要**：`AI_DASHSCOPE_API_KEY` 是唯一必填的 API 密钥，从 [DashScope 控制台](https://dashscope.console.aliyun.com/) 获取。

## 启动服务

### 开发模式

```bash
# 后端
cd backend
mvn spring-boot:run

# 前端
cd frontend
npm install
npm run dev
```

### 一键启动

```bash
# macOS/Linux
./start.sh

# Windows
start.bat
```

### 生产构建

```bash
# 构建后端 JAR
cd backend
mvn clean package -DskipTests
java -jar target/ai-kefu-backend-1.0.0.jar

# 构建前端静态文件
cd frontend
npm run build
# 产出目录：frontend/dist/
```

## 生产部署

### Nginx 反向代理

```nginx
server {
    listen 80;
    server_name your-domain.com;

    # 前端静态文件
    location / {
        root /path/to/frontend/dist;
        try_files $uri $uri/ /index.html;
    }

    # SSE 流式接口 — 禁用缓冲
    location /api/chat/stream {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_http_version 1.1;
        proxy_set_header Connection '';
        proxy_buffering off;
        proxy_cache off;
        chunked_transfer_encoding off;
    }

    # 后端 API
    location /api {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

### HTTPS 配置

建议配合 Let's Encrypt 免费证书使用：

```bash
sudo apt install certbot python3-certbot-nginx
sudo certbot --nginx -d your-domain.com
```

### Systemd 服务（Linux）

创建 `/etc/systemd/system/ai-kefu.service`：

```ini
[Unit]
Description=AI Kefu Backend
After=network.target postgresql.service

[Service]
Type=simple
User=app
WorkingDirectory=/opt/ai-kefu-system/backend
ExecStart=/usr/bin/java -jar /opt/ai-kefu-system/backend/target/ai-kefu-backend-1.0.0.jar
Restart=on-failure
EnvironmentFile=/opt/ai-kefu-system/backend/.env

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl enable ai-kefu
sudo systemctl start ai-kefu
```

## 性能优化

### 应用层

| 优化项 | 当前配置 | 建议调整 |
|--------|----------|----------|
| 数据库连接池 | HikariCP max=10 | 生产环境可增至 20-50 |
| SSE 超时 | 5 分钟 | 根据对话长度调整 |
| 日志级别 | DEBUG | 生产环境改为 INFO 或 WARN |
| 文件上传限制 | 50MB | 按需调整 `spring.servlet.multipart` |

### 数据库层

```sql
-- 向量索引已在 schema.sql 中创建
-- 大数据量时可调整 IVFFlat 参数
CREATE INDEX idx_rag_chunk_embedding ON t_rag_chunk
  USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
```

### 缓存建议

- 添加 Redis 缓存热点文档和向量查询结果
- 使用消息队列异步处理大文档上传

## 安全建议

1. **API Key 保护**：不要将 `.env` 文件提交到版本控制
2. **数据库安全**：使用强密码，限制远程访问
3. **输入验证**：所有用户输入需验证和过滤
4. **API 限流**：添加限流防止滥用
5. **HTTPS**：生产环境必须使用 HTTPS
6. **CORS**：生产环境配置具体允许的域名

## 监控与日志

### 后端日志

```yaml
# application.yml
logging:
  file:
    name: logs/app.log
  level:
    com.aikefu: INFO
    org.springframework.ai: WARN
```

### 前端监控

可集成 Sentry 等错误监控服务。

## 故障排除

| 问题 | 原因 | 解决方案 |
|------|------|----------|
| Maven 编译失败 | 依赖下载超时 | 配置阿里云镜像或检查网络 |
| 数据库连接拒绝 | PostgreSQL 未运行或密码错误 | 检查服务状态和 .env 配置 |
| 端口 8080 被占用 | 其他进程占用 | 修改 SERVER_PORT 或 `lsof -i :8080` 查找 |
| API 返回 401 | API Key 无效 | 检查 AI_DASHSCOPE_API_KEY |
| SSE 响应被缓冲 | Nginx/Vite 代理缓冲 | 配置 proxy_buffering off |
| 内存不足 | Tika 解析大文件 | 设置 `MAVEN_OPTS="-Xmx2g"` |

## 更多文档

- [项目说明](./README.md)
- [快速入门](./QUICKSTART.md)
- [接口测试](./API_TEST.md)
