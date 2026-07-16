# FamilyAgent AI Harness 统一升级计划

> 更新日期：2026-07-16
> 来源：本文件合并原 `AI_OPTIMIZATION_NEW_PLAN.md` 与 `AGENT_HARNESS_PLAN.md`。
> 范围：`ai-service/`、`backend/src/main/java/com/familyagent/infra/ai/`、后端 `agent` / `memory` / `diary` / `growth` / `mirror` / `family` / `session` / `skillrun` 中调用 AI 或 Agent 工具的链路，以及前端 Agent 交互与确认流程。
> 目标：把 FamilyAgent 从“安全增强版 AI 网关 + 固定业务端点”逐步升级为统一的 AI Architecture Harness。所有 Agent、Skill、Tool、Memory Recall、Web Search、Trace、Replay、Eval、Prompt / Model Governance 都应接入同一个演进路线，而不是拆成多份互相前置的计划。

---

## 0. 计划关系结论

原 AI 优化计划和 Agent Harness 计划不应再作为两份独立计划维护。

统一后的关系是：

1. 已完成的 AI 优化治理，作为本计划的 `Phase 0：AI 安全与契约地基`。
2. 当前最该开始的工程落地，作为本计划的 `Phase 1：轻型 Agent Harness 骨架`。
3. Skill Runtime、Trace / Replay、Eval / Regression、Prompt / Model Governance 作为后续阶段继续演进。
4. 重型 Harness 不是立即引入工作流引擎，而是在轻型闭环跑稳后按触发条件升级。

因此后续只维护本文件作为 AI / Agent / Harness 的主计划。

---

## 1. 终极目标

本计划的终极目标是把项目 AI 部分升级成世界主流形态的 AI Architecture Harness，而不是只做局部优化、只补几条测试、或只换一个 agent 框架。

Harness 在 FamilyAgent 中的定位是 AI 基础设施中枢：

1. Runtime Harness
   - 统一运行 Agent、Skill、Tool、LLM、Retrieval、Embedding。
   - 所有核心 AI 能力都通过 runner / executor 进入系统，避免继续散落在 endpoint、helper 或大 Service 中。

2. Contract Harness
   - 固定 API、SSE event、errorCode、Embedding 状态、Web Search 隐私边界。
   - 确保内部可以重构，backend / frontend 契约不漂移。

3. Tool / Permission Harness
   - 统一声明 Agent 能调用哪些工具、需要什么权限、是否有写入副作用、是否需要用户确认。
   - 工具调用必须可审计、可失败、可追踪，不能绕过 backend 权限。

4. Trace / Replay Harness
   - 每次 Agent run 记录 runId、step、tool call、prompt/schema/skill 版本、失败原因。
   - 支持脱敏回放，用于定位问题、比较模型升级效果、复盘 AI 决策路径。

5. Evaluation / Governance Harness
   - 通过 golden cases、mock LLM、真实模型抽样、trajectory eval 持续验证 AI 行为。
   - 模型、prompt、schema、skill 变更有回归报告、上线门禁和回滚依据。

目标结构：

```text
API Adapter
  -> Contract Harness
  -> Agent / Skill Runtime Harness
  -> Tool / LLM / Retrieval Gateways
  -> Trace / Replay Harness
  -> Evaluation / Governance Harness
```

达成后，新增 AI 能力的默认方式应从“新增 endpoint + helper + prompt 常量”转变为“新增 manifest / tool / executor + eval cases + trace span + contract tests”。

---

## 2. 当前地基

FamilyAgent 已经完成一轮关键 AI 安全与契约治理，足以启动轻型 Agent Harness：

1. 用户可控输入不能提升为高可信上下文。
   - `AgentMemoryContextFacade`、`AgentMirrorContextFacade`、`AgentPersonaContextFacade` 已把高可信上下文收回后端生成。
   - 前端在可由服务端生成可信上下文时，不再发送客户端 `memoryContext`。

2. AI provider 失败不能伪装成成功回答。
   - 非流式接口返回明确 `success=false` / `errorCode`。
   - stream 失败输出结构化 `error` event，不伪装成普通 assistant 文本。

3. Chat stream 契约已强类型化。
   - 继续稳定支持 `content`、`metadata`、`done`、`error`。
   - EOF 前没有收到 terminal event 时，前端视为错误。

4. Embedding 质量边界已经强化。
   - provider 失败、degraded、维度异常、非有限值不会写入 READY 索引。
   - 失败可见并进入明确失败语义。

5. Web Search 隐私治理已有基础。
   - 外部搜索前做 query rewrite / PII stripping。
   - 高隐私 query 默认跳过。
   - 日志不应落原始敏感 query。

6. Backend / AI service / frontend 核心契约已逐步强类型化。
   - Chat stream payload、memory extraction、embedding、error event 等已有 Java DTO / Python Pydantic schema。
   - 后续新增稳定契约不应继续使用裸 `Map<String, Object>`。

7. Timeout、retry、fallback、requestId、metrics 已有统一基础。
   - `AIServiceClient` 已接入 Resilience4j。
   - embedding / memory extraction 的 retry 与 fallback 语义已补测试。

这部分是本计划的 Phase 0，后续不再作为独立前置计划维护。

---

## 3. 当前缺口

AI 安全地基已经比较稳，但还没有形成真正的 Harness：

1. 没有统一 `AgentTool` 抽象。
2. 没有统一工具注册中心。
3. 没有统一工具权限门。
4. 没有统一工具调用审计记录。
5. 保存类动作的用户确认还不是 harness 级状态。
6. 没有 `AgentRun` / `AgentStep` 的最小生命周期记录。
7. skill 仍偏静态登记表和固定 endpoint，不是统一 runtime。
8. `SkillRunService` 更像记录器，不负责 lifecycle、确认、失败、幂等。
9. Web Search、Memory Recall、Save Plan、Draft Generation 仍偏固定 pipeline 或业务分支，不是统一 tool call。
10. requestId 可以串起请求，但不能描述一次 Agent run 内部的 tool / skill / LLM 轨迹。
11. Eval 还没有成为 AI 变更门禁。
12. prompt、schema、skill 定义缺少统一版本化和回归报告。
13. Python AI service 入口还没有完全收敛。后端业务链路主要通过 `AIServiceClient` 调用 Python AI service，但前端仍存在 `/ai-proxy/*` 直连代理；Agent、家庭记忆、写入、权限相关能力必须避免绕过 Java Backend。

---

## 4. 核心原则

### 4.1 接口稳定，内部演进

必须保护的外部契约：

