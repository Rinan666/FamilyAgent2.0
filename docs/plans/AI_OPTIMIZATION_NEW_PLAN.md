# FamilyAgent AI 当前优化计划

> 更新日期：2026-07-17
> 作用：只维护当前仍需解决的问题，不再记录已经完成的阶段历史。
> 历史实现与验证记录以 Git 提交、测试和迁移历史为准。

---

## 1. 当前判断

FamilyAgent 已经完成 AI Architecture Harness 的核心骨架，当前重点不再是继续搭建 AgentTool、确认门、Skill Runtime、Trace、Replay 或基础 Eval。

接下来的优化目标是：

1. 证明召回结果质量，而不只是证明链路能运行。
2. 让最终业务记录可以反向追溯到 Agent run、tool call 和版本。
3. 让核心 AI 输出缺少版本信息时能够被自动发现。
4. 收敛真正稳定的匿名契约，不进行无差别 Map 清理。
5. 让生产镜像依赖更小、更可复现，开发依赖不进入运行镜像。
6. 建立低频、可控成本的真实 provider 质量监测，而不把付费调用塞进 readiness。
7. 继续拆分仍明显承担过多职责的基础设施客户端。

这份计划不是功能愿望列表。没有证据表明存在问题的模块，不进入当前优化范围。

---

## 2. 已稳定基线，不再作为优化主线

以下能力已完成并有测试或真实环境验证。除非出现回归，不再为它们继续新增抽象或重复重构：

- Backend 是家庭数据、权限、确认、写入和审计的唯一业务权威。
- Agent 工具具备统一 descriptor、registry、permission gate、executor 和 audit。
- 日记、家庭记忆、成长观察写入默认经过确认门和幂等执行。
- Python save-plan、organize-draft、persona-material-draft 只能由内部服务身份调用。
- 前端业务 AI 能力已迁入 Backend，`/ai-proxy/*` 对 Backend-owned 能力默认拒绝。
- AgentRun、AgentRunStep、requestId、runId、Trace、Replay 已形成最小闭环。
- LLM、Web Search、Memory Recall、Embedding 和 Skill execution 已接入结构化 observation。
- provider failure、stream error、Embedding failure 和 draft failure 已使用结构化错误语义。
- provider 异常正文、响应 body、家庭内容、原始搜索 query 和模型输出不进入 AI 调用日志。
- 核心 skill 已具备 manifest、executor、prompt/schema/skill 版本和 SkillRun。
- 36 条 deterministic AI eval、3 条 Backend trajectory fixture 和基础 regression comparison 已可运行。
- V15/V16 已在 PostgreSQL 16.14 真实执行。
- AI Service readiness、Docker healthcheck、官方构建源、BuildKit cache 和 headless OpenCV 已验证。

这些能力只需保持回归测试。不要为了“统一”再次搬迁代码或新增第二套框架。

---

## 3. 进入计划的判定标准

新增优化项至少满足一项：

- 有失败测试、线上错误、日志证据或真实 smoke 证明问题存在。
- 当前契约稳定但仍使用匿名结构，已经影响跨端维护或版本治理。
- 当前评测只能证明链路成功，不能证明业务质量。
- 当前实现明显增加生产镜像、发布不可复现性或运行成本。
- 当前类持续增长并已经混合三个以上独立职责。
- 缺口会造成隐私泄露、权限绕过、错误成功、不可追溯或不可回滚。

以下情况不应单独立项：

- 只因为某个文件使用了 Map。
- 只因为某个类超过一个主观行数阈值，但职责仍单一。
- 只为了追求微服务、工作流引擎、MCP 或多 Agent 的形式完整。
- 只为了增加更多 prompt、更多埋点或更多状态值。
- 已有测试和真实验证均正常，没有明确收益指标。

---

## 4. 当前优先事项

### P0-1：Memory Recall 质量评测

> 状态（2026-07-17）：已实现并通过 Backend 合成质量套件。覆盖精确命中、主题语义命中、Embedding degraded 文本 fallback 和未授权排除；支持 baseline/candidate 排名比较，未授权结果会触发隐私硬失败。

#### 当前问题

现有 eval 覆盖权限、失败语义、save-plan、draft、stream 和 Embedding 契约，但还没有真正评估授权家庭记忆召回的排序质量。

当前只能证明：召回不会越权、失败会降级、Embedding 异常不会污染 READY。还不能证明：最相关的家庭记忆是否稳定出现在 Top K。

#### 工作范围

