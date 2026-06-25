# FamilyAgent AI 部分优化计划

> 更新日期：2026-06-24  
> 范围：`ai-service/`、`backend/src/main/java/com/familyagent/infra/ai/`、后端 `agent` / `memory` / `mirror` / `session` 中调用 AI 的链路  
> 目标：优先修复 AI 安全边界、失败语义、Embedding 质量、跨服务契约和隐私风险，在不大改产品功能的前提下提升稳定性与可维护性

---

## 1. 当前判断

FamilyAgent 的 AI 部分已经具备较完整的功能雏形：

- Python `ai-service` 负责聊天、记忆整理、Embedding、Web Search 等模型能力。
- Java backend 通过 `AIServiceClient` 调用 AI 服务，并在 `agent`、`memory`、`mirror` 等模块使用 AI 结果。
- 已经有一定安全限制、限流、Prompt 模板、Resilience4j 配置和基础测试。

当前主要问题不是“AI 功能是否存在”，而是以下工程治理和安全边界问题：

1. 客户端输入过于可信，存在 `history.role=system`、`memory_context` 注入等风险；其中 P0 安全边界已先完成第一轮止血。
2. AI 失败路径经常被伪装成普通成功结果，调用方难以区分正常回答、降级回答和失败。
3. Embedding provider 失败时会返回 hash embedding，可能污染长期记忆向量索引。
4. Java backend 与 Python AI service 之间大量使用裸 `Map<String, Object>`，契约弱、字段变化不可控。
5. 认证、超时、限流、重试、熔断策略在不同 AI 链路之间不一致。
6. Web Search 可能把包含家庭隐私的完整用户消息发送给外部搜索服务。
7. AI 相关测试主要覆盖 happy path，失败、超时、降级、契约漂移测试不足。

---

## 2. 优化原则

本轮 AI 优化遵循以下原则：

1. 先补安全边界，再优化体验。任何用户可控输入都不能被当成高可信系统上下文。
2. 失败必须显式表达，不把错误伪装成普通内容。
3. 长期记忆索引质量优先，Embedding 失败不应写入伪成功数据。
4. 跨服务契约逐步强类型化，优先治理高风险接口。
5. 保持现有功能和 API 尽量兼容，必要时通过过渡字段兼容旧调用。
6. AI 外部依赖必须具备可观察、可重试、可降级、可测试的边界。

---

## 3. 优先治理对象

### 3.1 AI 服务侧重点文件

- `ai-service/app/api/agent.py`
- `ai-service/app/agents/family_agent.py`
- `ai-service/app/llm/client.py`
- `ai-service/app/llm/prompts/chat.py`
- `ai-service/app/api/embedding.py`
- `ai-service/app/api/memory.py`
- `ai-service/app/api/memory_contracts.py`
- `ai-service/app/middleware/auth.py`
- `ai-service/app/utils/safety_limits.py`
- `ai-service/app/services/web_search.py`
- `ai-service/app/config.py`

### 3.2 Backend 侧重点文件