- URL 不漂移，例如 `/ai/agent/chat/stream`、`/ai/memory/save-plan`、`/ai/embedding/embed`。
- Java 后端继续使用统一 `Result<T>`。
- AI JSON 能力继续遵守 `{ success, data }`。
- SSE event 结构继续稳定支持 `content`、`metadata`、`done`、`error`。
- `requestId` 继续从 frontend 到 backend 再到 AI service 透传。
- auth、限流、隐私脱敏、prompt injection 检测、Web Search 脱敏不能在重构中丢失。
- Embedding 仍必须保证失败、degraded、维度异常、非有限值不会写入 READY 索引。

隐藏契约也必须保护：

- stream EOF 如果没有收到 `done` 或 `error`，客户端应视为失败。
- provider 失败不能被包装成普通 assistant 文本。
- backend stream proxy 必须保留 AI service 的结构化 error event。
- `memory_context` 即使字段继续存在，也不能重新变成客户端可伪造的高可信上下文。
- Web Search 不能把家庭隐私原文发给外部服务，也不能在日志里落原始隐私 query。

### 4.2 先轻型闭环，再重型运行时

当前不直接引入 Temporal、Camunda、Conductor 等独立工作流引擎。

优先做：

- 工具声明。
- 工具注册。
- 权限检查。
- 确认策略。
- 调用记录。
- 失败语义。
- 最小 run / step / tool call 轨迹。

等轻型闭环跑稳，再考虑跨天恢复、长任务队列、完整状态机和多 worker 调度。

### 4.3 Backend 仍是业务权威

- 不让模型直接拥有数据库写权限。
- 不让 Agent 绕过 backend 权限写入记忆、日记、成长观察。
- 跨模块协作优先走 Facade。
- 稳定契约优先强类型 DTO / VO / record。
- 不把新逻辑继续堆进热点大 Service。

### 4.4 AI service 入口收敛

Python AI service 是 Java Backend 的 AI runtime 子系统，不是第二套业务主控后端。

- 后端业务 AI 能力统一通过 `AIServiceClient` 访问 Python AI service。
- Agent、家庭记忆、日记、成长观察、权限、确认、审计相关能力不得通过前端 `/ai-proxy/*` 绕过 Java Backend。
- Python AI runtime 可以负责 prompt、model、planning、tool-call proposal、trace、eval，但真实业务工具执行必须回到 Backend Agent Harness。
- 前端 `/ai-proxy/*` 只能作为临时或明确标注的非业务主权路径；若能力涉及家庭数据、权限、写入或审计，必须迁入 Java Backend API。
- 纯媒体处理、文件分析等能力如果暂时保留直连代理，必须单独登记边界、权限假设和迁移策略，避免与 Agent Harness 混用。

---

## 5. 分阶段计划

### Phase 0：AI 安全与契约地基

状态：核心地基已基本完成，后续只做回归和补强。

目标：保证启动 Agent Harness 时，不会绕开已经建立的 AI 安全边界。

已完成重点：

1. 服务端可信上下文生成。
2. AI provider 失败结构化。
3. Chat stream typed event。
4. Embedding 失败可见与 READY 索引保护。
5. Web Search 隐私 query 脱敏。
6. Backend / AI service / frontend 核心 DTO / Pydantic 契约收敛。
7. Timeout、retry、fallback、metrics、requestId 基础统一。

后续补强项：

1. 继续增加 contract tests。
2. 继续增加 stream EOF / provider failure / embedding degraded 回归测试。
3. 继续清理裸 `Map` 与 `Object metadata`。
4. 继续补 Web Search 隐私日志验证。

验收标准：

- 新增 AI 能力不会降低上述安全边界。
- 任何 AI 契约改动必须同步 backend DTO、AI Pydantic schema、frontend parser 和测试。

---

### Phase 1：轻型 Agent Harness 骨架

状态：已完成。轻型 Agent Harness 最小闭环已接入真实 family-memory Agent 入口。

最近进度：

- 2026-06-29：完成后端轻型 harness 第一刀，提交 `12f5d61c Add lightweight agent harness foundation`。
- 2026-06-29：拆分 `AgentToolExecutor` 协作职责，提交 `be5dd77b Split agent tool executor collaborators`。
- 2026-06-29：将 `AgentChatController` 的 family-memory 上下文解析迁入 `AgentToolExecutor -> recall_family_memory`，并集中工具名、隐藏工具实现类。
- 2026-06-29：明确 Python AI service 入口收敛原则：业务 AI 调用走 `AIServiceClient`，Agent / 家庭数据 / 写入 / 审计能力不得经前端 `/ai-proxy/*` 绕过 Backend Harness。

已完成：

1. `AgentTool`。
2. `AgentToolDescriptor`。
3. `AgentToolRegistry`。
4. `AgentToolExecutor`。
5. `AgentToolPermissionGate`。
6. `AgentToolInputValidator`。
7. `AgentToolErrorMapper`。
8. `AgentToolDescriptorFactory`。
9. `AgentToolCallRequest` / `AgentToolCallResult`。
10. `agent_tool_calls` 最小审计表。
11. `recall_family_memory` 只读工具。
12. 工具权限拒绝、输入错误、未知工具、审计摘要、只读工具 Facade 调用测试。
13. `AgentChatMemoryContextResolver` 已让真实 family-memory Agent 入口通过 `AgentToolExecutor` 调用 `recall_family_memory`。
14. `RecallFamilyMemoryTool` 已改为包内可见，上层通过 `AgentToolName.RECALL_FAMILY_MEMORY` 走 executor，降低直接注入工具实现的风险。
15. 已补 `AgentChatMemoryContextResolverTest` 验证真实入口使用 tool executor，且不再信任客户端 `memoryContext`。

目标：建立 Agent 工具声明、注册、权限、执行和审计的最小闭环。

建议新增后端包：

`backend/src/main/java/com/familyagent/module/agent/harness/`

核心对象：

1. `AgentTool`
   - 工具接口。
   - 声明工具名、输入类型、输出类型、是否写入、是否需要确认。

2. `AgentToolDescriptor`
   - 稳定工具描述。
   - 包含 name、description、inputType、outputType、sideEffect、requiresConfirmation、privacyLevel。

3. `AgentToolRegistry`
   - 工具注册中心。
   - 通过 Spring 注入收集所有 `AgentTool`。
   - 禁止业务代码临时拼工具名绕过 registry。

4. `AgentToolPermissionGate`
   - 统一工具权限门。
   - 根据 familyId、viewerUserId、toolName、input 判断是否允许执行。

