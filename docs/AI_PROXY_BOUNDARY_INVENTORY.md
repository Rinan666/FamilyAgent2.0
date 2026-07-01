# AI Proxy Boundary Inventory

Updated: 2026-06-30

This document records the temporary frontend `/ai-proxy/*` boundary for Phase 1.5 of the AI Optimization plan. The proxy is not a business authority path. Agent, family data, memory writes, diary, growth, permission, audit, and tool execution capabilities must enter through Java Backend APIs and the Backend Agent Harness.

The executable boundary lives in `frontend/src/lib/api/aiProxyBoundary.ts`. Frontend callers must use `AI_PROXY_ROUTES` and `aiProxyUrl()` instead of writing raw `/ai-proxy/...` strings. The Next.js proxy route also uses the same boundary resolver and rejects unregistered paths.

## Current Allowlist

| Proxy route | Category | Boundary decision |
| --- | --- | --- |
| `/ai-proxy/memory/save-plan` | AI runtime | Allowed as draft planning only. Final memory writes remain Java Backend-owned. |
| `/ai-proxy/memory/organize-draft` | AI runtime | Allowed as draft generation only. Final memory writes remain Java Backend-owned. |
| `/ai-proxy/memory/persona-material-draft` | AI runtime | Allowed as persona material drafting only. Final profile writes remain Java Backend-owned. |
| `/ai-proxy/dip/faces/cluster-by-urls` | Non-business media | Allowed temporarily for media processing without direct family business writes. |

## Backend-Owned Prefixes

| Prefix | Required migration target |
| --- | --- |
| `/ai-proxy/agent/*` | `/api/agent/*` |
| `/ai-proxy/diary/*` | `/api/diaries/*` |
| `/ai-proxy/family/*` | `/api/families/*` |
| `/ai-proxy/growth/*` | `/api/growth/*` |
| `/ai-proxy/memory/*` except allowlisted draft routes | `/api/memories/*` |
| `/ai-proxy/memories/*` | `/api/memories/*` |
| `/ai-proxy/session/*` | `/api/sessions/*` |
| `/ai-proxy/skillrun/*` | `/api/skill-runs/*` |
| `/ai-proxy/tool/*` and `/ai-proxy/tools/*` | `/api/agent/*` |

Unknown routes are denied by default until they are explicitly classified.

## Current Source Call Sites

| Source | Proxy route constant | Notes |
| --- | --- | --- |
| `frontend/src/lib/api/memory.ts` | `MEMORY_SAVE_PLAN` | Draft planning only; final writes use Backend memory APIs. |
| `frontend/src/lib/api/memory.ts` | `MEMORY_ORGANIZE_DRAFT` | Draft generation only; final writes use Backend memory APIs. |
| `frontend/src/lib/api/memory.ts` | `MEMORY_PERSONA_MATERIAL_DRAFT` | Draft generation only; final writes use Backend profile or memory APIs. |
| `frontend/src/app/(dashboard)/album/page.tsx` | `DIP_FACES_CLUSTER_BY_URLS` | Temporary media processing route; no direct business writes. |

## Python AI Service Route Classification

| Python route | Category | Frontend proxy decision |
| --- | --- | --- |
| `/ai/agent/chat/stream` | Backend-owned Agent runtime | Denied through `/ai-proxy`; frontend must use Java Backend `/api/agent/chat/stream`. |
| `/ai/embedding/embed` | Backend-owned AI infrastructure | Denied through `/ai-proxy`; Backend uses `AIServiceClient`. |
| `/ai/memory/extract` | Removed memory capability | Denied through `/ai-proxy`; route removed from Python AI service and Backend client. |
| `/ai/memory/save-plan` | AI runtime draft planning | Temporarily allowed; final writes remain Backend-owned. |
| `/ai/memory/organize-draft` | AI runtime draft generation | Temporarily allowed; final writes remain Backend-owned. |
| `/ai/memory/persona-material-draft` | AI runtime draft generation | Temporarily allowed; final writes remain Backend-owned. |
| `/ai/memory/skills` and `/ai/memory/skills/{name}` | AI runtime metadata | Not currently proxied by frontend; classify before exposing. |
| `/ai/dip/faces/cluster-by-urls` | Non-business media processing | Temporarily allowed; no direct family business writes. |
| `/ai/dip/faces/cluster` | Non-business media processing | Not currently proxied by frontend; classify before exposing. |
| `/ai/health` and `/ai/health/ready` | Infrastructure health | Not proxied by frontend application code. |
