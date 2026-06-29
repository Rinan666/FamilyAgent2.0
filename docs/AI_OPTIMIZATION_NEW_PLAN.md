# FamilyAgent AI Harness 统一升级计划

> 更新日期：2026-06-29
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

状态：当前推荐立即执行。

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

- 工具不能绕过 registry 执行。
- 工具执行前必经 permission gate。
- 每次工具调用都有审计记录。
- 只读工具失败返回结构化错误，不伪装成普通 assistant 文本。
- 不破坏当前可信上下文与授权召回边界。

---

### Phase 2：写入工具与确认门

目标：把写日记、写家庭记忆、写成长观察纳入统一工具执行器。

工作项：

1. 定义 `AgentConfirmationPolicy`。
2. 定义确认状态：
   - `NOT_REQUIRED`
   - `REQUIRED`
   - `APPROVED`
   - `REJECTED`
   - `EXPIRED`
3. 新增 `AgentToolConfirmation` DTO 或表。
4. 接入写入工具：
   - `create_diary_entry`
   - `create_family_memory`
   - `create_growth_guard_record`
5. 前端保存确认弹窗改为消费统一 confirmation contract。
6. 现有 save-plan 可以先作为上游决策，不强行迁移 LLM prompt。

验收标准：

- Agent 不能直接执行写入工具，除非确认策略允许。
- 用户拒绝确认时不写库。
- 用户重复确认不会重复写入。
- 每次写入工具记录 idempotency key。
- 写入来源能追溯到 tool call record。

---

### Phase 3：Skill Runtime Harness

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

1. 保留原 endpoint 和 `{ success, data }` 响应结构。
2. 新增 `SkillManifest`：name、version、description、inputSchema、outputSchema、reads、writes、requiresConfirmation、timeoutSeconds、privacyLevel。
3. 新增 `SkillExecutor`。
4. 新增 `SaveMemoryPlanUseCase`。
5. 抽出 prompt 渲染和输出解析。
6. 后端拆分 `SkillRunService`：
   - `SkillRunCommandService`
   - `SkillRunQueryService`
   - `SkillRunLifecycleService`
7. 收敛 `SkillRun.metadata`：
   - 新增强类型 `SkillRunMetadata`。
   - 动态兼容字段放入 `extra`。

验收标准：

- 外部接口完全兼容。
- `memory.py` 不再直接承载 save-plan 的完整编排。
- skill registry 可关联 manifest 和 executor。
- 每次 skill 执行都有强类型 run record。
- 需要确认的 skill 不会绕过确认直接写入最终数据。

---

### Phase 4：Agent Run、Trace 与 Replay

目标：从 requestId 追踪升级为 Agent run 级别的结构化 trace，并沉淀可脱敏回放的 run artifact。

工作项：

1. 新增 `agent_runs` 表。
2. 新增 `agent_run_steps` 表或轻量事件表。
3. `AgentRunContext` 增加 runId。
4. 每次工具调用关联 runId。
5. 新增或完善 `TraceRecorder`。
6. 定义 span 字段：
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
7. 接入关键链路：
   - chat stream。
   - LLM call。
   - Web Search。
   - Memory Recall。
   - Skill execution。
   - Embedding。
8. 新增 run artifact / replay 数据结构：
   - 不保存敏感原文。
   - 保存脱敏输入摘要、版本、工具轨迹和结果摘要。
   - 保存 eval 可读取的 trajectory summary。

验收标准：

- 可以按 sessionId / requestId / runId 查询一次 Agent 调用的工具轨迹。
- 可以解释某条记忆、日记或成长观察由哪个 Agent 工具写入。
- 一次聊天可以看到最小 run tree。
- eval 可读取 trace / run artifact 检查 trajectory。

---

### Phase 5：Evaluation / Governance Harness

目标：让 AI 变更可比较、可回滚、可评估，并逐步建立模型、prompt、schema、skill 上线门禁。

工作项：

1. 新增 `ai-service/evals/`。
2. 建立第一批 golden cases：
   - history system role 注入。
   - 客户端伪造 memory_context。
   - Web Search 隐私 query。
   - save-plan 垃圾输入。
   - organize-draft schema 输出。
   - stream provider failure。
   - embedding provider failure。
   - tool permission denied。
   - confirmation rejected。
   - duplicated confirmation。
3. eval runner 支持 mock LLM 和 JSON 报告。
4. 为核心 prompt 增加版本：
   - `family_chat.system.v1`
   - `memory.save_plan.v1`
   - `memory.organize_draft.v1`
   - `persona.material_draft.v1`
5. 为核心 schema 增加版本：
   - `save_tool_plan.schema.v1`
   - `organized_draft.schema.v1`
   - `persona_material_draft.schema.v1`
6. skill manifest 引用 prompt/schema 版本。
7. trace、SkillRun、eval 报告记录版本。
8. 增加 regression report：
   - eval pass rate。
   - trajectory pass rate。
   - safety / privacy failure count。
   - model / prompt 对比结论。
9. 定义上线门禁：
   - P0 safety / privacy case 必须 100% 通过。
   - contract case 必须 100% 通过。
   - 真实模型抽样 eval 可先作为人工审批依据，不立即阻断常规开发。

验收标准：

- 至少 20 条 AI golden cases。
- 本地可一键运行低成本 eval。
- 关键工具可以用 fixture 回放。
- 权限、确认、失败、幂等都有测试。
- 任何一次 AI 输出都能追溯 prompt/schema/skill 版本。
- 模型升级前后能比较核心场景结果。

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