5. `AgentToolExecutor`
   - 统一工具执行器。
   - 负责权限检查、确认检查、异常映射、审计记录。

6. `AgentToolCallRequest`
   - 工具调用请求 DTO。
   - 不使用裸 `Map` 作为主契约。

7. `AgentToolCallResult`
   - 工具调用结果 DTO。
   - 失败必须结构化返回 errorCode。

8. `AgentRunContext`
   - 一次 Agent 调用上下文。
   - 包含 requestId、familyId、viewerUserId、sessionId、agentMode、subject、contextLabel。

9. `agent_tool_calls`
   - 最小审计表。
   - 字段包含 toolName、familyId、viewerUserId、requestId、inputSummary、status、errorCode、createdAt。

第一批工具：

1. `recall_family_memory`
   - 只读。
   - 使用 `AgentMemoryContextFacade` 或授权召回边界。
   - 不需要用户确认。

2. `create_diary_entry`
   - 写入。
   - 默认需要用户确认。
   - 复用 diary 模块现有 Service。

3. `create_family_memory`
   - 写入。
   - 默认需要用户确认。
   - 复用 memory 模块现有写入能力。

4. `create_growth_guard_record`
   - 写入。
   - 默认需要用户确认。
   - 必须检查 targetUserId 和 care authorization。

5. `draft_weekly_growth_report`
   - 草稿。
   - 第一阶段只生成草稿，不自动保存。
   - 需要引用来源摘要。

推荐第一刀只实现：

1. `AgentTool`。
2. `AgentToolRegistry`。
3. `AgentToolExecutor`。
4. `AgentToolPermissionGate`。
5. `AgentToolCallRequest` / `AgentToolCallResult` / `AgentToolDescriptor`。
6. `agent_tool_calls` 最小审计表。
7. `recall_family_memory` 只读工具。

验收标准：

- [已完成] 工具不能绕过 registry 执行。真实 family-memory Agent 入口已迁入 executor，工具实现类包内可见，上层只使用集中工具名。
- [已完成] 工具执行前必经 permission gate。已有 `AgentToolExecutorTest` 覆盖拒绝路径。
- [已完成] 每次 executor 工具调用都有审计记录。已有 `AgentToolAuditServiceTest` 覆盖不落原始隐私输入。
- [已完成] 只读工具失败返回结构化错误，不伪装成普通 assistant 文本。已有未知工具、输入错误、权限拒绝、执行异常映射。
- [已完成] 不破坏当前可信上下文与授权召回边界。`recall_family_memory` 复用 `AgentMemoryContextFacade`。

---

### Phase 1.5：AI service 入口收敛与双层 Harness 边界

状态：已完成。前端 `/ai-proxy/*` 已有显式边界、清单、源码调用集中化和防回退测试；Phase 2 可以在该边界上继续推进写入工具与确认门。

最近进度：

- 2026-06-30：新增 `/ai-proxy/*` 共享边界模块 `frontend/src/lib/api/aiProxyBoundary.ts`，将允许路由集中为 `AI_PROXY_ROUTES` 与 `aiProxyUrl()`。
- 2026-06-30：Next.js `/ai-proxy/[...path]` 代理已接入同一 boundary resolver，未登记路径、Agent / family / memory 写入 / diary / growth / session / tool 等 Backend-owned 前缀默认返回 `403`。
- 2026-06-30：前端现有 `/ai-proxy` 调用点已改为共享常量；源码中不再直接散落 `/ai-proxy/...` 调用字符串。
- 2026-06-30：新增 `docs/AI_PROXY_BOUNDARY_INVENTORY.md`，记录当前 allowlist、Backend-owned 前缀、迁移目标和源码调用点。
- 2026-06-30：新增 `aiProxyBoundary.test.ts`，覆盖 AI runtime 草稿路由放行、媒体处理放行、Agent / family / memory 写入绕过 Backend 被拒、未知路径默认拒绝。
- 2026-06-30：补充源码扫描防回退测试，确保前端新增 `/ai-proxy/...` 调用必须集中到 `AI_PROXY_ROUTES` 与 `aiProxyUrl()`。
- 2026-06-30：盘点 Python AI service 当前入口，并在 `docs/AI_PROXY_BOUNDARY_INVENTORY.md` 中标注 Agent、Embedding、Memory、DIP、Health 路由分类与前端代理结论。
- 2026-07-15：完成容器级入口收敛验证。PostgreSQL 16.14 中 V15/V16 均为成功状态，`agent_runs` / `agent_run_steps` 实表存在；Backend、AI Service、Frontend、Redis、RabbitMQ、MinIO 全部启动，健康端点返回 200。无内部身份调用 Python draft 路由返回 401，内部身份可通过认证并执行 synthetic organize-draft smoke，前端 `/ai-proxy/memory/organize-draft` 返回 403，Backend 未登录调用保持统一 `Result.code=401`。
- 2026-07-15：修复 AI Service 镜像构建对固定镜像站的依赖。Dockerfile 的 Debian/PyPI 源改为可配置 build args，默认回到官方源；使用干净 `python:3.12-slim` 完整构建成功，`opencv-python-headless`、`insightface`、`onnxruntime` 均进入正式运行镜像，DIP 路由恢复加载，draft synthetic smoke 继续成功。
- 2026-07-16：完成 AI Service 镜像依赖瘦身与缓存验证。InsightFace 继续固定为 `1.0.1`，但其桌面 OpenCV 依赖元数据在构建期规范化为已固定的 `opencv-python-headless==4.13.0.92`；`pip check` 无破损依赖，正式镜像不再包含 `opencv-python`，ONNX / ONNX Runtime 导入、DIP JPEG 解码、readiness 和内部 organize-draft synthetic smoke 均通过。镜像由 `508181971` B 降至 `462746732` B，减少 `45435239` B（约 `8.94%`）；BuildKit pip cache 已验证命中。
- 2026-07-16：将 AI Service readiness 从固定 `ready/not_checked` 占位响应升级为强类型配置检查。主 LLM provider 凭据或生产内部服务身份缺失时返回 HTTP 503 和 `not_ready`，不调用付费模型或发送家庭数据；AI Service 当前不直接访问数据库，因此明确标记 `database=not_required`。Compose healthcheck 已接入该端点，Backend 启动条件同步升级为 `service_healthy`。
- 2026-07-16：收敛 Python 与 Backend AI 调用日志，provider、Embedding、Web Search、stream、内部认证和 fallback 失败只记录稳定 errorCode、异常类型、requestId、provider/model 与 latency，不再记录异常正文、响应 body 或堆栈。新增日志捕获回归测试；AI Service 全量测试增至 171 条，36/36 eval 的进程输出不再包含 provider fixture 异常详情。

