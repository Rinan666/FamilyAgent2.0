# AGENTS.md

## Project

FamilyAgent 是面向有传承意识家庭的家族记忆与软资产 AI 系统。

当前阶段：**Phase 2：家族记忆与软资产传承 MVP 验证**。

当前主线不是题库或拍照答疑，而是：

- 家族日记
- 家族经验 / 长者建议 / 家风价值观
- 镜像 Agent
- 家庭陪伴 AI
- 成长守护
- 授权记忆召回与隐私边界

历史 AI 家教、数学诊断、作业批改、错题本和学习报告代码暂时保留，作为成长信号和历史兼容来源；不要把它们重新推成普通用户主入口。

## Tech Stack

- Frontend：Next.js 16 + React 19 + TypeScript + shadcn/ui + Tailwind CSS
- Backend：Java 17 + Spring Boot 3.3 + MyBatis-Plus + Sa-Token
- AI Service：Python 3.12 + FastAPI + LiteLLM + sympy
- Infra：PostgreSQL 16 + pgvector + Redis 7 + RabbitMQ 3.13 + MinIO

## Architecture Rules

- Java Backend 是业务数据、家庭权限、照护授权和 AI 上下文裁决的权威源。
- Python AI Service 负责 LLM、整理、总结、隐私脱敏辅助、embedding 和数学验证，不绕过后端权限。
- Frontend 负责交互、展示、SSE 和低门槛录入，不信任前端传入的 `userId` 或家庭权限。
- 浏览器访问 Backend 使用 `/api/*`。
- 浏览器访问 AI 使用 `/ai-proxy/*`，由 Next.js 代理到 AI Service `/ai/*`。
- AI Service 验证 token 时调用 Backend `/api/users/me`。生产/Beta 必须 fail-closed，开发可配置 `AUTH_FAIL_OPEN=true`。
- 家庭记忆、成长记录、成员画像和未成年人数据进入 AI 前必须先经过后端权限过滤。
- 镜像 Agent 只能做基于授权记录的参考，不能假装本人、伪造记忆或代表真实想法。
- 视角称呼只影响显示和表达，不参与权限判断；照护授权才决定照护类数据可见性。

## How to Run

Windows 一键启动：

```text
start-all.bat
```

约定：
- `backend` 统一使用 `.\mvnw.cmd`
- `ai-service` 统一使用项目内 `.venv`
- 避免直接使用全局 `mvn`、`python`、`pip`、`pytest`、`uvicorn`

手动启动：

```bash
docker compose --env-file .env.infra.local up -d

cd backend
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev

cd ai-service
py -3.12 -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\start.bat

cd frontend
npm install
npm run dev
```

## Test Commands

```bash
cd backend && .\mvnw.cmd test
cd ai-service && .\.venv\Scripts\python.exe -m pytest tests/ -v
cd ai-service && ruff check app/
cd frontend && npm run lint
cd frontend && npx tsc --noEmit
cd frontend && npm run build
```

## Docs

只维护三份主文档：

- `docs/商业计划.md`
- `docs/路线图.md`
- `docs/技术架构.md`

避免新增临时 Markdown；需要补充时优先合并到这三份。
