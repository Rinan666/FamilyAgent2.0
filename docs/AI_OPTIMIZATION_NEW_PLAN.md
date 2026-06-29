# FamilyAgent AI Harness 架构升级计划

> 更新日期：2026-06-29
> 来源：本文件由原 `FamilyAgent AI 部分优化计划` 升级而来。原计划中的安全边界、失败语义、Embedding 质量、跨服务契约、Web Search 隐私等核心治理项已作为后续拓展的工程基线。
> 范围：`ai-service/`、`backend/src/main/java/com/familyagent/infra/ai/`、后端 `agent` / `memory` / `mirror` / `session` / `skillrun` 中调用 AI 的链路。
> 目标：在保护现有 API、SSE、Embedding、隐私和安全契约的基础上，把当前“安全增强版 AI 网关 + 固定业务端点”升级为世界主流形态的 AI Architecture Harness。Harness 是后续多 Agent、Skill Runtime、Tool Calling、Trace / Replay、Eval / Regression、Prompt / Model Governance 的共同底盘。

---

## 0. 终极目标

本计划的终极目标是把项目 AI 部分升级成世界主流的 AI Architecture Harness，而不是只做局部优化、只补几条测试、或只换一个 agent 框架。

Harness 在本项目中的定位是 AI 基础设施中枢：多 Agent、Skill Runtime、Tool Calling、RAG / Memory Recall、Web Search、Prompt Versioning、Model Upgrade、Safety / Privacy Guard、Trace / Replay、Evaluation / Regression 都必须接入 Harness，而不是各自实现一套散落流程。

目标中的 harness 是 AI 系统的运行与验证中枢：

1. Runtime Harness
   - 统一运行 agent、skill、tool、LLM、retrieval、embedding。
   - 所有核心 AI 能力都通过 runner / executor 进入系统，避免继续散落在 endpoint 和 helper 中。

2. Evaluation Harness
   - 通过 golden cases、mock LLM、真实模型抽样、trajectory eval 持续验证 AI 行为。
   - 不只验证最终输出，还验证中间过程、tool 选择、权限边界、隐私边界和失败语义。

3. Trace / Replay Harness
   - 每次 agent run 都能记录 runId、span、tool call、prompt/schema/skill 版本、失败原因。
   - 支持脱敏回放，用于定位问题、比较模型升级效果、复盘 AI 决策路径。

4. Contract Harness
   - 固定 API、SSE event、errorCode、Embedding 状态、Web Search 隐私边界。
   - 确保内部架构可以大胆重构，外部 backend / frontend 契约不漂移。

5. Governance Harness
   - 管理 skill manifest、权限、确认、状态迁移、审计、版本和回滚。
   - 保证 AI 不绕过 backend 权限，不直接写入高风险数据，不扩大隐私外发。

最终目标结构：

```text
API adapter
  -> Contract Harness
  -> Agent / Skill Runtime Harness
  -> Tool / LLM / Retrieval Gateways
  -> Trace / Replay Harness
  -> Evaluation Harness
```

达成后，新增 AI 能力的默认方式应从“新增 endpoint + helper + prompt 常量”转变为“新增 manifest + executor/tool + eval cases + trace span + contract tests”。

### 0.1 Harness 成熟度模型

本计划按 L1-L5 推进，避免只做到 runtime 雏形就误认为完成世界主流 AI Harness。

| 等级 | 名称 | 目标 | 完成信号 |
| --- | --- | --- | --- |
| L1 | Contract Harness | 保护 API、SSE、errorCode、Embedding 状态、隐私边界不漂移 | 内部重构时 backend / frontend 契约测试稳定通过 |
| L2 | Skill Runtime Harness | skill 通过 manifest、executor、lifecycle、eval 标准化运行 | 核心 skill 不再散落在 endpoint + helper 中 |
| L3 | Agent Tool Harness | agent 通过 runner 调用声明式 tools，并能评测 trajectory | Web Search、Memory Recall、Save Plan 等工具调用可追踪、可评测 |
| L4 | Trace Replay Harness | 每次 AI run 都形成可脱敏回放的 run artifact | eval 能读取 trace 判断过程是否正确 |
| L5 | Continuous Governance Harness | 模型、prompt、schema、skill 变更有分数、阈值、回归报告和上线门禁 | AI 变更默认经过 eval gate / regression report |