目标：避免 Python AI service、前端代理和 Java Backend 同时形成多套 AI 入口，确保 Backend Agent Harness 与未来 Python AI Runtime Harness 分工清晰。

工作项：

1. 盘点所有 `/ai-proxy/*` 前端调用，按能力分为：
   - 业务主权路径：Agent、家庭记忆、日记、成长观察、权限、写入、审计。
   - AI runtime 路径：prompt、LLM、embedding、rerank、eval、trace。
   - 非业务主权路径：纯媒体处理、文件分析、临时开发工具。
2. 业务主权路径必须迁入 Java Backend API，再由后端通过 `AIServiceClient` 调 Python AI service。
3. `AIServiceClient` 继续作为后端访问 Python AI service 的唯一正式客户端边界，不把 Python URL、timeout、fallback、requestId、metrics 分散到业务 Service。
4. Python AI Runtime Harness 只负责模型运行与工具调用建议；真实工具执行、权限、确认、审计、事务、幂等由 Backend Agent Harness 承担。
5. 为暂时保留的 `/ai-proxy/*` 能力补充边界说明，明确是否允许访问家庭数据、是否需要鉴权、是否需要迁移到后端。
6. 新增或调整测试，至少覆盖 Agent / 家庭数据能力不能绕过 Backend Harness 的关键入口。

验收标准：

- [已完成] Agent、Memory、Diary、Growth 等家庭业务 AI 能力没有新增前端直连 Python AI service 的路径。
- [已完成] 现有 `/ai-proxy/*` 路径有清单、分类和迁移结论。
- [已完成] 新增 Python AI service 能力时，计划中能明确判断它属于 AI runtime 还是后端业务工具执行。
- [已完成] Backend Harness 与 Python AI Runtime Harness 的职责边界在文档和代码入口上保持一致。

---

### Phase 2：写入工具与确认门

状态：核心闭环已完成。已完成确认状态、确认策略、持久化确认记录、确认审批/拒绝 API 契约、三个写入工具声明、approve 后的后端幂等执行闭环，以及前端保存按钮接入统一 confirmation contract；后续只做回归补强和与 Skill Runtime 的衔接。

目标：把写日记、写家庭记忆、写成长观察纳入统一工具执行器。

最近进度：

- 2026-06-30：新增 `AgentConfirmationStatus`，先定义 `NOT_REQUIRED`、`REQUIRED`、`APPROVED`、`REJECTED`、`EXPIRED` 五种确认状态。
- 2026-06-30：新增 `AgentConfirmationPolicy`，将确认判断从 `AgentToolExecutor` 拆为独立 Spring 协作类，executor 继续只负责编排流程。
- 2026-06-30：`AgentToolExecutor` 已通过 confirmation policy 返回结构化 `CONFIRMATION_REQUIRED`，不会直接执行需要确认的工具。
- 2026-06-30：新增 `AgentConfirmationPolicyTest`，并补充 executor confirmation-required 测试。
- 2026-06-30：新增 `agent_tool_confirmations` 表、`AgentToolConfirmationRecord`、Repository 与 `AgentToolConfirmationService`，确认记录包含 `idempotencyKey`、状态、过期时间和脱敏输入摘要。
- 2026-06-30：抽出 `AgentToolInputSummarizer`，审计记录与确认展示共用脱敏摘要逻辑，避免在日志、审计摘要和前端确认列表中暴露原始家庭隐私内容。
- 2026-06-30：executor 在确认策略返回 `REQUIRED` 时会创建 pending confirmation，并在结构化结果中返回 `confirmationId`，仍不会执行写入工具。
- 2026-06-30：新增 `create_diary_entry` 工具、强类型 `CreateDiaryEntryInput` / `CreateDiaryEntryOutput`，并通过 `AgentDiaryEntryFacade` 隔离 diary 模块 Service；该写入工具默认 `REQUIRED`，经 executor 调用时只生成确认记录。
- 2026-06-30：新增 `AgentConfirmationDecision` 和确认决策流转，支持 approve / reject / expired / duplicate terminal confirmation 幂等处理，错误用户不能处理他人的 confirmation。
- 2026-06-30：新增 `/api/agent/tool-confirmations/{confirmationId}/decision`，并同步前端 `agentApi.decideToolConfirmation` 与 `AgentToolConfirmation` / `AgentConfirmationDecision` / `AgentConfirmationStatus` 类型。
- 2026-07-01：扩展 confirmation 记录，保存仅供后端确认执行使用的受控 typed payload、最小 run context、执行状态和 `executedAt`；确认 approve 后通过 `AgentToolConfirmationDecisionService -> AgentToolExecutor.executeConfirmed` 重新走权限、校验、审计和工具执行。
- 2026-07-01：`agent_tool_calls` 增加 `confirmation_id`，approve 后的真实写入 tool call 可追溯到 confirmation；重复 terminal confirmation 不会再次执行写入工具。
- 2026-07-01：确认决策 API 响应扩展为 `AgentToolConfirmationDecisionResult`，同时返回 confirmation 与可选 `toolResult`，前端类型和 `agentApi.decideToolConfirmation` 已同步。
- 2026-07-01：新增 `create_family_memory` 写入工具、强类型 `CreateFamilyMemoryInput` / `CreateFamilyMemoryOutput`，并通过 `AgentFamilyMemoryFacade` 隔离 memory 模块 Service；该工具默认进入统一确认门，approve 后才执行真实写入。
- 2026-07-01：新增 `create_growth_guard_record` 写入工具、强类型 `CreateGrowthGuardRecordInput` / `CreateGrowthGuardRecordOutput`，并通过 `AgentGrowthGuardRecordFacade` 隔离 growth 模块 Service；工具层要求 `targetUserId` 必填，真实 care authorization 继续由 growth 模块内规则裁决。
- 2026-07-04：收紧前端显式保存命令链路。用户说“帮我保存 / 记一下 / 留作记录”等短命令时，前端会以上一段非 system 对话作为保存规划上下文，不再把保存命令本身当成待保存内容；保存规划 prompt 同步增加防臆造约束，要求 content 只能来自用户原话和最近对话中已经出现的信息。
- 2026-07-04：优化手动保存草稿体验。记忆/日记编辑器将标题输入与正文拆开，AI 草稿标题不再被拼回正文首行，降低后续保存内容被标题重复污染的风险。
- 2026-07-04：前端 Agent 保存按钮接入统一 confirmation contract。新增 Backend `/api/agent/save-memory-tool` 专用入口，将前端保存规划结果映射到 `create_diary_entry`、`create_family_memory`、`create_growth_guard_record` 强类型工具 input，经 `AgentToolExecutor` 生成 `CONFIRMATION_REQUIRED`；前端保存反馈可展示确认/取消按钮，并通过 `agentApi.decideToolConfirmation` 完成 approve / reject，SkillRun 同步流转为 `PLANNED`、`SUCCEEDED`、`CANCELED` 或 `FAILED`。
- 2026-07-04：保存确认链路补齐来源 metadata 保留。`/api/agent/save-memory-tool` 继续作为专用入口，前端从 save plan 与 agent mode 构造强类型 `AgentSaveMemoryMetadata`，后端写入工具 input 保留该 DTO，并只在调用 Diary / Memory / Growth Facade 边界转换为现有 metadata map，保留旧写入路径的 source、relationSource、target、scenario、followUpStatus 与计划字段。

