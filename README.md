# 家族教育Agent (Family Education Agent)

> AI驱动的家族教育与管理平台 — 家教、成长、传承

## 项目简介

家族教育Agent是一个以AI Agent为核心的教育平台，围绕三大核心能力构建：

- **AI家教 (Tutor)**：苏格拉底式智能讲题、自适应抽题、步骤级批改、学力评估
- **个性化成长 (Growth)**：语音快捷日记、家族知识库、成长仪表盘
- **镜像Agent (Mirror)**：基于日记数据成长的数字精神体

## 技术栈

| 层 | 技术 |
|----|------|
| 前端 | Next.js 14 + TypeScript + shadcn/ui + Tailwind CSS |
| 后端 | Java 21 + Spring Boot 3.3 + MyBatis-Plus |
| AI服务 | Python 3.12 + FastAPI + LiteLLM |
| 数据库 | PostgreSQL 16 + pgvector |
| 缓存 | Redis 7 |
| 消息队列 | RabbitMQ 3.13 |
| 对象存储 | MinIO |

## 快速开始

### 前置要求

- Java 21+
- Python 3.12+
- Node.js 20+
- Docker & Docker Compose
- Maven 3.9+

### 1. 启动基础设施

```bash
docker-compose up -d
```

### 2. 启动后端服务

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### 3. 启动AI服务

```bash
cd ai-service
python -m venv venv
source venv/bin/activate  # Windows: venv\Scripts\activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000
```

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

访问 http://localhost:3000

## 项目结构

```
FamilyAgent/
├── backend/          # Java Spring Boot 业务服务
├── ai-service/       # Python FastAPI AI服务
├── frontend/         # Next.js 前端
├── docs/             # 文档
├── scripts/          # 工具脚本
└── docker-compose.yml
```

## 文档

- [商业计划书](商业计划书.md)
- [项目开发计划书](项目开发计划书.md)

## 环境变量

复制 `.env.example` 到 `.env` 并填入配置：

```bash
cp .env.example .env
```

## License

私有项目，保留所有权利。