最终目标不是只完成 L2-L3，而是让 FamilyAgent AI 部分至少达到 L4，并为 L5 连续治理留出清晰接口。

---

## 1. 当前定位

FamilyAgent AI service 已经不是简单封装 LLM API。当前基础能力包括：

- Python `ai-service` 负责聊天、记忆整理、Embedding、Web Search 等模型能力。
- Java backend 通过 `AIServiceClient` 调用 AI 服务，并在 `agent`、`memory`、`mirror` 等模块使用 AI 结果。
- 已有 FastAPI 路由、Pydantic 契约、Prompt 模板、安全限制、限流、Resilience4j、requestId 和基础测试。
- 已完成一轮关键安全治理：history role 白名单、服务端可信上下文、生产 auth fail-closed。
- 已完成 Embedding 质量治理：provider 失败不再伪成功，degraded / 维度异常 / 非有限值不会写入 READY 索引。
- 已完成 stream 失败语义治理：provider failure 不再伪装成普通 assistant 文本，SSE 有结构化 error / done / metadata event。
- 已完成部分强类型契约治理：embedding、memory extraction、chat stream payload 已逐步收敛 DTO / Pydantic。
- 已完成 Web Search 隐私治理基础：外部搜索前做 query rewrite / PII stripping，高隐私 query 默认跳过。

现在的主要问题已经从“安全止血”转为“Harness 架构升级”：

1. skill 仍是静态登记表和固定 endpoint，不是统一运行时。
2. Agent 链路仍是固定 pipeline，不是可声明、可插拔、可回放的工具编排。
3. 测试覆盖较多接口和安全规则，但 Evaluation Harness 还没有成为 AI 变更门禁。
4. requestId 和 metrics 已有基础，但缺少 agent / skill / tool / LLM / retrieval 的全链路 trace span。
5. prompt、schema、skill 定义缺少统一版本化，模型和 prompt 变更难以对比。
6. `SkillRunService` 更像记录器，不是 skill lifecycle runtime。
7. 部分 AI 用例仍在 router/helper 中直接拼 prompt、调用 LLM、解析 JSON，继续发展会变臃肿。

---

## 2. 核心原则

### 2.1 接口稳定，内部可以重构

AI service 与 backend / frontend 的硬连接主要是少数几个接口，因此可以在保护契约的前提下重构内部结构。

必须保护的外部契约：

- URL 不漂移，例如 `/ai/agent/chat/stream`、`/ai/memory/save-plan`、`/ai/embedding/embed`。
- request / response 字段不漂移，尤其是 `{ success, data }`、`degraded`、`errorCode`。
- SSE event 结构不漂移，继续稳定支持 `content`、`metadata`、`done`、`error`。
- `requestId` 继续从 frontend 到 backend 再到 AI service 透传。
- auth、限流、隐私脱敏、prompt injection 检测、Web Search 脱敏不能在重构中丢失。
- Embedding 仍必须保证 provider 失败、degraded、维度异常、非有限值不会写入 READY 索引。

需要特别保护的隐藏契约：

- stream EOF 如果没有收到 `done` 或 `error`，客户端应视为失败。
- provider 失败不能被包装成普通 assistant 文本。
- backend stream proxy 必须保留 AI service 的结构化 error event。
- `memory_context` 即使字段继续存在，也不能重新变成客户端可伪造的高可信上下文。
- Web Search 不能把家庭隐私原文发给外部服务，也不能在日志里落原始隐私 query。

### 2.2 先立护栏，再动内部

重构顺序：

1. 先补接口契约测试，固定 URL、字段、SSE event、errorCode 和失败语义。
2. 旧 endpoint 保留为 adapter，只把内部实现迁入 use case / skill executor / agent runner。
3. 一个接口一个接口迁移，优先选择风险较低且最像 skill 的 `/ai/memory/save-plan`。
4. 新增能力默认走新 runtime，不再继续堆进 router、helper 或大 client。
5. 新路径跑稳后，再逐步删除旧 helper 中的重复逻辑。