工作项：

1. [已完成] 定义 `AgentConfirmationPolicy`。
2. [已完成] 定义确认状态：
   - `NOT_REQUIRED`
   - `REQUIRED`
   - `APPROVED`
   - `REJECTED`
   - `EXPIRED`
3. [已完成] 新增 `AgentToolConfirmation` DTO 或表。
4. 接入写入工具：
   - [已接入确认门与 approve 执行闭环] `create_diary_entry`
   - [已接入确认门与 approve 执行闭环] `create_family_memory`
   - [已接入确认门与 approve 执行闭环] `create_growth_guard_record`
5. [已完成] 前端保存确认弹窗改为消费统一 confirmation contract。
6. 现有 save-plan 可以先作为上游决策，不强行迁移 LLM prompt。

验收标准：

- [已完成] Agent 不能直接执行写入工具，除非确认策略允许。
- [已完成] 用户拒绝确认时不写库。
- [已完成] 用户重复确认不会重复写入。
- [已完成] 每次写入工具记录 idempotency key。
- [已完成] 写入来源能追溯到 tool call record。

---

### Phase 3：Skill Runtime Harness

状态：核心闭环已完成。`save_memory` 已接入版本化 manifest、runtime registry、统一 executor、use case、强类型 SkillRun 和结构化失败语义；后续 trace 与 eval 分别进入 Phase 4 / Phase 5。

最近进度：

- 2026-07-14：新增 `SkillManifest` 与 `SkillExecutor`，为 skill 声明版本、输入/输出 schema、读写层、确认要求、隐私级别和统一超时预算。
- 2026-07-14：将 `/ai/memory/save-plan` 的价值预审、脱敏、LLM 规划、结果清洗和失败降级迁入 `SaveMemoryPlanUseCase`；路由只保留 API 适配，仍返回 `{ success, data }`。
- 2026-07-14：`save_memory` registry 条目开始复用 manifest，避免 registry 与执行实现继续分叉。
- 2026-07-14：抽出 `SaveMemoryPromptRenderer` 与 `SaveMemoryOutputParser`，分别负责 AI-bound prompt 脱敏渲染和确定性价值清洗；补充隐私脱敏、提示词注入拒绝与原有 save-plan 回归测试。
- 2026-07-14：将后端 `SkillRunService` 收敛为稳定门面，新增 `SkillRunCommandService`、`SkillRunQueryService`、`SkillRunLifecycleService` 与输入策略；状态值收敛到 `SkillRunStatus`，跨模块成员校验经 `FamilyMembershipFacade` 隔离。
- 2026-07-14：将 `SkillRun.metadata` 与 `usedSources` 收敛为 `SkillRunMetadata` / `SkillRunSourceRef`，并使用专用 typed JSONB handler；旧未知字段读取到显式 `extra`，前端请求和响应类型同步收敛。
- 2026-07-14：新增 Backend `/api/agent/save-memory-plan`，通过 `AIServiceClient -> SaveMemoryPlanClient` 调用 Python AI，并自动记录 save-plan 的 SkillRun 状态；前端移除手工 SkillRun 创建和 `/ai-proxy/memory/save-plan` 直连。
- 2026-07-14：修正 save-plan provider failure 与非法模型输出的错误语义，AI service 返回 `success=false` 和稳定 `errorCode`；Backend 不再把不可用降级计划记为正常低价值结果，并将原始结构化失败码写入 `SkillRun.metadata.executionErrorCode`。
- 2026-07-14：新增 `SkillRuntimeRegistry` / `SkillRuntime`，将 `save_memory` manifest 与统一 executor 稳定关联；`memory.py` 只装配已注册 runtime 与 use case，不再临时拼装执行边界。

目标：把 skill 从静态 registry / 固定 endpoint 升级为可执行、可审计、可版本化、可评测的 runtime。

优先切片：

```text
/ai/memory/save-plan
  -> SaveMemoryPlanUseCase
    -> SaveMemorySkillExecutor
      -> PromptRenderer
      -> LLMClient
      -> OutputParser
      -> TraceRecorder
      -> EvalCaseBinding
```

工作项：

1. [已完成] 保留原 endpoint 和 `{ success, data }` 响应结构。
2. [已完成] 新增 `SkillManifest`：name、version、description、inputSchema、outputSchema、reads、writes、requiresConfirmation、timeoutSeconds、privacyLevel。
3. [已完成] 新增 `SkillExecutor`。
4. [已完成] 新增 `SaveMemoryPlanUseCase`。
5. [已完成] 抽出 prompt 渲染和输出解析。
6. [已完成] 后端拆分 `SkillRunService`：
   - [已完成] `SkillRunCommandService`
   - [已完成] `SkillRunQueryService`
   - [已完成] `SkillRunLifecycleService`
7. [已完成] 收敛 `SkillRun.metadata`：
   - [已完成] 新增强类型 `SkillRunMetadata`。
   - [已完成] 动态兼容字段放入 `extra`。

验收标准：

- [已完成] 外部接口完全兼容。
- [已完成] `memory.py` 不再直接承载 save-plan 的完整编排。
- [已完成] skill runtime registry 可关联 manifest 和 executor。
- [已完成] 当前 `save_memory` skill 每次后端业务执行都有强类型 SkillRun record。
- [已完成] 需要确认的 skill 不会绕过确认直接写入最终数据。

---

### Phase 4：Agent Run、Trace 与 Replay

状态：核心闭环已完成。Agent 工具执行链路、chat stream 父 run、runId 三端透传、skill / LLM / Web Search / Memory Recall / Embedding span、内部 trace 查询和隐私安全 replay artifact 已接齐；后续 eval 与治理进入 Phase 5。

