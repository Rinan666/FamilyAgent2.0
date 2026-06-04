#!/bin/bash
# ============================================
# FamilyAgent 开发环境快速设置脚本
# ============================================

set -e

echo "=========================================="
echo "  FamilyAgent 开发环境设置"
echo "=========================================="

# 颜色
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# 检查依赖
check_command() {
    if ! command -v "$1" &> /dev/null; then
        echo -e "${YELLOW}[警告] 未找到 $1，请先安装${NC}"
        return 1
    else
        echo -e "${GREEN}[OK]${NC} $1"
    fi
}

echo ""
echo "检查环境依赖..."
check_command java
check_command python3
check_command node
check_command mvn
check_command docker

# 复制环境变量
echo ""
echo "设置环境变量..."
if [ ! -f .env ]; then
    cp .env.example .env
    echo -e "${GREEN}[OK]${NC} 已创建 .env 文件，请修改其中的配置"
else
    echo -e "${YELLOW}[跳过]${NC} .env 已存在"
fi

# 启动 Docker 服务
echo ""
echo "启动基础设施..."
docker-compose up -d
echo -e "${GREEN}[OK]${NC} PostgreSQL, Redis, RabbitMQ, MinIO 已启动"

# 等待服务就绪
echo ""
echo "等待服务就绪..."
sleep 5

# 后端
echo ""
echo "设置 Java 后端..."
cd backend
mvn install -DskipTests -q
echo -e "${GREEN}[OK]${NC} 后端依赖安装完成"
cd ..

# AI 服务
echo ""
echo "设置 Python AI 服务..."
cd ai-service
if [ ! -d "venv" ]; then
    python3 -m venv venv
fi
source venv/bin/activate
pip install -r requirements.txt -q
echo -e "${GREEN}[OK]${NC} AI 服务依赖安装完成"
deactivate
cd ..

# 前端
echo ""
echo "设置前端..."
cd frontend
npm install --silent
echo -e "${GREEN}[OK]${NC} 前端依赖安装完成"
cd ..

echo ""
echo "=========================================="
echo -e "${GREEN}  设置完成！${NC}"
echo ""
echo "启动方式："
echo "  1. 后端:  cd backend && mvn spring-boot:run"
echo "  2. AI服务: cd ai-service && source venv/bin/activate && uvicorn app.main:app --reload --port 8000"
echo "  3. 前端:  cd frontend && npm run dev"
echo ""
echo "  访问 http://localhost:3000"
echo "=========================================="