### 2.3 不做大爆炸式重写

本计划不是推翻现有系统：

- 不拆 AI 微服务。
- 不立即引入大型多 agent 框架。
- 不让模型直接拥有数据库写权限。
- 不破坏 backend 作为权限和持久化权威的边界。
- 不为了“更主流”牺牲当前已完成的安全和隐私治理成果。

---

## 3. 与主流 AI 工程的差距

### 3.1 MVC 分层：基础已有，但 AI use case 层不足

当前已有类似分层：

- `api/*`：HTTP 路由和契约。
- `agents/*`：Agent 编排。
- `llm/client.py`：模型调用边界。
- `services/*` / `utils/*`：搜索、安全、隐私等协作能力。
- backend `AIServiceClient`：跨服务调用边界。

差距：

- 部分 router 仍直接组织 prompt、调用 LLM、解析 JSON。
- 缺少独立 `UseCase` / `SkillExecutor` / `PromptRenderer` / `OutputParser` / `PolicyGuard`。
- helper 函数承担太多业务规则，后续会继续膨胀。

优化方向：

- Controller / router 只负责 HTTP 语义、鉴权依赖和契约校验。
- AI 业务流程下沉到 use case。
- Prompt 渲染、LLM 调用、输出解析、安全策略、trace 记录拆成明确协作类。

### 3.2 Skill：从登记表升级为运行时

当前已有 `family_skill_registry.py` 和 `SkillRunService`，但它们还不是 skill runtime。

差距：

- skill registry 是静态 dict，不是强类型 manifest。
- skill endpoint 和 skill 定义没有统一执行层。
- `SkillRunService` 是记录服务，不负责 lifecycle、确认、失败、幂等。
- skill run metadata 仍偏开放，不符合强类型收敛方向。

优化方向：

- 新增 `SkillManifest`：name、version、description、inputSchema、outputSchema、reads、writes、requiresConfirmation、timeoutSeconds、privacyLevel。
- 新增 `SkillExecutor`：统一执行入口。
- 新增 `SkillRunLifecycleService`：集中处理 planned、running、waiting_confirmation、succeeded、failed、canceled。
- 把 `save_memory`、`organize_draft`、`persona_material_draft` 作为首批迁移 skill。

### 3.3 Agent / Tool 编排：从固定 pipeline 升级为可治理流程

当前 `FamilyAgent.chat_stream()` 基本是固定流程：安全检查、quick/think 判断、可选 Web Search、拼 prompt、stream 调用 LLM。

差距：

- Web Search 是硬编码分支，不是标准 tool。
- memory recall、save planning、draft generation 不是统一 tool call。
- 没有统一 tool result、tool error、tool timeout 语义。
- 缺少 agent trajectory 记录，难以复盘 AI 为什么做了某个决定。

优化方向：

- 新增轻量 `AgentRunner`。
- 新增 `ToolDefinition` 和 `ToolRegistry`。
- 先把 `authorized_memory_recall`、`web_search_public`、`save_memory_plan`、`organize_draft` 封装为内部 tools。
- 所有 tool 必须声明 timeout、retryable、sideEffect、privacyPolicy。

### 3.4 Harness / Evals：从接口测试升级为 AI 行为评测

当前测试覆盖了不少安全和契约场景，但缺少 AI 行为评测体系。

差距：

- 缺少 golden conversations。
- 缺少 prompt / model 变更前后对比分数。
- 缺少 trajectory eval，无法验证“过程是否正确”。
- 缺少 Web Search 是否应触发、query rewrite 是否合格的评测集。
- 缺少 Memory Recall 召回质量评估。

优化方向：

- 新增 `ai-service/evals/`。
- 建立家庭聊天、保存记忆、整理草稿、mirror、persona、Web Search、embedding 的小型 eval dataset。
- 先用 deterministic mock LLM 做低成本稳定测试。
- 真实模型 eval 作为 nightly 或手动任务，不阻塞常规开发。

### 3.5 Trace：从 requestId 升级为全链路 span