最近进度：

- 2026-07-15：新增 `agent_runs` 表、`AgentRunRecord`、`AgentRunStatus` 与 `AgentRunLifecycleService`；工具执行自动创建或续接 run，并在成功、失败、等待确认、拒绝和过期时记录明确状态。
- 2026-07-15：`AgentRunContext` 增加兼容式 `runId`，`agent_tool_calls` 与 `agent_tool_confirmations` 同步关联 run；确认 approve 继续原 run，reject / expired 进入明确终态。
- 2026-07-15：新增 `AgentRunQueryService` 与 `AgentRunTraceQueryService`，支持内部按 runId、requestId、sessionId 查询，并按 runId 组装只包含脱敏 input summary 的工具轨迹；暂未开放外部 Controller。
- 2026-07-15：Chat stream 接入父 run 生命周期。Backend 在授权召回前创建 run，召回工具作为子调用只记录 tool trace、不提前结束父 run；stream `done`、结构化 `error`、EOF、transport failure 和请求拒绝分别收敛到明确终态。
- 2026-07-15：`runId` 通过 `X-Agent-Run-Id` 从 Backend 透传到 Python AI service，并同步进入 metadata / content / done / error SSE event；前端消息 metadata 增加强类型 `requestId` / `runId`，不改变已有事件判定方式。
- 2026-07-15：新增 `agent_run_steps`、`AgentTraceRecorder` 与强类型 span descriptor，字段覆盖 operation、stepType、promptVersion、skillVersion、latencyMs、errorCode、degraded 和 privacyCategories；step 不保存 prompt、家庭原文或模型输出。
- 2026-07-15：save-memory planning 接入 AgentRun + SKILL span。SkillRun metadata 同步记录 `agentRunId`、`skillVersion=1.0.0` 与 `promptVersion=memory.save_plan.v1`，成功和结构化失败均可从 SkillRun 追溯到 Agent run/step。
- 2026-07-15：Chat stream 接入隐私安全的 LLM / Web Search observation。Python AI service 仅在终端 `done` / `error` event 附带强类型 `traceObservations`，Backend tracker 提取后写入 `agent_run_steps`；前端保持原终端事件处理，不新增 trace metadata 回调。
- 2026-07-15：每次 LLM 主模型与 fallback 尝试分别记录 provider、model、latency、success、errorCode 和 degraded；即使所有 provider 均失败，也会先刷新 observation 再输出结构化 stream error。
- 2026-07-15：Web Search 对 provider 失败、功能禁用、隐私 query 拒绝和 metadata timeout 保持明确失败/降级语义；trace 不保存 prompt、原始 search query、家庭内容或模型输出，无效 observation 会在 AI service / Backend 边界被拒绝。
- 2026-07-15：授权家庭记忆召回接入独立 `MEMORY_RECALL` span，operation 固定为 `memory.recall.authorized`；正常空结果记成功，底层召回异常继续向聊天链路返回空上下文，但 span 记录 `MEMORY_RECALL_FAILED`、`degraded=true`。Trace 存储失败采用 best-effort，不阻断召回主路径，异常日志不输出底层错误详情。
- 2026-07-15：授权召回的 query embedding 接入独立 `EMBEDDING` span，operation 固定为 `embedding.recall_query`，记录 provider、model、latency、success、errorCode 和 degraded，不保存 query 或向量。Provider degraded、传输失败、空向量、维度不符、非有限值均进入明确失败 span，并继续文本召回 fallback。
- 2026-07-15：Embedding 外部调用与响应校验从已偏大的 `AuthorizedMemoryRecallRankingService` 下沉到独立 Spring 协作类 `AuthorizedMemoryRecallEmbeddingService`；Ranking 仅消费已校验向量和强类型 observation，不继续承载 provider 逻辑。
- 2026-07-15：`V15__create_agent_runs.sql` 与 `V16__create_agent_run_steps.sql` 已在本地 PostgreSQL 16.14 实例通过 Flyway 正式执行，schema 从 v14 升级到 v16；迁移历史、34 个字段、索引、外键及事务内写入/回滚烟雾测试均已验证。
- 2026-07-15：新增内部只读 `AgentRunReplayService`，按时间合并 STEP / TOOL trajectory，并输出 run 状态、版本、错误、降级和延迟汇总；未开放外部 Controller。
- 2026-07-15：Replay artifact 不包含原始 requestId、familyId、viewerUserId、sessionId、subject、prompt、家庭内容、模型输出、搜索 query 或向量。requestId 仅生成 16 位十六进制哈希引用，工具输入摘要仅允许 `inputType=[A-Za-z0-9_.$]{1,120}`，旧数据或不安全摘要统一输出 `inputType=REDACTED`。

目标：从 requestId 追踪升级为 Agent run 级别的结构化 trace，并沉淀可脱敏回放的 run artifact。

工作项：

1. [已完成] 新增 `agent_runs` 表。
2. [已完成] 新增 `agent_run_steps` 表或轻量事件表。
3. [已完成] `AgentRunContext` 增加 runId。
4. [已完成] 每次工具调用关联 runId。
5. [已完成] 新增或完善 `TraceRecorder`：当前已包含 run lifecycle、typed step recorder、tool audit、LLM / Web Search observation、trace query 与 replay artifact。
6. [已完成] 定义 span 字段：
   - requestId。
   - runId。
   - spanId。
   - parentSpanId。
   - operation。
   - provider。
   - model。
   - promptVersion。
   - skillVersion。
   - latencyMs。
   - success。
   - errorCode。
   - degraded。
   - privacyCategories。
7. [已完成] 接入关键链路：
   - [run 级别已完成] chat stream。
   - [已完成] LLM call。
   - [已完成] Web Search。
   - [已完成] Memory Recall。
   - [save-memory 已完成] Skill execution。
   - [已完成] Embedding。
8. [已完成] 新增 run artifact / replay 数据结构：
   - [已完成] 不保存敏感原文。
   - [已完成] 保存脱敏输入摘要、版本、工具轨迹和结果摘要。
   - [已完成] 保存 eval 可读取的 trajectory summary。

验收标准：

- [已完成] 可以按 sessionId / requestId / runId 查询一次 Agent 调用的工具轨迹。
- 可以解释某条记忆、日记或成长观察由哪个 Agent 工具写入。
- [已完成] 一次聊天可以看到 chat 父 run、授权召回 tool call、独立 Memory Recall / Embedding span、LLM 尝试、Web Search span 和脱敏 replay trajectory。
- [已完成] eval 可读取 trace / run artifact 检查 trajectory。

