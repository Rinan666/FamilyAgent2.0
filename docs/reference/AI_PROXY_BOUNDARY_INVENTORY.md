# AI Proxy Boundary Inventory

Updated: 2026-07-15

This document records the temporary frontend `/ai-proxy/*` boundary for Phase 1.5 of the AI Optimization plan. The proxy is not a business authority path. Agent, family data, memory writes, diary, growth, permission, audit, and tool execution capabilities must enter through Java Backend APIs and the Backend Agent Harness.

The executable boundary lives in `frontend/src/lib/api/aiProxyBoundary.ts`. Frontend callers must use `AI_PROXY_ROUTES` and `aiProxyUrl()` instead of writing raw `/ai-proxy/...` strings. The Next.js proxy route also uses the same boundary resolver and rejects unregistered paths.

## Current Allowlist

| Proxy route | Category | Boundary decision |
| --- | --- | --- |
| `/ai-proxy/dip/faces/cluster-by-urls` | Non-business media | Allowed temporarily for media processing without direct family business writes. |

## Backend-Owned Prefixes

| Prefix | Required migration target |
| --- | --- |
| `/ai-proxy/agent/*` | `/api/agent/*` |
| `/ai-proxy/diary/*` | `/api/diaries/*` |
| `/ai-proxy/family/*` | `/api/families/*` |
| `/ai-proxy/growth/*` | `/api/growth/*` |
| `/ai-proxy/memory/save-plan` | `/api/agent/save-memory-plan` |
| `/ai-proxy/memory/organize-draft` | `/api/agent/organize-draft` |
| `/ai-proxy/memory/persona-material-draft` | `/api/agent/persona-material-draft` |
| `/ai-proxy/memory/*` | `/api/memories/*` or a focused `/api/agent/*` capability |
| `/ai-proxy/memories/*` | `/api/memories/*` |
| `/ai-proxy/session/*` | `/api/sessions/*` |
| `/ai-proxy/skillrun/*` | `/api/skill-runs/*` |
| `/ai-proxy/tool/*` and `/ai-proxy/tools/*` | `/api/agent/*` |

Unknown routes are denied by default until they are explicitly classified.

## Current Frontend Call Sites

| Source | Route | Notes |
| --- | --- | --- |
| `frontend/src/lib/api/memory.ts` | Backend `/api/agent/save-memory-plan` | Backend records the save-plan SkillRun and AgentRun before returning the typed plan. |
| `frontend/src/lib/api/memory.ts` | Backend `/api/agent/organize-draft` | Backend validates family membership and records SkillRun, AgentRun, and trace before calling Python SkillRuntime. |
| `frontend/src/lib/api/memory.ts` | Backend `/api/agent/persona-material-draft` | Backend validates family membership and records SkillRun, AgentRun, and trace before calling Python SkillRuntime. |
| `frontend/src/app/(dashboard)/album/page.tsx` | `DIP_FACES_CLUSTER_BY_URLS` | Temporary media processing route; no direct business writes. |

## Python AI Service Route Classification

| Python route | Category | Frontend proxy decision |
| --- | --- | --- |
| `/ai/agent/chat/stream` | Backend-owned Agent runtime | Denied through `/ai-proxy`; frontend must use Java Backend `/api/agent/chat/stream`. |
| `/ai/embedding/embed` | Backend-owned AI infrastructure | Denied through `/ai-proxy`; Backend uses `AIServiceClient`. |
| `/ai/memory/extract` | Removed memory capability | Denied through `/ai-proxy`; route removed from Python AI service and Backend client. |
| `/ai/memory/save-plan` | Backend-mediated AI runtime planning | Denied through `/ai-proxy` and requires internal service identity; Backend `/api/agent/save-memory-plan` calls it through `AIServiceClient` and records `SkillRun`. |
| `/ai/memory/organize-draft` | Backend-mediated AI runtime draft generation | Denied through `/ai-proxy` and requires internal service identity; Backend `/api/agent/organize-draft` calls it through `DraftGenerationClient` and records SkillRun, AgentRun, and trace. |
| `/ai/memory/persona-material-draft` | Backend-mediated AI runtime draft generation | Denied through `/ai-proxy` and requires internal service identity; Backend `/api/agent/persona-material-draft` calls it through `DraftGenerationClient` and records SkillRun, AgentRun, and trace. |
| `/ai/memory/skills` and `/ai/memory/skills/{name}` | AI runtime metadata | Not currently proxied by frontend; classify before exposing. |
| `/ai/dip/faces/cluster-by-urls` | Non-business media processing | Temporarily allowed; no direct family business writes. |
| `/ai/dip/faces/cluster` | Non-business media processing | Not currently proxied by frontend; classify before exposing. |
| `/ai/health` and `/ai/health/ready` | Infrastructure health | Not proxied by frontend application code. |