1. 建立不包含真实家庭隐私的 recall fixture：
   - 精确关键词命中。
   - 同义表达与语义相关命中。
   - 时间、成员、类型和 scope 过滤。
   - 文本 fallback。
   - degraded Embedding。
   - 相似但未授权内容必须排除。
2. 增加最小质量指标：
   - unauthorized result count 必须为 0。
   - expected Top K hit rate。
   - 首条结果命中率。
   - baseline/candidate 排序变化。
3. Eval 报告只记录 case ID、候选数量、排名、命中与否和算法版本，不保存 query、记忆正文或向量。

#### 验收标准

- 至少覆盖正常语义命中、文本 fallback、degraded、权限排除四类场景。
- 排序算法或权重修改可以与 baseline 自动比较。
- 任何未授权结果都使 P0 privacy gate 失败。

---

### P0-2：最终业务记录的反向追溯

> 状态（2026-07-17）：已完成并在 PostgreSQL 16 + pgvector 临时数据库按 V1→V17 全量执行通过。三类 Agent 写入通过集中 provenance 表关联最终记录、Agent run、tool call、工具版本；删除通过外键级联清理。

#### 当前问题

Agent run、tool call、confirmation、SkillRun 和 trace 已经存在，但还需要验证最终记忆、日记、成长观察能否稳定反查创建它的 tool call 和 Agent run。

不能只在执行日志中看到“调用成功”，还要能从最终业务记录解释其来源。

#### 工作范围

1. 盘点三类写入实体当前已有的 source、metadata 和审计字段。
2. 选择最小稳定关联方式：
   - 业务实体保存 `toolCallId` / `agentRunId`；或
   - 使用集中 provenance reference 表。
3. 不保存 prompt、模型输出或确认原文，只保存稳定 ID、工具名、版本和时间。
4. 提供内部只读查询能力，不直接开放普通用户管理接口。

#### 验收标准

- 任意 Agent 创建的记忆、日记或成长观察都能反查 tool call 和 run。
- 手工创建记录不会被伪装成 Agent 创建。
- 删除、回滚和重复确认不会产生错误来源关联。

---

### P0-3：核心 AI 输出版本完整性门禁

> 状态（2026-07-17）：已实现 Java/Python 机器可读清单和契约测试；save-memory 补齐 schemaVersion，Recall ranking 增加 algorithmVersion，Eval artifact 已纳入 ranking algorithm。

#### 当前问题

Chat、save-memory、organize-draft 和 persona-material-draft 已记录主要版本，但当前没有一个统一门禁证明所有“核心 AI 输出”都带有适用的 artifact 版本。

#### 工作范围

1. 明确定义需要版本追溯的输出：
   - family chat。
   - save-memory plan。
   - organize draft。
   - persona material draft。
   - recall ranking algorithm。
2. 按能力要求记录适用字段，不强迫无 prompt 的能力伪造 promptVersion。
3. 新增 manifest consistency test：
   - skillVersion。
   - promptVersion（适用时）。
   - schemaVersion（适用时）。
   - model/provider observation（发生外部调用时）。
4. 缺少必需版本时，eval 或契约测试失败。

#### 验收标准

- 核心能力的版本要求有一份机器可读清单。
- 新增核心 AI 能力若没有声明版本和 eval binding，测试失败。
- Replay 和 SkillRun 使用同一版本来源，不在多处复制字面量。

---

### P1-1：有边界的强类型契约收敛

> 状态（2026-07-17）：已完成本轮第一个有消费者的迁移：Agent Memory Recall 的 rag metadata 从匿名 Map 收敛为强类型 DTO，并只在 SSE 兼容边界转换为 Map。该项按证据渐进推进，不以清零 Map 为目标。

#### 当前问题

AI 相邻 Backend 模块仍有约 71 处 `Map<String, Object>` / `Object metadata` 匹配，分布在约 22 个文件。但其中包含合法的动态 `extra`、JSONB 兼容层和 SSE event envelope，不能用匹配数量作为重构指标。

#### 工作范围

优先处理已经稳定并跨层传播的结构：

- `AgentMemoryContextResult` 中可确定的字段。
- 记忆写入请求中已经稳定的 metadata。
- Recall 输出中固定的 attribution / observation 字段。
- AI client 与 Controller 之间仍存在的稳定匿名响应。

明确保留：

- `extra` 兼容字段。
- 真正动态的 JSONB 附加信息。
- SSE event envelope 的兼容适配层。
- `MemoryIndexMetadataBuilder` 中尚未稳定的扩展信息。

#### 验收标准

- 不新增裸 Map 作为稳定主契约。
- 每次只迁移一个有明确消费者的结构，并补序列化兼容测试。
- 不以“Map 数量归零”为目标。