---

### Phase 5：Evaluation / Governance Harness

状态：核心闭环已完成。已具备 36 条 AI golden cases、3 条 Backend trajectory fixtures、隐私安全 JSON 报告、核心 prompt/schema manifest 版本、P0 safety/privacy 与 contract 100% 门禁，以及 baseline/candidate 对比；后续继续扩大真实模型抽样和更多业务工具覆盖。

最近进度：

- 2026-07-15：新增 `ai-service/evals/`，按强类型 case、evaluator、runner、report model 拆分职责；可通过 `python -m evals` 一键运行，也可使用 `--output` 生成 JSON 报告。
- 2026-07-15：首批 `familyagent.core.v1` 套件包含 36 条 golden cases，覆盖 history system/developer role 注入、prompt 提取、身份劫持、正常安全复盘、正常风格请求、垃圾/暗语输入、家庭价值信号、Web Search 隐私、save-plan、chat stream、embedding、organize-draft 和 persona material 契约。
- 2026-07-15：Save-memory eval 使用 deterministic mock LLM，覆盖低价值输入不调用模型、模糊家庭信号进入模型、高价值学习经验经 post-check 保留、provider failure 和非法输出保持结构化失败。
- 2026-07-15：`eval.report.v1` 仅记录 case ID、保护资产、预期/实际决策、稳定 errorCode 和延迟，不记录 prompt、家庭内容、搜索 query、模型输出或异常详情；当前 36/36 通过，安全/隐私失败数为 0。
- 2026-07-15：`save_memory` manifest 新增强类型 `prompt_version=memory.save_plan.v1` 与 `schema_version=save_tool_plan.schema.v1`；`eval.report.v1` 同步记录 skill、prompt、schema artifact 版本，为后续模型或提示词对比提供稳定基线。
- 2026-07-15：Chat `memory_context` 现在仅信任带内部服务身份的 Backend 调用；普通用户令牌提交的伪造上下文直接忽略，内部上下文仍经过 PII 脱敏与 prompt/role 注入检测，且日志不输出上下文原文。
- 2026-07-15：新增 stream provider failure / terminal done、embedding provider failure / local dimensions、organize-draft strict schema / enum post-check / heritage form cleanup 等跨链路 fixture eval。
- 2026-07-15：新增统一 artifact 版本常量，family chat LLM trace 写入 `promptVersion=family_chat.system.v1`；Eval 报告同步记录 `memory.organize_draft.v1` 与 `organized_draft.schema.v1`。
- 2026-07-15：新增内部 `AgentTrajectoryEvalService` 与 `trajectory.eval.report.v1`，按 run 终态、错误码和精确事件顺序评估脱敏 replay artifact；报告只包含 case ID、事件数量、稳定错误码和 trajectory pass rate，不复制 run、inputType 或工具详情。
- 2026-07-15：新增工具权限拒绝、用户拒绝确认、重复确认不再次执行 3 条 Backend trajectory fixtures；`agent.trajectory.core.v1` 当前 3/3 通过，trajectory pass rate 为 100%。
- 2026-07-15：`organize_draft` 与 `persona_material_draft` 迁入强类型 `SkillManifest`，registry 不再分别手写 reads、writes、确认、prompt 和 schema 版本字段；persona 新增 strict schema 与输出边界 fixture。
- 2026-07-15：`eval.report.v1` 新增 fail-closed `P0_SAFETY_PRIVACY` 与 `CONTRACT` gates，两类案例均要求 100% 通过；缺少对应案例时也不会误判为可发布。主观质量案例仍进入总体 pass rate，不被扩大成硬安全策略。
- 2026-07-15：新增隐私安全 `eval.comparison.v1` 与 `EvalReportComparator`，比较 baseline/candidate 的 case 通过状态、gate 状态和 artifact 版本，不读取或保存 prompt、家庭原文或模型输出。
- 2026-07-15：`python -m evals --baseline <report>` 支持输出 `NO_CHANGE`、`IMPROVED`、`REGRESSION`、`INCOMPARABLE` 结论；case/gate 回退或案例集合漂移返回非零退出码。本地 baseline 自比较验证为 36 条 unchanged、0 regression。
- 2026-07-15：`organize_draft` 与 `persona_material_draft` 已接入统一 `SkillRuntime`、独立 prompt renderer、output parser 和 use case；路由不再直接编排 provider、JSON 解析和失败映射。
- 2026-07-15：两个 draft endpoint 的 provider failure、timeout、非法 JSON 统一返回 `success=false`、`data=null` 与稳定 `AI_PROVIDER_ERROR` / `AI_TIMEOUT` / `AI_INVALID_RESPONSE`，响应和日志不输出底层异常详情；前端按 errorCode 映射安全提示。
- 2026-07-15：`organize_draft` 与 `persona_material_draft` 已迁入 Backend `/api/agent/organize-draft`、`/api/agent/persona-material-draft`；Backend 通过独立 `DraftGenerationClient` 和内部服务身份调 Python SkillRuntime，先校验家族成员身份，并记录 SkillRun、AgentRun、trace、prompt/schema/skill 版本。Python save-plan 与两个 draft POST 路由新增 internal-only gate，普通用户令牌不能绕过 Backend 直接调用；前端已移除两个 `/ai-proxy/memory/*draft` allowlist 入口。

目标：让 AI 变更可比较、可回滚、可评估，并逐步建立模型、prompt、schema、skill 上线门禁。

工作项：

1. [已完成] 新增 `ai-service/evals/`。
2. [已完成首批 36 条] 建立第一批 golden cases：
   - [已完成] history system role 注入。
   - [已完成] 客户端伪造 memory_context。
   - [已完成] Web Search 隐私 query。
   - [已完成] save-plan 垃圾输入。
   - [已完成] organize-draft schema 输出。
   - [已完成] stream provider failure。
   - [已完成] embedding provider failure。
   - [已完成] tool permission denied。
   - [已完成] confirmation rejected。
   - [已完成] duplicated confirmation。
3. [已完成] eval runner 支持 mock LLM 和 JSON 报告。
4. [已完成] 为核心 prompt 增加版本：
   - [已完成] `family_chat.system.v1`
   - [已完成] `memory.save_plan.v1`
   - [已完成] `memory.organize_draft.v1`
   - [已完成] `persona.material_draft.v1`
5. [已完成] 为核心 schema 增加版本：
   - [已完成] `save_tool_plan.schema.v1`
   - [已完成] `organized_draft.schema.v1`
   - [已完成] `persona_material_draft.schema.v1`
