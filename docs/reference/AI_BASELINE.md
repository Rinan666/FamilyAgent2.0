# FamilyAgent AI 工程基线

> 基线日期：2026-08-04  
> 状态：已建立；仅在测试、生产指标或新产品需求提供明确证据时重新立项  
> 范围：`ai-service/`、Backend Agent/Memory/AI 边界、Frontend Agent 交互契约

---

## 1. 基线定位

FamilyAgent 已完成本阶段 AI Harness、RAG、工具治理、追溯、评测和运行边界建设。本文件记录已经成立的工程事实和后续约束，不再作为待办计划使用。

当前架构是**应用自管、provider-neutral 的 Agent 架构**：

- Python AI Service 负责模型调用、Prompt 编排、Web Search、流式输出和固定 Skill 工作流。
- Java Backend 是家庭数据、权限、召回、工具、确认、持久化、审计和 provenance 的唯一业务权威。
- Frontend 负责会话体验、SSE 消费、证据展示和用户确认交互。
- 默认聊天模型为 DashScope Qwen，模型调用通过 LiteLLM/兼容层抽象；Embedding 独立配置。
- 当前没有模型自主选择工具的 function-calling loop，也没有多 Agent、MCP、Managed Agent 或 provider 托管的持久会话。

因此，“更换模型”与“替换 Agent 架构”是两类工作。新增 Claude、OpenAI 或其他模型时，默认只接入现有 provider 边界，不绕过 Backend 权限、确认和审计链路。

---

## 2. 已稳定能力

### 2.1 安全与业务边界

- Backend 统一处理家庭成员权限、可信上下文、写入确认、幂等执行、审计和记录来源。
- Frontend 不可通过 `/ai-proxy/*` 绕过 Backend 调用 Backend-owned AI 能力。
- Python 内部 save-plan、organize-draft、persona-material-draft 等能力要求内部服务身份。
- 客户端提供的 `memory_context` 不能直接成为高可信家庭上下文。
- Provider 异常正文、家庭内容、原始搜索 query、Prompt 和模型输出不进入 AI 调用日志。

### 2.2 RAG 与 Embedding

- PostgreSQL + pgvector 是当前向量存储，不引入额外向量数据库。
- 召回先按家庭权限加载候选，再在已授权 ID 范围内执行向量排序。
- 排序综合向量、文本、成员关系、时间和业务权重；Embedding 异常时降级为文本召回。
- Recall 质量评测覆盖精确命中、语义命中、文本 fallback、Embedding degraded 和未授权排除。
- 未授权召回数量必须为 0，并作为隐私硬门禁。

### 2.3 Agent 工具与确认

- Backend 已具备 AgentTool descriptor、registry、permission gate、executor、confirmation、audit 和 provenance。
- 已落地家庭记忆召回、日记、个人记忆、家庭记忆和成长观察等工具。
- 读工具可直接执行；写工具默认经过确认和幂等保护。
- 当前工具由应用流程确定，不向模型暴露为 provider function/tool definitions。

### 2.4 Skill、Trace、Replay 与版本

- 核心 Skill 已具备 manifest、executor、输入输出契约、超时、权限/隐私声明和版本。
- AgentRun、AgentRunStep、requestId、runId、tool call、confirmation、Trace、Replay 和最终记录 provenance 已形成闭环。
- 核心输出需声明适用的 skillVersion、promptVersion、schemaVersion 或 algorithmVersion。
- 新增核心 AI 能力如果没有版本声明和 eval binding，应由契约测试阻止合入。

### 2.5 Streaming、失败语义与观测

- Python → Backend → Frontend 已形成 typed SSE 链路，包含 `content`、`metadata`、`done`、`error`。
- EOF 前未收到 `done` 或 `error` 时，客户端必须判定为失败。
- Provider、stream、Embedding 和 Skill 失败使用结构化错误语义，不伪装为普通 assistant 文本。
- 已有 provider/model/latency/error/degraded 指标、Agent run/span 持久化、低频 synthetic monitor 和真实 provider 抽样评测。

### 2.6 依赖与部署

- AI Service 生产依赖与开发测试依赖已分离。
- 生产镜像不包含 pytest、ruff 等开发工具。
- AI Service 不依赖未使用的 PostgreSQL、Redis 或 RabbitMQ 连接即可通过自身 readiness。
- `AIServiceClient` 已拆为 chat stream、Embedding、health 和公共请求支持等独立边界，兼容门面只保留委托职责。

---

## 3. Provider 与模型接入约束

当前 provider 抽象使用 OpenAI 风格的通用参数，并不能自动代表所有模型能力完全兼容。接入或升级模型前必须验证：

1. 模型标识符合当前 LiteLLM 版本的 provider-qualified 规则。
2. `temperature`、`response_format`、额外 body 等参数是否被目标模型接受或正确转换。
3. 流式失败、fallback 和结构化输出是否保持现有 SSE/错误契约。
4. 真实 provider 抽样不保存 Prompt、家庭原文或模型完整输出。
5. Chat 模型迁移不隐式替换 Embedding 模型；当前数据库和服务约束为 1536 维。
6. 若引入 provider-native tool use、Prompt caching、compaction 或 Agent SDK，必须单独设计与现有 Backend 权威边界的衔接，不能按“更换模型配置”处理。

---

## 4. 仍存在但不自动立项的缺口

以下能力尚未形成完整产品或运行时能力，但只有满足实际需求和验收指标后才进入计划：

- 长文本拆解、候选记录生成、去重/合并建议和批量确认。
- 用户可见的多步骤任务工作台、取消、重试和恢复。
- 跨会话主动提醒、周期总结和持久调度。
- 模型自主工具调用与 tool-result continuation。
- 长会话 token-aware history、自动摘要或 compaction。
- 生产聊天 token/cost、provider request ID 和分布式 trace。
- 全页 Web 内容抓取、claim-level citation 和远程内容注入防护。

这些缺口主要应由产品路线图决定，不能仅因主流框架提供对应能力而实施。

---

## 5. 明确不做

没有新的证据时，不实施：

- 拆分多个 AI 微服务。
- 引入 Temporal、Camunda、Conductor 等重型工作流引擎。
- 为形式完整引入 MCP、多 Agent handoff 或 provider 托管 Agent。
- 让模型直接访问数据库或绕过 Backend 权限、确认和审计。
- 全量重写 Prompt，或全量清除 Map、metadata、JSONB 动态字段。
- 重做已稳定的 AgentTool、Skill Runtime、Trace、Replay、Eval 或 provenance。
- 仅按文件行数或匹配数量发起重构。

---

## 6. 重新立项条件

至少满足一项后再创建新计划：

- 出现失败测试、生产错误、隐私/权限风险或可复现的真实 smoke 失败。
- Provider 质量、延迟、成本或 fallback 连续回归。
- 新产品能力需要跨步骤、跨会话或可恢复执行。
- 稳定匿名契约已有多个消费者并显著增加维护成本。
- 新模型或 provider-native 能力能以明确指标改善质量、成本或延迟。
- 当前类持续增长并混合三个以上独立职责。

---

## 7. 验证要求

本文件不维护永久递增的测试数量。后续改动应以当时仓库为准，至少执行受影响范围的：

- Backend 单元/集成测试和 Flyway 全量迁移验证。
- AI Service pytest、deterministic eval、版本/契约门禁。
- Frontend lint、typecheck、相关组件测试和 production build。
- Provider 变更时的低成本 synthetic smoke 与人工触发质量抽样。
- 涉及用户流程时的真实端到端验证。

历史完成情况、精确测试数量、镜像大小和迁移范围以 Git、CI 与发布记录为准。