---

### P1-2：生产依赖与镜像可复现性

> 状态（2026-07-17）：已拆分 runtime/dev requirements，生产镜像改用固定 pip 版本和完整 runtime lock；pytest/ruff 不进入镜像，镜像构建和 pip check 已通过。当前验证镜像大小为 1.89GB，后续仅在相同基础镜像和构建参数下比较体积。

#### 当前问题

当前生产镜像安装了 `pytest`、`pytest-asyncio`、`ruff` 等开发依赖；顶层包虽已固定版本，但传递依赖和 pip 版本仍会随构建时间变化。

#### 工作范围

1. 拆分：
   - runtime requirements。
   - development/test requirements。
2. 生产镜像只安装 runtime requirements。
3. 审计当前没有应用层 import 的依赖，重点确认 SQLAlchemy、asyncpg、pgvector、pika/aio-pika、structlog 是否仍有真实运行时消费者。
4. 为 runtime 生成可审查的锁定结果，固定传递依赖或 hashes。
5. 固定构建使用的 pip 版本；发布记录保留基础镜像 digest。
6. 继续保留 InsightFace headless OpenCV 兼容处理和 `pip check`。

#### 验收标准

- 生产镜像不包含 pytest、ruff 等开发工具。
- 同一锁文件重复构建得到相同 Python 依赖版本。
- DIP、ONNX、InsightFace、readiness 和内部 draft smoke 继续通过。
- 记录优化前后镜像大小，不设脱离功能需求的体积目标。

---

### P1-3：移除 AI Service 的虚假基础设施依赖

> 状态（2026-07-17）：已移除 AI Service 的 DB/Redis/RabbitMQ 配置、Python runtime 包、Compose 环境变量和 depends_on。无这些基础设施时 readiness 返回 database=not_required 且状态为 ready。

#### 当前问题

AI Service 当前没有数据库、Redis 或 RabbitMQ 的实际应用代码，但仍保留相关配置、Python 包和 Compose `depends_on`。这会让 AI Service 在无业务需要时等待 PostgreSQL、Redis、RabbitMQ，并扩大运行镜像和故障面。

#### 工作范围

1. 再次确认没有隐藏消费者、启动 hook 或待运行 worker。
2. 若确认未使用：
   - 移除无效 DB / Redis / RabbitMQ 配置。
   - 移除对应 runtime dependencies。
   - 移除 AI Service 对这些容器的启动依赖。
3. Backend 对 PostgreSQL、Redis、RabbitMQ 的真实依赖保持不变。
4. 如果未来新增 AI 后台任务，应以独立能力和失败策略重新引入，不保留“以后可能用”的占位连接。

#### 验收标准

- PostgreSQL、Redis、RabbitMQ 不可用时，AI Service 仍能独立启动并通过自身 readiness。
- Chat、Embedding、DIP 和内部 draft 能力不发生契约漂移。
- Compose 不再把未使用基础设施故障误判为 AI Service 不可启动。

---

### P1-4：低频真实 Provider 监测

> 状态（2026-07-17）：独立 synthetic monitor 已实现，默认关闭，固定无家庭数据输入和 8 token 上限，可区分主模型成功、fallback 成功和全 provider 失败；未接入 readiness。仍需在有 provider 凭据和成本审批的环境配置低频调度并完成一次真实 smoke。

#### 当前问题

readiness 当前只检查配置，不调用付费 provider。这是正确的容器健康策略，但不能反映 provider 的长期可用性、延迟和 fallback 质量。

#### 工作范围

1. 新增独立 synthetic monitor，不接入 Docker healthcheck。
2. 使用固定、无家庭数据的最小输入。
3. 控制执行频率、并发和 token 上限。
4. 记录 provider、model、latency、success、errorCode、degraded，不记录输出正文。
5. provider 异常只影响监控状态，不自动使业务容器重启。

#### 验收标准

- 可区分主模型失败、fallback 成功和全 provider 失败。
- 有明确成本上限和关闭开关。
- 监控日志、报告和告警不包含 prompt 或模型输出。

---

### P2-1：拆分 AIServiceClient 基础设施职责

> 状态（2026-07-17）：已完成。`AIServiceClient` 从 349 行收敛为 62 行兼容门面，chat stream、Embedding、health 和统一 requestId/metrics 支持拆为独立 Spring Bean；完整 Backend 回归通过。

#### 当前问题

Backend `AIServiceClient` 约 349 行，仍同时承担 chat stream、Embedding、health、fallback、metrics 和兼容门面职责。它已经成为当前最明确的 AI 基础设施热点之一。

#### 工作范围

