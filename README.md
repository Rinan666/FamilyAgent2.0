# FamilyAgent

面向有传承意识家庭的家族记忆与软资产 AI 系统。

FamilyAgent 不把题库、拍照答疑或作业批改作为主竞争点，而是帮助家庭记录、整理、授权共享并活化家族日记、长辈经验、家风价值观、成员理解和下一代成长观察。

一句话：

**FamilyAgent 不是更强的 AI 家教，而是更懂这个家庭的 AI。**

当前阶段是 **Phase 2：家族记忆与软资产传承 MVP 验证**。目标是在 10-30 个真实用户/家庭中验证：家庭是否愿意记录真实内容，是否信任授权边界，镜像 Agent 和家庭陪伴 AI 是否能带来通用 AI 没有的家庭理解价值。

## 核心闭环

```text
创建家庭
-> 邀请成员
-> 写家族日记 / 录入家族经验 / 记录成长观察
-> 设置可见范围
-> 后端按权限过滤上下文
-> AI 整理、召回、生成陪伴或镜像参考
-> 家庭成员获得更具体的理解和建议
```

## 当前产品模块

| 模块 | 说明 |
|------|------|
| 家族日记 | 记录日常、重要事件、自我复盘、成长观察和给家人的话 |
| 家族经验 | 沉淀长者建议、家庭故事、家风价值观、健康提醒和人生教训 |
| 镜像 Agent | 基于授权记录做风格参考、价值观参考和自我复盘辅助；不能假装本人 |
| 家庭陪伴 AI | 支持自由对话、学习陪伴和家庭记忆问答 |
| 成长守护 | 记录体态、牙齿、视力、睡眠、运动、屏幕时间、情绪和沟通等观察 |
| 隐私授权 | 日记、经验、画像和未成年人数据进入 AI 前必须先经过后端权限过滤 |

早期 AI 家教、数学诊断、作业批改、错题本和学习报告代码暂时保留，作为历史兼容和成长信号来源；普通用户主线不再围绕这些入口展开。

## 技术栈

| 层 | 技术 |
|----|------|
| 前端 | Next.js 16 + React 19 + TypeScript + shadcn/ui + Tailwind CSS |
| 后端 | Java 17 + Spring Boot 3.3 + MyBatis-Plus + Sa-Token |
| AI 服务 | Python 3.12 + FastAPI + LiteLLM + sympy |
| 数据库 | PostgreSQL 16 + pgvector |
| 基础设施 | Redis 7 + RabbitMQ 3.13 + MinIO |

## 快速开始

Windows 一键启动：

```text
start-all.bat
```

停止服务：

```text
stop-all.bat
```

手动启动：

```bash
docker-compose up -d

cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev

cd ai-service
pip install -r requirements.txt
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000

cd frontend
npm install
npm run dev
```

访问：

- 前端：http://localhost:3000
- 后端：http://localhost:8080
- AI 文档：http://localhost:8000/docs

## 本地测试种子

`scripts/seed-family003.sql` 提供一个五人测试家族。

```powershell
docker cp scripts/seed-family003.sql fa-postgres:/tmp/seed-family003.sql
docker exec fa-postgres psql -U fa_user -d familyagent -f /tmp/seed-family003.sql
```

测试家族邀请码：`FAMILY003`

测试账号密码统一为：`Test@123456`

| 用户名 | 昵称 | 家族身份 |
|--------|------|----------|
| `family003_grandpa` | 李明德 | `OWNER` |
| `family003_father` | 陈远航 | `ADMIN` |
| `family003_mother` | 林秋然 | `GUARDIAN` |
| `family003_aunt` | 陈知微 | `MEMBER` |
| `family003_student` | 陈一诺 | `STUDENT` |

## 常用检查

```bash
cd backend && mvn test
cd ai-service && python -m pytest tests/ -v
cd ai-service && ruff check app/
cd frontend && npm run lint
cd frontend && npx tsc --noEmit
cd frontend && npm run build
```

## 文档

当前只保留三份主文档：

- [商业计划](docs/商业计划.md)
- [路线图](docs/路线图.md)
- [技术架构](docs/技术架构.md)

`AGENTS.md` 是给编码助手的短项目上下文，不作为产品文档维护。

## License

私有项目，保留所有权利。
