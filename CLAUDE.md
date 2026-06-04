# CLAUDE.md

## Project: FamilyAgent (家族教育Agent)

### Overview
AI驱动的家族教育平台。三大核心模块：AI家教(Tutor)、个性化成长(Growth)、镜像Agent(Mirror)。
当前 Phase 1：AI家教MVP（数学学科）。

### Tech Stack
- **Frontend**: Next.js 14 (App Router) + TypeScript + shadcn/ui + Tailwind CSS
- **Backend**: Java 21 + Spring Boot 3.3 + MyBatis-Plus + Sa-Token
- **AI Service**: Python 3.12 + FastAPI + LiteLLM
- **Infrastructure**: PostgreSQL 16 + pgvector + Redis 7 + RabbitMQ 3.13 + MinIO

### How to Run
```bash
# 1. Infra
docker-compose up -d

# 2. Backend (port 8080)
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 3. AI Service (port 8000)
cd ai-service && pip install -r requirements.txt && uvicorn app.main:app --reload --port 8000

# 4. Frontend (port 3000)
cd frontend && npm install && npm run dev
```

### Key Design Decisions
- Java handles business logic (users, families, question bank, sessions)
- Python handles AI (tutor agent, grader agent, generator agent, math verification, BKT)
- TypeScript handles interaction (chat UI, dashboards)
- Java ↔ Python communicates via REST (sync) and RabbitMQ (async)
- Math correctness verified by sympy, not LLM
- BKT (Bayesian Knowledge Tracing) for adaptive learning
- SSE for streaming tutor responses from Python through to frontend

### Architecture
```
Frontend (Next.js) → Nginx → Java (Spring Boot) ──MQ── Python (FastAPI)
                                    │                      │
                              PostgreSQL              LiteLLM
                              Redis                   sympy
```

### Project Structure
```
FamilyAgent/
├── backend/        Java business service (Spring Boot)
├── ai-service/     Python AI service (FastAPI)
├── frontend/       Next.js web app
├── scripts/        Dev scripts & seed data
├── docs/           Documentation
└── docker-compose.yml
```
