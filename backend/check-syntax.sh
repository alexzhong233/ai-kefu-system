#!/bin/bash

echo "================================"
echo "AI Kefu Backend - Code Check"
echo "================================"
echo ""

# 检查Java文件
echo "检查Java文件..."
find src -name "*.java" | while read file; do
    echo "检查: $file"
    # 基本语法检查
    if grep -q "public class\|public interface\|public enum" "$file"; then
        echo "  ✓ 找到公共类/接口"
    fi
done

echo ""
echo "项目结构:"
echo "---"
find src -type f -name "*.java" | sort

echo ""
echo "文件统计:"
echo "Java文件数: $(find src -name "*.java" | wc -l)"
echo "Controller数: $(find src -name "*Controller.java" | wc -l)"
echo "Service数: $(find src -name "*Service.java" | wc -l)"
echo "Mapper数: $(find src -name "*Mapper.java" | wc -l)"
echo "Entity数: $(find src -name "*.java" -path "*/entity/*" | wc -l)"

echo ""
echo "检查完成！"
echo ""
echo "下一步:"
echo "1. 确保PostgreSQL + pgvector已运行"
echo "2. 配置 backend/.env 文件"
echo "3. 运行: mvn clean compile -DskipTests"
echo "4. 运行: mvn spring-boot:run"
