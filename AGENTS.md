# AGENTS.md

## Project: FamilyAgent (家族教育Agent)

### Overview
AI驱动的家族教育平台。三大核心模块：AI家教(Tutor)、个性化成长(Growth)、镜像Agent(Mirror)。
当前 Phase 1：AI家教MVP（数学学科），进度约 75%。

### Tech Stack
- **Frontend**: Next.js 16 (App Router) + React 19 + TypeScript + shadcn/ui + Tailwind CSS
- **Backend**: Java 17 + Spring Boot 3.3 + MyBatis-Plus + Sa-Token
- **AI Service**: Python 3.12 + FastAPI + LiteLLM + sympy
- **Infrastructure**: PostgreSQL 16 + pgvector + Redis 7 + RabbitMQ 3.13 + MinIO

### How to Run

**一键启动（Windows）：**
双击 `start-all.bat`（启动 4 个窗口：Docker 容器、AI 服务、后端、前端）
双击 `stop-all.bat`（停止全部服务并关闭窗口）

**手动启动：**
```bash
# 1. Infra
docker-compose up -d

# 2. Backend (port 8080)
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 3. AI Service (port 8000)
cd ai-service && pip install -r requirements.txt && python -m uvicorn app.main:app --host 0.0.0.0 --port 8000

# 4. Frontend (port 3000)
cd frontend && npm install && npm run dev
```

访问 http://localhost:3000

### Environment Variables
- `frontend/.env.local` — `BACKEND_URL`(默认 localhost:8080), `NEXT_PUBLIC_AI_SERVICE_URL`(默认 localhost:8000)
- `ai-service/.env` — LLM API keys, DB/Redis/RabbitMQ hosts, `BACKEND_URL`(token验证用)
- `.env` — 基础设施配置

### Key Design Decisions
- Java handles business logic (users, families, question bank, sessions, BKT persistence)
- Python handles AI (tutor agent, grader agent, generator agent, math verification, BKT computation)
- TypeScript handles interaction (chat UI, dashboards)
- Java ↔ Python communicates via REST (sync)；RabbitMQ 已配置但未启用
- Math correctness verified by sympy, not LLM
- BKT (Bayesian Knowledge Tracing) 计算由 Python 完成，Java 调 API 后持久化；Python 是唯一权威源
- SSE for streaming tutor responses from Python through to frontend
- 密码使用 BCrypt 加密（旧 SHA-256 账号登录时自动迁移）
- AI 服务有 Token 鉴权中间件，调 Java `/api/users/me` 验证 Sa-Token，fail-open 策略

### Architecture
```
Frontend (Next.js :3000) ──直连──> Python AI Service (FastAPI :8000) ──auth──> Java Backend (:8080)
       │                                    │                                      │
       └─ proxy /api ──────────────────────>┘                              LiteLLM / sympy
                                                                                │
                                                                     PostgreSQL / Redis / RabbitMQ / MinIO
```
- `/api/*` → Next.js 代理到 Java 后端
- `/ai/*` → 前端直连 Python AI 服务（避免代理 POST body 丢失）
- AI 服务调 Java `/api/users/me` 验证 token

### Project Structure
```
FamilyAgent/
├── backend/          Java Spring Boot 业务服务 (25 files, 14 tests)
├── ai-service/       Python FastAPI AI 服务 (14 files, 18 tests)
├── frontend/         Next.js 前端 (17 files, 0 tests)
├── scripts/          开发脚本 & 种子数据
├── docs/             文档 (进度报告、PRD)
├── start-all.bat     一键启动 (入口)
├── start-all.ps1     一键启动 (逻辑)
├── stop-all.bat      一键停止 (入口)
├── stop-all.ps1      一键停止 (逻辑)
├── docker-compose.yml
└── .github/workflows/ci.yml
```

### Frontend Details
- 路由：`/(auth)/login`, `/register` | `/(dashboard)/dashboard/{tutor,assessment,family,knowledge,settings}`
- 状态管理：Zustand (`authStore`, `chatStore`)
- API 客户端：`src/lib/api.ts` — 统一封装 Java 后端(`/api`) 和 AI 服务直连
- KaTeX 数学公式渲染：`src/components/tutor/MathRenderer.tsx`
- 类型定义：`src/types/index.ts` (17 个接口)
- ESLint：`eslint-config-next`，命令 `npm run lint`（Next.js 16 移除了 `next lint`，改用 `eslint . --ext .ts,.tsx`）

### Backend Details
- 模块：user / family / question / assessment / session
- 安全：Sa-Token 拦截 `/api/**`，`@JsonProperty(WRITE_ONLY)` 隐藏密码
- 错误码：32 个 ErrorCode 枚举（1000-9999）
- 分页：MyBatis-Plus `Page<T>` + `PageResult<T>`
- 测试：JUnit 5 + Mockito（`src/test/`）

### AI Service Details
- API：`/ai/tutor/*`（鉴权）、`/ai/assessment/*`（内部）、`/ai/health`（公开）
- Agent：TutorAgent（苏格拉底）、GraderAgent（步骤评分）、GeneratorAgent（出题）
- Engine：MathSandbox（sympy）、BayesianKnowledgeTracker（BKT）
- Prompt：`app/llm/prompts/` 目录，中文输出，结构化 JSON Schema
- 测试：pytest + ruff（`tests/`）

### Testing
```bash
# Backend tests (14)
cd backend && mvn test

# AI Service tests (18)
cd ai-service && python -m pytest tests/ -v

# AI Service lint
cd ai-service && ruff check app/

# Frontend lint
cd frontend && npm run lint

# Frontend type check
cd frontend && npx tsc --noEmit
```

### Git Workflow
- `main` — 生产分支
- 提交规范：Conventional Commits (`feat:`, `fix:`, `chore:`, `refactor:`, `docs:`)
- CI：on push/PR to main — Java Maven test, Python ruff + pytest, Frontend eslint + tsc + build