当前 requestId 可以串起请求，但不能描述一次 agent run 内部发生了什么。

差距：

- 缺少 `agent.run`、`retrieval.memory_recall`、`tool.web_search`、`skill.save_plan`、`llm.chat`、`output.parse` 等 span。
- stream、embedding、memory extraction 的观测字段正在靠近一致，但 agent 内部步骤不可回放。
- requestId 不等于 runId，无法表达一次 run 下多个 tool / skill / LLM 调用。

优化方向：

- 新增 `TraceRecorder`。
- 统一记录 requestId、runId、spanId、parentSpanId、operation、provider、model、promptVersion、skillVersion、latencyMs、success、errorCode、degraded、privacyCategories。
- 先写结构化日志，不急于引入复杂链路追踪平台。

### 3.6 Prompt / Schema / Skill 版本化

当前 prompt 多为 Python 常量，schema 与 skill 绑定不够明确。

差距：

- prompt 没有统一 id/version。
- schema 没有和 skill manifest 绑定。
- 运行日志没有稳定记录 promptVersion / schemaVersion / skillVersion。
- eval 用例没有声明覆盖哪个 prompt 或 skill。

优化方向：

- 核心 prompt 增加 `prompt_id` 和 `prompt_version`。
- 核心 JSON schema 增加 `schema_id` 和 `schema_version`。
- skill manifest 引用 prompt/schema 版本。
- trace、SkillRun、eval 报告记录实际使用版本。

---

## 4. Harness 目标架构

目标是在当前模块化单体 + Python AI service 上建立 Harness 中枢，而不是重新拆系统。backend 继续作为业务权威，AI service 升级为 AI runtime + eval + trace + governance harness。

建议目标分层：

1. API Adapter Layer
   - FastAPI router / Spring Controller。
   - 负责 HTTP、鉴权依赖、请求校验、响应语义。
   - 保持 URL、DTO、SSE event 和 errorCode 稳定。

2. Contract Harness Layer
   - 固定外部契约和隐藏契约。
   - 覆盖 stream EOF、provider failure、Embedding 状态、Web Search 隐私、requestId 透传。

3. Use Case Layer
   - 每个 AI 能力的业务用例。
   - 例如 `SaveMemoryPlanUseCase`、`OrganizeDraftUseCase`、`FamilyChatUseCase`。

4. Agent / Skill Runtime Harness Layer
   - `AgentRunner`。
   - `SkillManifest`、`SkillExecutor`、`SkillRunLifecycleService`。
   - 负责 run lifecycle、tool 调用、skill 输入输出、确认、审计、状态迁移、stream event、错误语义。

5. Tool / Gateway Layer
   - Web Search、Memory Recall、LLM、Embedding、Backend Client。
   - 负责外部依赖、timeout、retry、降级和隐私边界。

6. Trace / Replay Harness Layer
   - `TraceRecorder`、run artifact、span tree、tool trajectory、版本记录。
   - 负责脱敏回放和过程复盘。

7. Evaluation / Governance Harness Layer
   - eval datasets、golden conversations、trajectory eval、regression score、release gate。
   - 负责模型、prompt、schema、skill 变更的持续验证和治理。

---

## 5. 分阶段计划

## Phase 1：L1 Contract Harness 基线

时间建议：1-2 周

目标：先让 AI 重构可验证，避免内部调整破坏外部调用方。这一阶段不是普通测试补充，而是建立后续内部重构的契约护栏。

工作项：

1. 固定核心接口契约：
   - `/ai/agent/chat/stream`
   - `/ai/memory/save-plan`
   - `/ai/memory/organize-draft`
   - `/ai/memory/persona-material-draft`
   - `/ai/embedding/embed`
2. 固定 stream event：
   - `content`
   - `metadata`
   - `done`
   - `error`
3. 新增 `ai-service/evals/`。
4. 建立第一批 contract / safety golden cases：
   - history system role 注入。
   - 客户端伪造 memory_context。
   - Web Search 隐私 query。
   - save-plan 垃圾输入。
   - organize-draft schema 输出。
   - stream provider failure。
   - embedding provider failure。
5. eval runner 支持 mock LLM 和 JSON 报告。

