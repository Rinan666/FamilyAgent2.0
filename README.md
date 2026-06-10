# FamilyAgent

## Tunnel

统一隧道能力现已独立于 `start-all` / `stop-all`，默认只支持 Cloudflare named tunnel，并统一单入口回源到 Frontend `:3000`。

Windows:

```text
tunnel.bat up
tunnel.bat down
tunnel.bat status
tunnel.bat logs
```

Linux / 云服务器:

```bash
./tunnel.sh up
./tunnel.sh down
./tunnel.sh status
./tunnel.sh logs
```

配置方式：
- 复制 `/.env.tunnel.example` 为 `/.env.tunnel.local`
- 使用统一配置键：`TUNNEL_ENABLED`、`TUNNEL_PROVIDER`、`TUNNEL_MODE`、`TUNNEL_CONFIG_PATH`、`TUNNEL_PUBLIC_HOST`、`TUNNEL_TARGET_URL`
- 若希望 `start-all` 自动带起隧道，设置 `START_TUNNEL=true`
- 浏览器统一走前端入口访问 `/api/*` 与 `/ai-proxy/*`，不再保留浏览器直连 AI 公网域名配置

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

配置约定：
- 根目录 `.env.infra.local` 只负责 PostgreSQL、Redis、RabbitMQ、MinIO 等本地基础设施配置
- 前端配置放在 `frontend/.env.local`
- AI 服务配置放在 `ai-service/.env`
- 不再把根目录配置视为“全项目统一 .env”

说明：
- `start-all.bat` / `start-all.ps1` 会优先使用 `backend/mvnw.cmd` 和 `ai-service/.venv`
- 首次在新机器启动前，先完成下面的本地环境初始化，避免全局 `mvn` / `python` 混用

停止服务：

```text
stop-all.bat
```

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

推荐：
- `backend` 不要直接使用裸 `mvn`，统一使用 `.\mvnw.cmd`
- `ai-service` 不要直接使用裸 `python`、`pip`、`pytest`、`uvicorn`，统一使用 `.venv` 或 `start.bat`

访问：

- 前端：http://localhost:3000
- 后端：http://localhost:8180
- AI 文档：http://localhost:8090/docs

## 常用检查

```bash
cd backend && .\mvnw.cmd test
cd ai-service && .\.venv\Scripts\python.exe -m pytest tests/ -v
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
