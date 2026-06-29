# FamilyAgent 轻型 Agent Harness 计划

> 更新日期：2026-06-29
> 范围：后端 `agent` / `memory` / `diary` / `growth` / `mirror` / `family` 相关 Agent 工具调用链路，前端 Agent 交互与确认流程
> 目标：在不引入独立工作流引擎、不拆微服务的前提下，建立轻型 Agent harness，让 Agent 的工具调用、权限、确认、审计和失败语义有统一边界。

---

## 1. 与 AI 优化计划的关系

`docs/AI_OPTIMIZATION_PLAN.md` 是本计划的前置地基，但不是 harness 本身。

AI 优化计划主要解决：

1. 用户可控输入不能提升为高可信上下文。
2. AI provider 失败不能伪装成成功回答。
3. Embedding 失败或 degraded 不能污染 READY 索引。
4. Web Search 不能原样外发家庭隐私 query。
5. Backend / AI service / frontend 的核心契约要强类型化。
6. Timeout、retry、fallback、metrics、requestId 等基础设施语义要统一。

轻型 Agent harness 在此基础上继续解决：

1. Agent 可以调用哪些工具。
2. 每个工具需要什么权限。
3. 哪些工具调用需要用户确认。
4. 工具调用输入、输出、失败和写入动作如何审计。
5. Agent 的一次任务如何形成可追踪的 run / step / tool call 记录。

结论：**不是必须等 AI 优化计划 100% 完成后才能启动 harness，但必须先完成核心安全与契约地基。当前仓库已基本具备启动轻型 harness 的条件。**

---

## 2. 当前判断

FamilyAgent 目前已有一部分 harness 前置能力：

- `AgentMemoryContextFacade`、`AgentMirrorContextFacade`、`AgentPersonaContextFacade` 已把高可信上下文收回后端生成。
- `AIServiceClient` 已具备 typed request/response、timeout、retry、fallback、metrics。
- Chat stream 已有 typed `content` / `metadata` / `done` / `error` event。
- 保存记忆、写日记、成长观察、镜像/persona 上下文等能力已存在，但仍是各业务模块各自编排。

当前缺口：

1. 没有统一 `AgentTool` 抽象。
2. 没有统一工具注册中心。
3. 没有统一工具权限门。
4. 没有统一工具调用审计记录。
5. 保存类动作的用户确认还不是 harness 级状态。
6. 没有 `AgentRun` / `AgentStep` 的最小生命周期记录。

---

## 3. 本阶段不做什么

轻型 harness 阶段明确不做：

1. 不引入 Temporal、Camunda、Conductor 等独立工作流引擎。
2. 不把现有单体拆成多服务。
3. 不让 LLM 任意动态调用数据库写操作。
4. 不重写现有聊天链路。
5. 不把所有业务 Service 改成工具。
6. 不做跨天长任务恢复和复杂状态机。
7. 不把 prompt / tool / memory 全部平台化。

本阶段只做一个可落地的最小闭环：**工具声明、权限检查、确认策略、调用记录、可复用执行器。**

---

## 4. 轻型 Harness 目标形态

### 4.1 核心对象

建议新增后端包：

`backend/src/main/java/com/familyagent/module/agent/harness/`

核心类型：

1. `AgentTool`
   - 工具接口。
   - 负责声明工具名、输入类型、输出类型、是否写入、是否需要确认。

2. `AgentToolRegistry`
   - 工具注册中心。
   - 通过 Spring 注入收集所有 `AgentTool`。
   - 禁止业务代码临时拼工具名调用。

3. `AgentToolPermissionGate`
   - 统一工具权限门。
   - 根据 familyId、viewerUserId、tool name、input 判断是否允许执行。

4. `AgentToolExecutor`
   - 统一工具执行器。
   - 负责权限检查、确认检查、异常映射、审计记录。

5. `AgentToolCallRecord`
   - 工具调用记录。
   - 第一阶段可先用数据库表或现有 session metadata 承载；若要可查询，优先建表。

6. `AgentConfirmationPolicy`
   - 统一确认策略。
   - 区分只读工具、草稿工具、写入工具、高风险写入工具。

