#!/bin/bash

echo "================================"
echo "AI Kefu System - Quick Start"
echo "================================"

# 颜色定义
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# 检查环境
echo -e "${YELLOW}检查环境...${NC}"

# 检查Java
if ! command -v java &> /dev/null; then
    echo -e "${RED}错误: 未找到Java${NC}"
    exit 1
fi
java_version=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2)
echo "Java版本: $java_version"

# 检查Node
if ! command -v node &> /dev/null; then
    echo -e "${RED}错误: 未找到Node.js${NC}"
    exit 1
fi
node_version=$(node -v)
echo "Node版本: $node_version"

# 检查Maven
if ! command -v mvn &> /dev/null; then
    echo -e "${RED}错误: 未找到Maven${NC}"
    exit 1
fi
mvn_version=$(mvn -version | head -n 1)
echo "Maven版本: $mvn_version"

echo ""
echo -e "${GREEN}环境检查完成！${NC}"
echo ""

# 启动后端
echo -e "${YELLOW}启动后端服务...${NC}"
cd backend

if [ ! -d "target" ]; then
    echo "首次编译后端..."
    mvn clean package -DskipTests
fi

echo "启动Spring Boot后端..."
nohup mvn spring-boot:run > backend.log 2>&1 &
BACKEND_PID=$!

echo "后端进程ID: $BACKEND_PID"
echo "等待后端启动..."
sleep 10

# 检查后端是否启动成功
if curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
    echo -e "${GREEN}后端启动成功！${NC}"
else
    echo -e "${YELLOW}后端正在启动中（可能需要更多时间）...${NC}"
fi

# 启动前端
cd ../frontend
echo ""
echo -e "${YELLOW}启动前端服务...${NC}"

if [ ! -d "node_modules" ]; then
    echo "安装前端依赖..."
    npm install
fi

echo "启动Vue前端..."
nohup npm run dev > frontend.log 2>&1 &
FRONTEND_PID=$!

echo "前端进程ID: $FRONTEND_PID"
sleep 5

echo ""
echo -e "${GREEN}================================${NC}"
echo -e "${GREEN}启动完成！${NC}"
echo -e "${GREEN}================================${NC}"
echo ""
echo "后端地址: http://localhost:8080"
echo "前端地址: http://localhost:3000"
echo ""
echo "查看日志:"
echo "  后端日志: tail -f backend/backend.log"
echo "  前端日志: tail -f frontend/frontend.log"
echo ""
echo "停止服务:"
echo "  kill $BACKEND_PID"
echo "  kill $FRONTEND_PID"
echo ""
echo -e "${YELLOW}请确保已配置 .env 文件中的 AI_DASHSCOPE_API_KEY${NC}"
echo -e "${YELLOW}请确保PostgreSQL + PGVector已启动${NC}"