验收标准：

- 至少 20 条 AI golden cases。
- 本地可一键运行低成本 eval。
- 任何内部重构都能验证接口、失败语义和隐私边界未漂移。

---

## Phase 2：L2 Skill Runtime Harness 最小切片

时间建议：1-2 周

目标：选择风险较低、最像 skill 的 `/ai/memory/save-plan` 作为第一刀，验证 Skill Runtime Harness 可行性。

目标结构：

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
2. 新增 `SaveMemoryPlanUseCase`。
3. 新增 `SaveMemorySkillExecutor`。
4. 抽出 prompt 渲染和输出解析。
5. 接入 TraceRecorder 雏形。
6. 把 save-plan 相关 eval 接入新路径。
7. 为 save-plan 建立 skill run artifact 雏形。

验收标准：

- 外部接口完全兼容。
- `memory.py` 不再直接承载 save-plan 的完整编排。
- save-plan 执行过程有 runId / spanId / promptVersion / skillVersion。
- save-plan 的失败、拒绝、降级语义通过 eval 固定。

---

## Phase 3：L2 Skill Manifest 与 SkillRun Lifecycle 完整化

时间建议：2-3 周

目标：把 skill 从静态 registry 升级为可执行、可审计、可版本化、可评测的 runtime。

工作项：

1. 新增 `SkillManifest`。
2. 新增 `SkillExecutor` 接口。
3. 迁移三个核心 skill：
   - `save_memory`
   - `organize_draft`
   - `persona_material_draft`
4. 后端拆分 `SkillRunService`：
   - `SkillRunCommandService`
   - `SkillRunQueryService`
   - `SkillRunLifecycleService`
5. 收敛 `SkillRun.metadata`：
   - 新增强类型 `SkillRunMetadata`。
   - 动态兼容字段放入 `extra`。

验收标准：

- skill registry 可关联 manifest 和 executor。
- 三个迁移 skill 保持现有 API 兼容。
- 每次 skill 执行都有强类型 run record。
- 需要确认的 skill 不会绕过确认直接写入最终数据。

---

## Phase 4：L3 Agent Tool Harness

时间建议：3-4 周

目标：把固定 pipeline 中的 Web Search、Memory Recall、Skill 调用抽象为可治理、可评测 trajectory 的 tool。

工作项：

1. 新增 `ToolDefinition`：
   - name。
   - inputSchema。
   - outputSchema。
   - sideEffect。
   - timeoutSeconds。
   - retryable。
   - privacyPolicy。
2. 新增内部 tools：
   - `authorized_memory_recall`
   - `web_search_public`
   - `save_memory_plan`
   - `organize_draft`
3. 新增 `AgentRunner`：
   - 管理 runId。
   - 管理 tool call。
   - 统一输出 stream metadata/content/error/done。
4. `FamilyAgent.chat_stream()` 逐步瘦身。
5. 所有 tool 调用必须经过 policy guard。

验收标准：

- Web Search 不再只是 chat pipeline 的硬编码分支。
- tool call 有统一 timeout、errorCode 和 trace。
- agent run 可记录 tool 使用摘要。
- 不泄露隐私 query、原始 memory_context 或系统 prompt。

---

## Phase 5：L4 Trace Replay Harness

时间建议：2-3 周

目标：从 requestId 追踪升级为 agent run 级别的结构化 trace，并沉淀可脱敏回放的 run artifact。

工作项：

1. 完善 `TraceRecorder`。
2. 定义 span 字段：
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
3. 接入关键链路：
   - chat stream。
   - LLM call。
   - Web Search。
   - Memory Recall。
   - Skill execution。
   - Embedding。
4. 新增 run artifact / replay 数据结构：
   - 不保存敏感原文。
   - 保存脱敏输入摘要、版本、工具轨迹和结果摘要。
   - 保存 eval 可读取的 trajectory summary。

验收标准：

- 一次聊天可以看到完整 run tree。
- 任何 LLM / tool / skill 失败都能定位到 span。
- eval 可读取 trace / run artifact 检查 trajectory。

---

## Phase 6：L5 Continuous Governance Harness

时间建议：2 周

