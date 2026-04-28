@echo off
chcp 65001 > nul
echo ====================================
echo AI Kefu System - Quick Start (Windows)
echo ====================================
echo.

echo [检查环境...]
where java > nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo 错误: 未找到Java
    exit /b 1
)
java -version 2>&1 | findstr "version"
echo.

where node > nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo 错误: 未找到Node.js
    exit /b 1
)
node -v
echo.

where mvn > nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo 错误: 未找到Maven
    exit /b 1
)
echo.

echo [环境检查完成!]
echo.

echo [启动后端服务...]
cd backend

if not exist "target" (
    echo 首次编译后端...
    call mvn clean package -DskipTests
)

echo 启动Spring Boot后端...
start "Backend" cmd /k "mvn spring-boot:run"

echo 等待后端启动...
timeout /t 15 /nobreak > nul

echo.
echo [启动前端服务...]
cd ../frontend

if not exist "node_modules" (
    echo 安装前端依赖...
    call npm install
)

echo 启动Vue前端...
start "Frontend" cmd /k "npm run dev"

cd ..
echo.
echo ====================================
echo 启动完成！
echo ====================================
echo.
echo 后端地址: http://localhost:8080
echo 前端地址: http://localhost:3000
echo.
echo 请确保已配置.env文件中的DeepSeek API Key
echo 请确保PostgreSQL + PGVector已启动
echo.
pause