- `backend/src/main/java/com/familyagent/infra/ai/AIServiceClient.java`
- `backend/src/main/java/com/familyagent/module/agent/dto/AgentChatStreamRequest.java`
- `backend/src/main/java/com/familyagent/module/agent/controller/AgentChatController.java`
- `backend/src/main/java/com/familyagent/module/memory/service/MemoryEmbeddingService.java`
- `backend/src/main/java/com/familyagent/module/memory/service/AuthorizedMemoryRecallRankingService.java`
- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/application-prod.yml`
- `backend/src/test/java/com/familyagent/infra/ai/AIServiceClientTest.java`

---

## 4. 当前已发现问题

## P0：安全边界问题

### 4.1 客户端 history 允许传入 system role

问题：

- `AgentChatStreamRequest.HistoryMessage.role` 只是普通字符串，没有白名单。
- `ai-service` 中 `history` 使用 `list[dict]`，没有 Pydantic 类型约束。
- `family_agent.py` 会把客户端 history 直接拼入 LLM messages。
- 安全检查对 `role == "system"` 的消息跳过 prompt leak / role hijack 检查。

影响：

- 普通客户端可构造 `role=system` 历史消息，形成 prompt injection / role hijack 通道。

优化方向：

1. Backend DTO 限制 history role 只能是 `user` / `assistant`。
2. AI service Pydantic 模型也做同样限制。
3. 安全检查不能基于用户传入 role 决定是否跳过。
4. 对非法 role 返回明确 400 错误。

---

### 4.2 `memory_context` 由请求直接传入并进入 system prompt

问题：

- `memory_context` 是公开请求字段。
- Backend `toAiPayload()` 会原样转发。
- AI service 只做 redaction，不做 prompt injection 检测。
- Prompt 中把 `memory_context` 放入“已授权家族上下文”。

影响：

- 客户端可以伪造高可信“授权记忆上下文”。
- 用户输入和服务端授权记忆边界混淆。

优化方向：

1. `memory_context` 应由 backend 服务端检索和生成，不再信任客户端传入。
2. 如短期保留字段，应区分 `client_context` 和 `authorized_memory_context`。
3. Prompt 中明确上下文是数据，不是指令。
4. 对上下文加 delimiter、来源标签和注入检测。

---

### 4.3 AI 服务认证默认 development 且存在 fail-open 风险

问题：

- `app_env` 默认值为 `development`。
- dev/local 下默认允许 auth fail-open。
- 后端验证异常、超时或异常状态时可能返回 fallback user。

影响：

- 部署漏配环境变量时，AI 服务可能在非预期环境放行无效 token。

优化方向：

1. 生产镜像默认 fail-closed。
2. `APP_ENV` 缺失时不应默认为 development。
3. 只有显式 `AUTH_FAIL_OPEN=true` 且环境为 dev/local 时才允许 fail-open。
4. 启动时校验生产必要环境变量。

---

## P1：Embedding 质量和索引污染问题

### 4.4 Provider 失败时返回 hash embedding 并被标记 READY

问题：

- LiteLLM / DashScope embedding 失败时，AI service 会返回 hash embedding。
- Backend 只检查 `success` 和 `embedding` 是否存在。
- 随后写入 `memory_embeddings` 并标记为 `READY`。

影响：

- 长期记忆向量索引可能被非语义 hash 向量污染。
- 后续召回质量下降且难以排查。

优化方向：

1. hash embedding 只允许在显式 local/dev 模式使用。
2. 生产 provider 失败时返回 `success=false` 或 `degraded=true`。
3. Backend 遇到 degraded/provider failed 时不写 READY。
4. 增加 `FAILED` / `PENDING_RETRY` 状态或复用现有失败状态。
5. 记录 provider、model、dimensions、degraded、errorCode。

---

### 4.5 Embedding 响应缺少强校验

问题：

- Backend 没有校验向量维度。
- 没有校验 NaN / Infinity。
- 没有校验模型是否与索引模型一致。
- 没有区分真实成功和降级成功。

影响：

- 错误向量可能进入数据库。
- 模型切换后旧索引和新查询向量可能不兼容。

优化方向：

1. 定义强类型 `EmbeddingResponse`。
2. 强制校验 dimensions。
3. 拒绝 NaN / Infinity。
4. 记录并校验 model/version。
5. 增加对应单元测试。

---

## P1：失败语义问题

### 4.6 流式 AI 失败被伪装成普通 content

问题：

- `llm_client.chat_stream()` 失败后会 yield 普通兜底文本。
- `agent.py` 把它当正常 content 发送。
- 最后仍发送 `done=true`。

影响：

- 前端、会话持久化、指标和告警都会把失败当成成功回答。

优化方向：

1. 流式失败输出结构化 error event。
2. 区分 `done=true`、`error=true`、`degraded=true`。
3. 前端收到 error 后不应按正常 assistant 消息处理。
4. Backend 代理层保留 error event，不吞掉。

---

### 4.7 `AIServiceClient` 内部吞异常，可能绕过 Resilience4j

问题：

- `extractMemories()` 和 `embedText()` 标注了 `@CircuitBreaker` / `@Retry`。
- 但方法内部 catch 所有异常并返回 `success=false` Map。
- Resilience4j 可能无法正确统计失败、触发重试或熔断。

影响：

- 看似有重试/熔断配置，实际失败可能没有进入 Resilience4j 语义。

优化方向：

1. transport failure 继续抛异常，让 Resilience4j 处理。
2. fallback 中统一转换业务响应。
3. 区分 transport failure 和 business failure。
4. 增加 retry/fallback 触发测试。

---

## P2：契约和架构边界问题

### 4.8 AI 跨服务契约大量使用裸 Map

问题：

- `AIServiceClient` 的 chat、memory extract、embedding 都使用 `Map<String, Object>`。
- Python 侧部分请求也使用 `list[dict]` 等弱类型结构。

影响：

- 字段漂移无法编译期发现。
- 错误码、降级状态、模型信息无法稳定表达。
- 与项目整体“强类型契约”优化方向冲突。

优化方向：

1. 优先为 embedding 定义 Java DTO 和 Python Pydantic response。
2. 再为 memory extract 定义 DTO。
3. 最后治理 chat stream payload。
4. 保留兼容转换层，避免一次性破坏旧接口。

---

### 4.9 流式代理已迁移到统一 HTTP client

问题：

- 非流式 AI 调用使用统一 `RestTemplate`。
- 流式代理曾手写 `HttpURLConnection`，第一轮已迁移为注入的 `aiServiceRestTemplate.execute(...)`。

影响：

- 第一轮迁移后，backend stream proxy 已复用统一 request factory / timeout 配置，并保留原始 SSE 透传、header 传递和非 2xx 错误映射。
- metrics、trace、完整观测字段仍需在统一 client 层继续补齐。

优化方向：

1. 短期继续将 timeout、header、错误格式集中配置。
2. 中期按需要评估是否进一步迁移到专用 streaming HTTP client，例如 Spring `WebClient`。
3. 增加 stream 中断、非 200、半截 SSE 测试。

---

## P2：超时、限流和隐私问题

### 4.10 超时预算不一致

问题：

- Backend 非流式 AI timeout 默认约 60s。
- AI service LLM hard timeout 约 100s。
- Backend stream read timeout 约 300s。
- AI service stream idle timeout 约 20s。
- Embedding 部分路径没有统一使用配置 timeout。

影响：

- 可能出现上游仍运行、下游已超时的状态。
- 排障和重试困难。

优化方向：

1. 统一定义端到端 timeout budget。
2. 区分 connect timeout、first token timeout、idle timeout、total timeout。
3. Embedding 全路径使用统一配置。
4. LiteLLM embedding 增加 hard timeout。

---

### 4.11 内部 embedding 调用共享固定 user `-100`

问题：

- 内部服务 token 通过后，AI service 设置固定 user id 为 `-100`。
- embedding 限流和并发控制按 user 维度。

影响：

- 批量索引、重建任务、多用户写入会共享同一个限流桶。
- 一个后台任务可能影响所有内部 embedding 调用。

优化方向：

1. 内部调用携带 familyId、userId、jobType 或 sourceType。
2. 内部限流 key 改为 `internal:{jobType}:{familyId}` 等业务维度。
3. 后台任务和用户实时请求分开限流。

---

### 4.12 Web Search 可能泄露用户隐私

问题：

- Web Search 会把完整用户 query 发给外部搜索服务。
- 当前主要依赖启发式判断是否搜索。
- 如果用户消息同时包含私人信息和“查一下/最新/现在”等触发词，私人内容可能外发。

影响：

- 家庭、日记、照护、健康等私密内容可能进入外部搜索服务。

优化方向：

1. 搜索前做 query rewrite。
2. 去除姓名、手机号、地址、家庭关系、日记片段等 PII。
3. 对高隐私 query 默认禁搜或要求确认。
4. 明确日志中不记录原始隐私 query。

---

## P3：结构化输出和测试问题

### 4.13 AI JSON Schema 不够严格

问题：

- memory save-plan、organized draft、persona material draft schema 没有 `additionalProperties: false`。
- 枚举、字符串长度、数值范围、数组长度约束不足。

影响：

- LLM 可返回额外字段或异常值。
- sanitizer 压力过大，契约不稳定。

优化方向：

1. schema 增加 `additionalProperties: false`。
2. 补充 enum、min/max、maxLength、maxItems。
3. sanitizer 保留为第二道防线。
4. 增加 malformed output 测试。

---

### 4.14 AI 失败路径测试不足

问题：

当前测试主要覆盖 happy path，缺少：

- AI 返回非 200。
- 连接超时。
- read timeout。
- stream 中途断开。
- Resilience4j retry/fallback 是否触发。
- embedding 维度异常。
- degraded embedding 不应写 READY。
- stream error 不应当成正常回答。

优化方向：

1. 增加 `AIServiceClientTest` 故障注入。
2. 增加 AI service pytest 覆盖 auth、embedding、stream、web search 隐私逻辑。
3. 增加 backend memory embedding 状态流转测试。
4. 增加契约测试，固定 AI response schema。

---

## 5. 分阶段计划

## Phase 1：AI 安全边界止血

状态：已完成第一轮

时间：2026-06-24 到 2026-07-01

目标：先关闭最明显的 prompt injection 和认证 fail-open 风险。

工作项：

1. 限制 `history.role` 白名单。已完成：Backend DTO 和 AI service Pydantic model 均只允许 `user` / `assistant`。
2. AI service 为 chat request 定义强类型 Pydantic model。已完成：新增 `AgentHistoryMessage`。
3. 安全检查不再信任客户端 role。已完成：LLM message 校验只允许首条可信 `system` 消息，拒绝后续 `system` / 未知 role。
4. `memory_context` 改为服务端可信来源，或短期加注入检测与数据边界。已完成短期止血：增加注入检测、上下文 delimiter 和“数据不是指令”的 prompt 边界；长期仍需迁移为纯服务端生成。
5. AI service 生产环境默认 fail-closed。已完成：dev/local 下也默认 fail-closed，只有显式 `AUTH_FAIL_OPEN=true` 才 fail-open。

验收标准：

- 客户端传 `role=system` 返回 400 或被过滤。已验证：后端 DTO 和 AI service 模型已拒绝系统历史角色。
- prompt injection 文本即使伪装在 history 中也会被检测。已部分验证：safety_limits 已拒绝未知 role 和后续 system 消息；后续仍需扩展更多注入样本测试。
- 生产环境未显式开启时不允许 auth fail-open。已完成：默认关闭。
- `memory_context` 不再被无边界地当成高可信 system 内容。已完成第一轮止血：加入注入检测和 prompt 边界，后续仍需迁移来源。

---

## Phase 2：Embedding 质量保护

状态：已完成核心止血，进入契约增强阶段

时间：2026-07-01 到 2026-07-08

目标：防止错误或降级向量污染长期记忆索引。

工作项：

1. Embedding response 增加 `degraded`、`provider`、`model`、`dimensions`、`errorCode`。已完成第一轮：成功响应已增加 `degraded=false`、`model`、`dimensions`、`privacy_categories`；失败路径改为显式 503。
2. provider 失败时不返回伪成功 hash embedding。已完成：外部 provider 失败不再回退为 hash embedding，直接返回 503。
3. Backend 校验 embedding 维度、有限数值和 degraded 状态。已完成第一轮：backend 已校验 degraded、维度和有限数值；后续可继续收敛为强类型契约。
4. 降级/失败时不标记 `READY`。已完成第一轮：AI service 不再伪造成功，backend 会进入 FAILED 分支。
5. 补充 embedding 失败、维度异常、降级状态测试。已完成第一批。

验收标准：

- provider 失败不会写入 READY 向量。已验证：AI service provider 失败返回 503，backend 失败分支不会写 READY。
- 维度错误向量会被拒绝。已验证：backend 维度不等于 1536 时标记 FAILED。
- degraded embedding 有显式状态和日志。已完成第一轮：成功响应带 `degraded=false`，backend 拒绝 `degraded=true`；后续可补 `errorCode` / `provider` 字段。
- 召回查询可识别 embedding 失败并走明确降级路径。已完成第一轮：召回 query embedding 失败、degraded、维度错误或非有限值时走文本 fallback。

---

## Phase 3：AI 失败语义统一

状态：已完成第一轮流式失败语义止血，后续可继续补前端展示与 Resilience4j 场景化测试

时间：2026-07-08 到 2026-07-15

目标：让 AI 调用失败可见、可观测、可测试。

工作项：

1. 流式接口增加结构化 error event。已完成第一轮：AI service 和 backend fallback 均输出 `type=error`、`code`、`message`、`retryable`、`degraded=false`。
2. 区分 `done`、`error`、`degraded`。已完成第一轮：AI service 成功结束输出 `type=done` + `done=true` + `degraded=false`，失败输出 `type=error`；embedding fallback 继续显式 `degraded=false`。
3. `AIServiceClient` transport failure 不再内部吞异常。已完成第一轮：embedding / memory extraction transport failure 抛出业务异常，交由 retry/circuit breaker/fallback 统一处理。
4. Resilience4j fallback 统一返回明确业务错误。已完成第一轮：非流式 fallback 返回 `success=false` + `errorCode=AI_SERVICE_UNAVAILABLE`，流式 fallback 返回结构化 SSE error event。
5. 增加 stream 失败、非 200、中断、超时测试。已完成第一批：AI service 覆盖 stream 成功/失败 event，backend 覆盖 stream 非 200 不伪装为 assistant 文本；后续可补中断和超时专项测试。

验收标准：

- LLM provider 失败不会被当作普通 assistant 回答。已完成：LLM stream fallback 不再 yield 道歉文本，最终由结构化 error event 暴露。
- 前端/后端能识别 error stream event。已完成后端/AI service 契约；前端展示仍可继续增强。
- Resilience4j retry/fallback 测试可证明生效。已完成 fallback 契约收敛，后续可补带 Spring AOP 的 retry/circuit breaker 集成测试。
- 日志中可区分 transport failure、provider failure、business failure。已部分完成：backend transport failure 日志已单独标识，AI service provider failure 仍保留 LLM/embedding 日志；后续可进一步标准化字段。

---

## Phase 4：AI 契约强类型化

状态：已完成第一轮契约增强，chat stream event schema 和更深层 memory response Pydantic 仍待继续收敛

时间：2026-07-15 到 2026-07-29

目标：减少 backend 与 AI service 之间的裸 `Map` 契约。

工作项：

1. 优先强类型化 embedding request/response。已完成第一轮：AI service 新增 `EmbedResponse` Pydantic response model 并返回 `provider`；backend 新增 `EmbeddingRequest` / `EmbeddingResponse` DTO，`AIServiceClient.embedText()`、记忆索引和向量召回查询均改用强类型契约。
2. 强类型化 memory extraction request/response。已完成第一轮：backend `MemoryExtractionRequest` / `MemoryExtractionResponse` DTO 已新增，`AIServiceClient.extractMemories()` 已切换为强类型契约；AI service 侧仍使用过渡型 request model。
3. 为 chat stream request 定义稳定 DTO/Pydantic schema。已部分完成：backend `AgentChatStreamRequest` 和 AI service `AgentChatRequest` 已约束 history role 与主要字段，后续可继续收敛 stream event schema。
4. JSON schema 增加严格约束。已完成第一轮：save-plan、organized draft、persona material draft 和 weekly report schema 已开启 `strict` / `additionalProperties: false`，并补充 enum、长度、数组数量和数值范围约束。
5. 保留兼容转换层，避免破坏现有调用。

验收标准：

- AIServiceClient 关键方法不再以裸 Map 作为主契约。已部分完成：embedding 主链路已强类型化；memory extraction client 已新增强类型 request/response DTO；chat stream proxy 仍保留 Map 过渡。
- Python AI service 入参/出参有 Pydantic 类型约束。已部分完成：chat request 和 embedding response 已有 Pydantic 类型约束，memory LLM response schema 已严格化。
- schema 禁止无关额外字段。已完成第一轮：核心 memory LLM schema 已添加 `additionalProperties: false` 并通过契约测试固定。
- 契约测试覆盖核心 response。已部分完成：embedding provider metadata、强类型响应解析、降级/异常向量拒绝已有测试覆盖；memory response schema 严格模式和 backend memory extraction typed response 已增加测试。

---

## Phase 5：隐私、限流、超时和观测治理

状态：已完成第一轮 Web Search 隐私治理、内部 embedding 限流身份治理、前端 stream error 解析增强和 backend stream HTTP client 封装迁移；timeout budget 和完整观测字段仍待继续

时间：2026-07-29 到 2026-08-12

目标：补齐 AI 外部依赖治理能力。

工作项：

1. Web Search 增加 query rewrite / PII stripping。已完成第一轮：外部搜索前会改写公开 query、移除常见 PII；含手机号、地址、家庭记忆等隐私标记的 query 默认跳过外部搜索，并补充回归测试。
2. 内部 embedding 限流从固定 `-100` 改成业务维度。已完成第一轮：AI service `EmbedRequest` 接收 `source_type` / `family_id` / `user_id`，内部限流 key 改为 `internal:{source_type}:family:{family_id}:user:{user_id}`。
3. 统一 AI timeout budget。已部分完成：embedding LiteLLM / DashScope 调用统一使用 `ai_embedding_timeout_seconds`；整体端到端 timeout budget、backend stream timeout 和文档化配置仍待继续。
4. stream 代理迁移或封装到统一 HTTP client。已完成第一轮：backend stream proxy 从手写 `HttpURLConnection` 迁移到注入的 `aiServiceRestTemplate.execute(...)`，复用统一 timeout / request factory 配置，并保留原始 SSE frame 透传与非 2xx 结构化失败语义。
5. 增加 metrics/log 字段：provider、model、latency、degraded、errorCode、requestId。已部分完成：embedding response 已有 provider/model/dimensions/degraded；完整 latency、errorCode、requestId 和结构化日志/metrics 仍待继续。

验收标准：

- 私密 query 不会原样发给外部搜索服务。已完成第一轮并通过测试：隐私 query 会被跳过，公开 query 会先改写再发送。
- 后台 embedding 任务不会全部共用一个 user 限流桶。已完成第一轮：内部 embedding 限流使用 source/family/user 业务维度。
- AI 链路 timeout 有统一配置说明。已部分完成：embedding provider timeout 已收敛；全链路 timeout budget 仍待继续。
- stream 与非 stream 链路的观测和错误语义一致。已部分完成：前端可正确解析 typed stream error / metadata event；backend stream 代理已复用统一 RestTemplate client 并保留非 2xx 业务异常语义；完整观测字段仍待继续。

---

## 6. 测试与验证要求

每个阶段最少包含以下验证：

1. 单元测试：
   - role 白名单。
   - memory context 注入检测。
   - embedding response 校验。
   - JSON schema malformed output。

2. 集成测试：
   - backend 调 AI service 非 200。
   - timeout / retry / fallback。
   - stream 中途断开。
   - provider 失败不写 READY 向量。

3. 安全回归：
   - 客户端不能传 system role。
   - 客户端不能伪造授权记忆上下文。
   - 生产环境 auth fail-open 默认关闭。

4. 隐私验证：
   - Web Search 不发送包含手机号、地址、家庭成员真实描述的完整 query。
   - 日志不落原始敏感 query。

5. 契约验证：
   - Java DTO 与 Python Pydantic response 字段一致。
   - 旧调用在过渡期仍兼容。
   - 前端 stream error 处理不漂移。

---

## 7. 不做事项

本轮 AI 优化明确不做以下事情，除非单独立项：

1. 不更换整体 LLM provider 架构。
2. 不一次性重写所有 Prompt。
3. 不把 Python AI service 拆成多个微服务。
4. 不为了强类型化一次性破坏旧 API。
5. 不引入复杂工作流引擎替代当前调用链。
6. 不在未完成隐私治理前扩大 Web Search 使用范围。

---

## 8. 里程碑

| 里程碑 | 日期 | 结果 |
| --- | --- | --- |
| M1 | 2026-07-01 | 关闭 history system role、memory context 注入和生产 auth fail-open 风险 |
| M2 | 2026-07-08 | Embedding 失败不再污染 READY 索引 |
| M3 | 2026-07-15 | 流式和非流式 AI 失败语义统一 |
| M4 | 2026-07-29 | 完成第一批 AI 契约强类型化 |
| M5 | 2026-08-12 | 完成 Web Search 隐私、内部限流、timeout 和观测治理 |

---

## 9. 成功标准

满足以下条件，可认为本轮 AI 优化有效：

1. 用户可控输入不能提升为 system 指令。
2. 授权记忆上下文只来自服务端可信检索结果。
3. AI provider 失败不会被伪装成普通成功回答。
4. Embedding 降级或失败不会污染长期向量索引。
5. Backend 与 AI service 的核心契约有强类型 DTO/Pydantic schema。
6. Web Search 不会把家庭隐私原样发送给外部服务。
7. AI 链路的 timeout、retry、fallback、error event 可测试且可观测。
8. 新增 AI 功能遵守 `AGENTS.md` 中关于强类型、失败路径、外部依赖和隐私边界的规则。