目标：让 AI 变更可比较、可回滚、可评估，并逐步建立模型、prompt、schema、skill 上线门禁。

工作项：

1. 为核心 prompt 增加版本：
   - `family_chat.system.v1`
   - `memory.save_plan.v1`
   - `memory.organize_draft.v1`
   - `persona.material_draft.v1`
2. 为核心 schema 增加版本：
   - `save_tool_plan.schema.v1`
   - `organized_draft.schema.v1`
   - `persona_material_draft.schema.v1`
3. skill manifest 引用 prompt/schema 版本。
4. trace、SkillRun、eval 报告记录版本。
5. 增加 prompt 变更 checklist：
   - 是否更新 eval。
   - 是否影响前端展示。
   - 是否改变保存语义。
   - 是否改变隐私边界。
6. 增加 regression report：
   - eval pass rate。
   - trajectory pass rate。
   - safety / privacy failure count。
   - model / prompt 对比结论。
7. 定义上线门禁：
   - P0 safety / privacy case 必须 100% 通过。
   - contract case 必须 100% 通过。
   - 真实模型抽样 eval 可先作为人工审批依据，不立即阻断常规开发。

验收标准：

- 任何一次 AI 输出都能追溯 prompt/schema/skill 版本。
- prompt 改动必须跑对应 eval。
- 模型升级前后能比较核心场景结果。
- AI 变更有 regression report 和明确 release gate。

---

## 6. 优先级

P0：

- 接口契约测试。
- Contract Harness。
- Evaluation Harness 基线。
- `/ai/memory/save-plan` 最小 runtime 切片。
- stream / embedding / Web Search 隐私回归 eval。

P1：

- SkillManifest。
- SkillExecutor。
- SkillRunLifecycleService。
- TraceRecorder。

P2：

- ToolRegistry。
- AgentRunner。
- trajectory replay。
- Memory Recall quality eval。

P3：

- MCP 适配评估。
- 多 agent handoff。
- 更复杂的长期任务调度。

---

## 7. 不做事项

除非单独立项，本计划不做以下事项：

1. 不拆分多个 AI 微服务。
2. 不一次性重写所有 prompt。
3. 不让模型直接写数据库或绕过 backend 权限。
4. 不扩大 Web Search 到未治理的隐私场景。
5. 不引入大型工作流引擎替代当前链路。
6. 不破坏现有 URL、SSE event、errorCode、Embedding 状态语义。
7. 不继续扩大裸 `Map` / `Object metadata` 作为主契约。

---

## 8. 成功标准

满足以下条件，可以认为 AI 服务已经从“安全增强 AI 网关”升级到“AI Architecture Harness 雏形”：

1. L1：核心 API、SSE event、errorCode、Embedding 状态、Web Search 隐私边界都有 contract harness 保护。
2. L2：每个核心 skill 都有 manifest、executor、input schema、output schema、版本和 eval binding。
3. L2：每次 skill 执行都有强类型 run record、状态迁移、失败原因、requestId 和 runId。
4. L3：家庭聊天链路中的 Web Search、Memory Recall、Skill Planning 至少部分通过 tool registry 管理。
5. L3：Agent / tool trajectory 可被 eval 检查，而不只是最终输出可测。
6. L4：trace 能串起一次 agent run 下的 LLM、tool、retrieval、skill 调用，并形成可脱敏 replay artifact。
7. L5：prompt/schema/skill/model 变更有版本、有 eval、有 regression report、有回滚依据。
8. 新增 AI 能力默认复用 harness，而不是继续新增散落 endpoint + helper。

---

## 9. 建议首批落地文件

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

Backend：

- `backend/src/main/java/com/familyagent/module/skillrun/service/SkillRunLifecycleService.java`
- `backend/src/main/java/com/familyagent/module/skillrun/dto/SkillRunMetadata.java`
- `backend/src/main/java/com/familyagent/module/skillrun/dto/SkillRunTraceSummary.java`

注意：以上文件是方向建议，不要求一次性全部创建。每次实现必须遵守仓库规则：小步改动、强类型契约、失败路径明确、不要继续扩大大 Service 和匿名 Map。