1. 保留 `AIServiceClient` 作为兼容门面。
2. 按能力下沉到独立 Spring Client：
   - chat stream client。
   - embedding client。
   - health client。
3. 统一 requestId、timeout、metrics 和错误映射协作类，避免复制。
4. Repository/Service 继续按接口或稳定门面注入，不跨模块直接依赖实现。

#### 验收标准

- `AIServiceClient` 只保留委托和兼容入口。
- 拆分前后 URL、SSE、错误码、fallback 和 metrics 不漂移。
- 每个子 Client 有成功、超时、transport failure 和隐私日志测试。

---

### P2-2：Eval 信号质量与真实模型抽样

> 状态（2026-07-26）：已完成。新增独立、手动触发、默认关闭的真实 provider 质量抽样，仅使用合成家庭场景；报告包含质量分、通过率、结构化失败率、延迟、token/成本和 baseline/candidate 差异，不保存 prompt 或模型原文。DashScope `qwen-flash` 两案例真实 smoke 为 2/2 通过、平均质量分 1.0、结构化失败率 0；同时修复了可复用学习策略被误分为成长观察的问题。

#### 当前问题

deterministic eval 已适合作为安全与契约门禁，但不能替代真实模型质量判断。预期失败 fixture 产生的 errorType 日志也会增加 CI 噪声。

#### 工作范围

1. 保留 deterministic eval 作为强制门禁。
2. 增加小规模真实模型抽样，仅使用合成或脱敏数据。
3. 报告主观质量、延迟、token/cost 和结构化失败率。
4. 初期只作为人工审批依据，不直接阻断普通开发。
5. Eval runner 区分“预期失败日志”和“非预期执行错误”，提高 CI 信噪比。

#### 验收标准

- baseline/candidate 可以比较真实模型样本结果。
- 真实模型报告不保存 prompt、家庭原文或模型输出全文。
- deterministic P0 safety/privacy 与 contract gate 仍要求 100% 通过。

---

## 5. 执行顺序

按以下顺序推进，前一项未形成可验证结果时不扩展下一项范围：

1. 在有成本审批的环境执行一次 synthetic provider monitor，并配置低频调度。
2. 增加真实模型抽样和 Eval 信号优化。
3. 仅在出现明确消费者和兼容收益时继续强类型契约迁移。

---

## 6. 明确不做

当前没有证据需要以下工作，因此不进入近期计划：

- 拆分多个 AI 微服务。
- 引入 Temporal、Camunda、Conductor 等工作流引擎。
- 多 Agent handoff。
- MCP 适配。
- 跨天自动恢复和多 worker 调度。
- 全量重写 prompt。
- 全量清除 Map、Object metadata 或 JSONB 动态字段。
- 重做已稳定的 AgentTool、确认门、Trace、Replay 或 Skill Runtime。
- 让模型直接访问数据库或绕过 Backend 权限。

只有出现跨天任务、服务重启恢复、复杂条件分支、多 worker 并发或跨外部系统长事务时，才重新评估重型 Harness。

---

## 7. 当前验证基线

后续优化不得低于以下基线：

- Backend：265 tests passed。
- AI Service：175 tests passed，1 条跨仓源码一致性检查在仅挂载 AI Service 的容器中跳过。
- Frontend：46 tests passed，production build 成功。
- Eval：36/36 passed。
- P0 safety/privacy gate：100%。
- Contract gate：100%。
- PostgreSQL：V1-V17 已在 PostgreSQL 16 + pgvector 临时数据库顺序验证。
- AI 容器：healthy，`pip check` 通过，内部 draft smoke 通过。
- AI 优化验证镜像：1.89GB，生产环境不包含 pytest / ruff。
- provider fixture 异常正文不进入 eval 进程输出。

每次完成一个优化项，应更新本节基线；已完成事项应从“当前优先事项”删除，不在本文件积累历史流水账。

---

## 8. 完成定义

本轮优化完成需同时满足：

1. Recall 质量可量化比较，且未授权结果始终为 0。
2. Agent 创建的最终业务记录可反查 run 和 tool call。
3. 核心 AI 输出缺少必要版本时会自动失败。
4. 稳定匿名契约按消费者逐步收敛，没有扩大动态 Map。
5. 生产镜像不包含开发依赖，运行依赖可复现。
6. AI Service 不再依赖未使用的数据库、缓存或消息队列。
7. 真实 provider 有低频、低成本、无隐私内容的可用性监测。
8. `AIServiceClient` 不再集中承担所有 AI 基础设施职责。
9. deterministic eval、真实模型抽样和完整测试矩阵均有可比较基线。
