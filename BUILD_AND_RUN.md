# AI客服系统 - 后端启动指南

## 重要说明

由于当前环境的Maven仓库存在权限问题，请按照以下步骤在本地环境中编译和启动后端。

## 前置要求

1. **JDK 17+** 已安装
2. **Maven 3.8+** 已安装
3. **PostgreSQL 15+** 已安装并运行
4. **pgvector扩展** 已启用

## 第一步：配置环境变量

编辑 `backend/.env` 文件：

```bash
cd backend
cp .env.example .env
```

编辑 `.env` 文件内容：

```env
# DeepSeek API配置（必需）
DEEPSEEK_API_KEY=sk-your-actual-deepseek-api-key
DEEPSEEK_BASE_URL=https://api.deepseek.com

# 数据库配置
DB_HOST=localhost
DB_PORT=5432
DB_NAME=aikefu
DB_USER=postgres
DB_PASSWORD=your-postgresql-password

# 服务端口
SERVER_PORT=8080
```

## 第二步：初始化数据库

### 方式1：使用脚本（推荐）

```bash
# macOS/Linux
chmod +x init-db.sh
./init-db.sh

# Windows
init-db.bat
```

### 方式2：手动执行

```bash
# 创建数据库
psql -U postgres -d postgres -c "CREATE DATABASE aikefu;"

# 启用pgvector扩展
psql -U postgres -d aikefu -c "CREATE EXTENSION IF NOT EXISTS vector;"

# 执行schema
psql -U postgres -d aikefu -f backend/src/main/resources/schema.sql
```

## 第三步：编译后端

```bash
cd backend

# 编译（跳过测试）
mvn clean compile -DskipTests

# 或者打包
mvn clean package -DskipTests
```

如果遇到Maven依赖下载问题，可以尝试：

```bash
# 使用国内镜像（如果在国内）
mvn clean compile -DskipTests -Dmaven.repo.remote=https://maven.aliyun.com/repository/public

# 或者只编译主代码
mvn compiler:compile -DskipTests
```

## 第四步：启动后端

### 方式1：使用Maven

```bash
mvn spring-boot:run
```

### 方式2：运行JAR包

```bash
java -jar target/ai-kefu-backend-1.0.0.jar
```

### 方式3：使用启动脚本

```bash
# macOS/Linux
chmod +x start.sh
./start.sh

# Windows
start.bat
```

## 验证后端是否启动成功

后端启动后，应该能看到以下输出：

```
Tomcat started on port(s): 8080
Started AiKefuApplication in X.XXX seconds
```

访问健康检查端点：

```bash
curl http://localhost:8080/actuator/health
```

## 常见问题

### 1. Maven依赖下载失败

**问题**：下载依赖时出现网络错误或超时

**解决方案**：
- 检查网络连接
- 使用国内镜像：在 `pom.xml` 的 `<repositories>` 中添加
```xml
<repository>
    <id>aliyun</id>
    <url>https://maven.aliyun.com/repository/public</url>
</repository>
```

### 2. 数据库连接失败

**问题**：`Connection refused` 或认证失败

**解决方案**：
- 检查PostgreSQL是否正在运行
- 检查端口5432是否正确
- 验证用户名和密码
- 检查pgvector扩展是否已启用

```bash
psql -U postgres -d aikefu -c "SELECT * FROM pg_extension WHERE extname = 'vector';"
```

### 3. 端口8080被占用

**问题**：`Port 8080 was already in use`

**解决方案**：
- 修改 `.env` 文件中的 `SERVER_PORT`
- 或停止占用8080端口的进程

```bash
# macOS/Linux 查找占用端口的进程
lsof -i :8080

# Windows 查找占用端口的进程
netstat -ano | findstr :8080
```

### 4. API Key无效

**问题**：调用DeepSeek API时返回401或认证错误

**解决方案**：
- 检查 `.env` 文件中的 `DEEPSEEK_API_KEY`
- 确保API Key是从DeepSeek平台获取的有效Key
- 检查API余额是否充足

### 5. 编译时内存不足

**问题**：`OutOfMemoryError`

**解决方案**：
```bash
export MAVEN_OPTS="-Xmx2g -Xms512m"
mvn clean compile -DskipTests
```

## 启动后端验证清单

✅ PostgreSQL正在运行  
✅ pgvector扩展已启用  
✅ 数据库`aikefu`已创建  
✅ Schema已执行  
✅ `.env`文件已配置  
✅ DeepSeek API Key已配置  
✅ Maven依赖已下载  
✅ 项目已编译  
✅ 端口8080可用  
✅ 后端启动成功  

## 下一步

后端启动成功后，可以启动前端：

```bash
cd frontend
npm install
npm run dev
```

访问 http://localhost:3000 查看管理界面。

## 获取帮助

如果遇到问题：
1. 查看后端控制台日志
2. 检查 `application.yml` 配置
3. 验证数据库连接
4. 检查API Key有效性
5. 确认网络连接正常
