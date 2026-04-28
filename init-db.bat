@echo off
chcp 65001 > nul
echo =====================================
echo AI Kefu System - Database Setup (Windows)
echo =====================================
echo.

where psql > nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo 错误: 未找到psql命令
    echo 请安装PostgreSQL客户端
    pause
    exit /b 1
)

set DB_HOST=%DB_HOST%:localhost
set DB_PORT=%DB_PORT%:5432
set DB_NAME=%DB_NAME%:aikefu
set DB_USER=%DB_USER%:postgres
set DB_PASSWORD=%DB_PASSWORD%:postgres

echo 数据库配置:
echo 主机: %DB_HOST%
echo 端口: %DB_PORT%
echo 数据库: %DB_NAME%
echo 用户: %DB_USER%
echo.

set PGPASSWORD=%DB_PASSWORD%

echo 检查数据库连接...
psql -h "%DB_HOST%" -p "%DB_PORT%" -U "%DB_USER%" -d "postgres" -c "SELECT 1;" > nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo 错误: 无法连接到PostgreSQL
    echo 请检查PostgreSQL是否正在运行
    pause
    exit /b 1
)

echo 数据库连接成功！
echo.

echo 检查数据库 %DB_NAME% 是否存在...
psql -h "%DB_HOST%" -p "%DB_PORT%" -U "%DB_USER%" -d "postgres" -tAc "SELECT 1 FROM pg_database WHERE datname = '%DB_NAME%'" | findstr "1" > nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo 数据库 %DB_NAME% 已存在
    set /p RECREATE=是否要删除并重新创建? (y/N): 
    if /i "%RECREATE%"=="y" (
        echo 删除数据库...
        psql -h "%DB_HOST%" -p "%DB_PORT%" -U "%DB_USER%" -d "postgres" -c "DROP DATABASE %DB_NAME%;"
        echo 创建数据库...
        psql -h "%DB_HOST%" -p "%DB_PORT%" -U "%DB_USER%" -d "postgres" -c "CREATE DATABASE %DB_NAME%;"
    ) else (
        echo 使用现有数据库
    )
) else (
    echo 创建数据库 %DB_NAME%...
    psql -h "%DB_HOST%" -p "%DB_PORT%" -U "%DB_USER%" -d "postgres" -c "CREATE DATABASE %DB_NAME%;"
)

echo.
echo 启用pgvector扩展...
psql -h "%DB_HOST%" -p "%DB_PORT%" -U "%DB_USER%" -d "%DB_NAME%" -c "CREATE EXTENSION IF NOT EXISTS vector;"

echo.
echo 执行数据库schema...
psql -h "%DB_HOST%" -p "%DB_PORT%" -U "%DB_USER%" -d "%DB_NAME%" -f "backend\src\main\resources\schema.sql"

echo.
echo =====================================
echo 数据库设置完成！
echo =====================================
echo.
echo 数据库信息:
echo   主机: %DB_HOST%
echo   端口: %DB_PORT%
echo   数据库: %DB_NAME%
echo   用户: %DB_USER%
echo.
echo 下一步:
echo 1. 配置 backend\.env 文件中的DeepSeek API Key
echo 2. 启动后端: cd backend ^&^& mvn spring-boot:run
echo 3. 启动前端: cd frontend ^&^& npm install ^&^& npm run dev
echo.
pause
