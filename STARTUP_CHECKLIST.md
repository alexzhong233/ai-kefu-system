# 🚀 快速启动清单

## 前置检查 ✅

- [ ] JDK 17+ 已安装
- [ ] Maven 3.8+ 已安装
- [ ] PostgreSQL 15+ 已安装并运行
- [ ] pgvector扩展已启用

## 第一步：配置 ⚙️

```bash
cd backend
cp .env.example .env
```

编辑 `.env`:
```env
DEEPSEEK_API_KEY=sk-your-key
DB_PASSWORD=your-db-password
```

## 第二步：数据库 🗄️

```bash
psql -U postgres -d postgres -c "CREATE DATABASE aikefu;"
psql -U postgres -d aikefu -c "CREATE EXTENSION IF NOT EXISTS vector;"
psql -U postgres -d aikefu -f backend/src/main/resources/schema.sql
```

## 第三步：后端启动 🚀

```bash
cd backend
mvn clean compile -DskipTests
mvn spring-boot:run
```

✅ 后端运行在: http://localhost:8080

## 第四步：前端启动 🌐

```bash
cd frontend
npm install
npm run dev
```

✅ 前端运行在: http://localhost:3000

## 第五步：测试 ✅

1. 打开浏览器访问: http://localhost:3000
2. 上传一个文档测试RAG
3. 创建对话测试聊天
4. 切换用户/会话测试记忆

## 常用命令 📝

### 数据库
```bash
# 查看数据库
psql -U postgres -d aikefu -c "\dt"

# 查看表
psql -U postgres -d aikefu -c "\d t_user"

# 查看向量扩展
psql -U postgres -d aikefu -c "SELECT * FROM pg_extension WHERE extname='vector';"
```

### Maven
```bash
# 编译
mvn clean compile -DskipTests

# 运行
mvn spring-boot:run

# 打包
mvn clean package -DskipTests

# 只编译
mvn compiler:compile
```

### 日志
```bash
# 后端日志在控制台输出
# 使用 tail -f 跟踪日志
tail -f backend.log
```

## 故障排除 🔧

| 问题 | 解决方案 |
|------|----------|
| 端口被占用 | 修改 .env 中 SERVER_PORT=8081 |
| 数据库连接失败 | 检查DB_HOST, DB_PASSWORD |
| API调用失败 | 检查DEEPSEEK_API_KEY |
| Maven下载慢 | 添加阿里云镜像 |
| 前端无法连接 | 检查后端是否在8080端口运行 |

## API测试 🧪

```bash
# 测试后端是否启动
curl http://localhost:8080

# 测试用户列表
curl http://localhost:8080/api/chat/users

# 测试RAG统计
curl http://localhost:8080/api/rag/statistics

# 测试发送消息
curl -X POST http://localhost:8080/api/chat/send \
  -H "Content-Type: application/json" \
  -d '{"userId":"user1","message":"你好"}'
```

## 文档资源 📚

| 文档 | 用途 |
|------|------|
| QUICKSTART.md | 5分钟快速入门 |
| README.md | 完整项目说明 |
| DEPLOYMENT.md | 详细部署指南 |
| BUILD_AND_RUN.md | 编译运行指南 |
| TEST_REPORT.md | 测试报告 |
| PROJECT_STATUS.md | 项目状态总结 |

## 技术栈速查 📋

- Spring Boot 3.2.0
- Vue 3 + Element Plus
- PostgreSQL + PGVector
- MyBatis-Plus 3.5.5
- DeepSeek API
- Maven + npm

## 版本信息 ℹ️

- 项目版本: 1.0.0
- 创建日期: 2024-04-28
- Java文件: 23个
- Vue组件: 1个主页面
- API端点: 14个
- 数据库表: 6个

## 需要帮助？ 🆘

1. 查看详细文档
2. 检查控制台日志
3. 验证配置文件
4. 确认网络连接
5. 查看GitHub Issues

---

**状态**: ✅ 项目已完成，准备本地测试
**下一步**: 按照上述步骤启动系统
