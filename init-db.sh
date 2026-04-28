#!/bin/bash

echo "====================================="
echo "AI Kefu System - Database Setup"
echo "====================================="
echo ""

# 颜色定义
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

# 检查psql
if ! command -v psql &> /dev/null; then
    echo -e "${RED}错误: 未找到psql命令${NC}"
    echo "请安装PostgreSQL客户端"
    exit 1
fi

# 数据库连接参数
DB_HOST=${DB_HOST:-localhost}
DB_PORT=${DB_PORT:-5432}
DB_NAME=${DB_NAME:-aikefu}
DB_USER=${DB_USER:-postgres}
DB_PASSWORD=${DB_PASSWORD:-postgres}

echo -e "${YELLOW}数据库配置:${NC}"
echo "主机: $DB_HOST"
echo "端口: $DB_PORT"
echo "数据库: $DB_NAME"
echo "用户: $DB_USER"
echo ""

# 导出PGPASSWORD
export PGPASSWORD=$DB_PASSWORD

# 检查连接
echo "检查数据库连接..."
if ! psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "postgres" -c "SELECT 1;" > /dev/null 2>&1; then
    echo -e "${RED}错误: 无法连接到PostgreSQL${NC}"
    echo "请检查PostgreSQL是否正在运行，以及连接参数是否正确"
    exit 1
fi

echo -e "${GREEN}数据库连接成功！${NC}"
echo ""

# 创建数据库（如果不存在）
echo "检查数据库 $DB_NAME 是否存在..."
if psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "postgres" -tAc "SELECT 1 FROM pg_database WHERE datname = '$DB_NAME'" | grep -q 1; then
    echo -e "${YELLOW}数据库 $DB_NAME 已存在${NC}"
    read -p "是否要删除并重新创建? (y/N): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        echo "删除数据库..."
        psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "postgres" -c "DROP DATABASE $DB_NAME;"
        echo "创建数据库..."
        psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "postgres" -c "CREATE DATABASE $DB_NAME;"
    else
        echo "使用现有数据库"
    fi
else
    echo "创建数据库 $DB_NAME..."
    psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "postgres" -c "CREATE DATABASE $DB_NAME;"
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}数据库创建成功！${NC}"
    else
        echo -e "${RED}数据库创建失败${NC}"
        exit 1
    fi
fi

echo ""

# 启用pgvector扩展
echo "启用pgvector扩展..."
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -c "CREATE EXTENSION IF NOT EXISTS vector;"
if [ $? -eq 0 ]; then
    echo -e "${GREEN}pgvector扩展启用成功！${NC}"
else
    echo -e "${RED}pgvector扩展启用失败${NC}"
    echo "请确保已安装pgvector"
    exit 1
fi

echo ""

# 执行schema
echo "执行数据库schema..."
SCHEMA_FILE="backend/src/main/resources/schema.sql"
if [ -f "$SCHEMA_FILE" ]; then
    psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -f "$SCHEMA_FILE"
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}数据库schema执行成功！${NC}"
    else
        echo -e "${RED}数据库schema执行失败${NC}"
        exit 1
    fi
else
    echo -e "${RED}错误: 找不到schema文件 $SCHEMA_FILE${NC}"
    exit 1
fi

echo ""
echo -e "${GREEN}=====================================${NC}"
echo -e "${GREEN}数据库设置完成！${NC}"
echo -e "${GREEN}=====================================${NC}"
echo ""
echo "数据库信息:"
echo "  主机: $DB_HOST"
echo "  端口: $DB_PORT"
echo "  数据库: $DB_NAME"
echo "  用户: $DB_USER"
echo ""
echo "数据库表:"
echo "  - t_user (用户表)"
echo "  - t_rag_document (RAG文档表)"
echo "  - t_rag_chunk (文档分块表)"
echo "  - t_conversation (对话表)"
echo "  - t_conversation_message (对话消息表)"
echo "  - t_memory_compression (记忆压缩表)"
echo ""
echo -e "${YELLOW}下一步:${NC}"
echo "1. 配置 backend/.env 文件中的DeepSeek API Key"
echo "2. 启动后端: cd backend && mvn spring-boot:run"
echo "3. 启动前端: cd frontend && npm install && npm run dev"
echo ""