7. `AgentRunContext`
   - 一次 Agent 调用的上下文。
   - 包含 requestId、familyId、viewerUserId、sessionId、agentMode、subject、contextLabel。

### 4.2 第一批工具

优先接入最小但高价值的工具：

1. `recall_family_memory`
   - 只读。
   - 使用 `AgentMemoryContextFacade` 或授权召回服务。
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

---

## 5. 分阶段计划

## Phase 1：轻型 Harness 骨架

目标：建立工具声明、注册、权限、执行和审计的最小闭环。

工作项：

1. 新增 `AgentTool` 接口。
2. 新增 `AgentToolRegistry`。
3. 新增 `AgentToolExecutor`。
4. 新增 `AgentToolPermissionGate`。
5. 新增工具调用 DTO：
   - `AgentToolCallRequest`
   - `AgentToolCallResult`
   - `AgentToolDescriptor`
6. 新增最小审计记录：
   - 可先建 `agent_tool_calls` 表。
   - 字段包含 toolName、familyId、viewerUserId、requestId、inputSummary、status、errorCode、createdAt。
7. 接入只读工具 `recall_family_memory`。

验收标准：

- 工具不能绕过 registry 执行。
- 工具执行前必经 permission gate。
- 每次工具调用都有审计记录。
- 只读工具失败返回结构化错误，不伪装成普通 assistant 文本。

---

## Phase 2：写入工具与确认门

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

---

## Phase 3：Agent Run 最小记录

目标：让一次 Agent 操作形成可追踪的 run timeline，但不做重型状态机。

工作项：

1. 新增 `agent_runs` 表。
2. 新增 `agent_run_steps` 表或轻量事件表。
3. `AgentRunContext` 增加 runId。
4. 每次工具调用关联 runId。
5. 前端可展示最小来源：
   - 使用了哪些工具。
   - 是否写入。
   - 是否等待确认。
   - 失败原因。

验收标准：

- 可以按 sessionId / requestId 查询一次 Agent 调用的工具轨迹。
- 可以解释某条记忆或成长观察由哪个 Agent 工具写入。
- 不要求跨天恢复，不要求异步工作流。

---

## Phase 4：评测与回放基础

目标：让 harness 支持回归测试和行为评估。

工作项：

1. 固定工具输入输出 schema。
2. 增加工具调用契约测试。
3. 增加权限拒绝测试。
4. 增加确认拒绝/重复确认测试。
5. 增加 Agent run replay fixture。

验收标准：

- 关键工具可以用 fixture 回放。
- 权限、确认、失败、幂等都有测试。
- 新增工具必须提供 descriptor 和测试。

---

## 6. 何时进入重型 Harness

只有出现以下情况，才考虑重型 harness 或 workflow engine：

1. Agent 任务需要跨天运行。
2. 任务需要服务重启后自动恢复。
3. 任务步骤超过 5-8 个并且依赖复杂条件分支。
4. 多个 worker 并发处理同一类 Agent 任务。
5. 需要暂停、恢复、取消、人工审批、定时继续。
6. 需要跨多个外部系统写入。

到那时再考虑：

- 独立 workflow engine。
- 完整 AgentRun 状态机。
- 持久化 step executor。
- outbox / inbox。
- dead letter queue。
- 长任务恢复队列。

当前阶段不需要。

---

## 7. 成功标准

轻型 harness 完成后，应满足：

1. Agent 工具调用有统一入口。
2. 工具权限检查不可绕过。
3. 写入工具默认有确认策略。
4. 工具调用可审计、可查询。
5. 工具失败有结构化错误。
6. 新增 Agent 工具有稳定 descriptor、DTO 和测试。
7. 当前 AI 安全边界不会因工具调用被绕开。

---

## 8. 推荐第一步

第一步不做大改，建议只实现：

1. `AgentTool` 接口。
2. `AgentToolRegistry`。
3. `AgentToolExecutor`。
4. `AgentToolPermissionGate`。
5. `recall_family_memory` 只读工具。
6. `agent_tool_calls` 最小审计表。

这一步完成后，FamilyAgent 才算真正进入轻型 harness 阶段。