6. [核心 skill 已完成] skill manifest 引用 prompt/schema 版本。
7. [family chat / save-memory / organize-draft / persona eval 已完成基础接入] trace、SkillRun、eval 报告记录版本。
8. [已完成基础报告] 增加 regression report：
   - [已完成] eval pass rate。
   - [已完成] trajectory pass rate。
   - [已完成] safety / privacy failure count。
   - [已完成] model / prompt 对比结论。
9. [已完成基础门禁] 定义上线门禁：
   - [已完成] P0 safety / privacy case 必须 100% 通过。
   - [已完成] contract case 必须 100% 通过。
   - [已完成规则定义] 真实模型抽样 eval 可先作为人工审批依据，不立即阻断常规开发。

验收标准：

- [已完成] 至少 20 条 AI golden cases。
- [已完成] 本地可一键运行低成本 eval。
- [save-memory 与 Agent tool trajectory 已完成] 关键工具可以用 fixture 回放。
- [已完成基础闭环] 权限、确认、失败、幂等都有测试，并已纳入首批 trajectory eval。
- 任何一次 AI 输出都能追溯 prompt/schema/skill 版本。
- [已完成] 模型升级前后能比较核心场景结果。

---

### Phase 6：重型 Harness 演进

目标：当轻型 harness 无法承载真实业务复杂度时，再引入重型运行时能力。

只有出现以下情况，才考虑重型 harness 或 workflow engine：

1. Agent 任务需要跨天运行。
2. 任务需要服务重启后自动恢复。
3. 任务步骤超过 5-8 个并且依赖复杂条件分支。
4. 多个 worker 并发处理同一类 Agent 任务。
5. 需要暂停、恢复、取消、人工审批、定时继续。
6. 需要跨多个外部系统写入。
7. 需要 outbox / inbox、dead letter queue、长任务恢复队列。

到那时再考虑：

- 独立 workflow engine。
- 完整 AgentRun 状态机。
- 持久化 step executor。
- outbox / inbox。
- dead letter queue。
- 长任务恢复队列。
- 多 worker 调度。

当前阶段不直接做这些。

---

## 6. 边界与不做事项

除非单独立项，本计划不做以下事项：

1. 不拆分多个 AI 微服务。
2. 不一次性重写所有 prompt。
3. 不让模型直接写数据库或绕过 backend 权限。
4. 不扩大 Web Search 到未治理的隐私场景。
5. 不立即引入大型工作流引擎替代当前链路。
6. 不破坏现有 URL、SSE event、errorCode、Embedding 状态语义。
7. 不继续扩大裸 `Map` / `Object metadata` 作为主契约。
8. 不把所有业务 Service 一次性改成工具。
9. 不做大爆炸式重写。

---

## 7. 优先级

P0：

- Phase 1 轻型 Agent Harness 骨架。
- `recall_family_memory` 只读工具。
- `agent_tool_calls` 最小审计表。
- 工具权限拒绝测试。
- 工具失败结构化测试。

P1：

- 写入工具与确认门。
- Agent run 最小记录。
- SkillManifest。
- SkillExecutor。
- SkillRunLifecycleService。

P2：

- TraceRecorder。
- Eval runner。
- Golden cases。
- trajectory replay。
- Memory Recall quality eval。

P3：

- MCP 适配评估。
- 多 agent handoff。
- 更复杂的长期任务调度。
- 独立 workflow engine 评估。

---

## 8. 成功标准

满足以下条件，可以认为 FamilyAgent AI 部分已经从“安全增强 AI 网关”升级到“AI Architecture Harness 雏形”：

1. 核心 API、SSE event、errorCode、Embedding 状态、Web Search 隐私边界都有 contract harness 保护。
2. Agent 工具调用有统一入口。
3. 工具权限检查不可绕过。
4. 写入工具默认有确认策略。
5. 工具调用可审计、可查询。
6. 工具失败有结构化错误。
7. 新增 Agent 工具有稳定 descriptor、DTO 和测试。
8. 每个核心 skill 都有 manifest、executor、input schema、output schema、版本和 eval binding。
9. 每次 skill 执行都有强类型 run record、状态迁移、失败原因、requestId 和 runId。
10. Agent / tool trajectory 可被 eval 检查，而不只是最终输出可测。
11. trace 能串起一次 Agent run 下的 LLM、tool、retrieval、skill 调用，并形成可脱敏 replay artifact。
12. prompt/schema/skill/model 变更有版本、有 eval、有 regression report、有回滚依据。
13. 新增 AI 能力默认复用 harness，而不是继续新增散落 endpoint + helper。

---

## 9. 建议首批落地文件

Backend：

- `backend/src/main/java/com/familyagent/module/agent/harness/AgentTool.java`
- `backend/src/main/java/com/familyagent/module/agent/harness/AgentToolDescriptor.java`
- `backend/src/main/java/com/familyagent/module/agent/harness/AgentToolRegistry.java`
- `backend/src/main/java/com/familyagent/module/agent/harness/AgentToolExecutor.java`
- `backend/src/main/java/com/familyagent/module/agent/harness/AgentToolPermissionGate.java`
- `backend/src/main/java/com/familyagent/module/agent/harness/AgentRunContext.java`
- `backend/src/main/java/com/familyagent/module/agent/harness/dto/AgentToolCallRequest.java`
- `backend/src/main/java/com/familyagent/module/agent/harness/dto/AgentToolCallResult.java`
- `backend/src/main/java/com/familyagent/module/agent/harness/tool/RecallFamilyMemoryTool.java`
- `backend/src/main/resources/db/migration/*__create_agent_tool_calls.sql`

AI service：

- `ai-service/app/runtime/skill_manifest.py`
- `ai-service/app/runtime/skill_executor.py`
- `ai-service/app/runtime/agent_runner.py`
- `ai-service/app/runtime/tool_registry.py`
- `ai-service/app/runtime/trace_recorder.py`
- `ai-service/app/runtime/run_artifact.py`
- `ai-service/app/use_cases/save_memory_plan.py`
- `ai-service/evals/runner.py`
- `ai-service/evals/cases/*.json`
- `ai-service/evals/report.py`

Frontend：

- 统一 confirmation contract 的展示与提交。
- Agent run / tool call 最小来源展示。

注意：以上文件是方向建议，不要求一次性全部创建。每次实现必须遵守仓库规则：小步改动、强类型契约、失败路径明确、不要继续扩大大 Service 和匿名 Map。
