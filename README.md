# 家族教育Agent (Family Education Agent)

> AI驱动的家族教育与管理平台 — 家教、成长、传承

现实痛点是：孩子长大后认为自己没有受到良好教育、父母没有时间或没有能力或没有金钱教育孩子、家族知识传承容易遗失或无法被良好继承、人们害怕写的日记被人看到或不擅长写日记或觉得写日记太麻烦而对自己的成长和变化了解甚少但后来想深入了解自己的时候没有资料参考。家教是代替传统的家教老师的功能，也能代替传统父母的功能。个性化成长是每个家族成员可以写快捷日记，还有家族知识库。镜像Agent是根据日记而随家族成员成长的Agent，模拟成员的精神体。

## 三大核心能力

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

**一键启动（Windows）：**
双击项目根目录下的 `start-all.bat`

**手动启动：**

### 1. 启动基础设施
```bash
docker-compose up -d
```

### 2. 启动 AI 服务
```bash
cd ai-service
pip install -r requirements.txt
python -m uvicorn app.main:app --port 8000
```

### 3. 启动后端
```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
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
