# FamilyAgent AI 项目学习路线图

这份路线图的目标不是让你马上成为全栈专家，而是先让你具备三种能力：

- 能看懂项目由哪些部分组成。
- 能判断问题大概出在哪一层。
- 能验收 AI 生成或修改的代码是否可靠。

核心原则：先掌握项目，再学习技术；先能排查小问题，再追求系统性理解。

---

## 阶段 0：建立项目地图

建议时间：1-2 天

### 目标

知道项目由哪些部分组成，每一部分大概负责什么。

### 需要掌握

```text
frontend     前端页面和用户交互
backend      Java 业务后端，负责权限、数据、核心业务
ai-service   Python AI 服务，负责大模型、记忆生成、AI 编排
PostgreSQL   主数据库
Redis        缓存、登录状态、锁
RabbitMQ     异步任务
MinIO        文件、照片对象存储
```

### 重点目录

```text
frontend/src/app                         前端页面
frontend/src/components                  前端组件
frontend/src/lib/api                     前端 API 封装
frontend/src/types                       前端类型

backend/src/main/java/com/familyagent/module
                                         Java 后端业务模块
backend/src/main/resources/db/migration  数据库迁移脚本
backend/src/main/resources/application*.yml
                                         后端配置

ai-service/app/api                       AI 服务接口
ai-service/app/llm                       大模型调用
ai-service/app/services                  AI 业务逻辑
ai-service/app/main.py                   AI 服务入口
```

### 练习任务

- 打开项目根目录，熟悉 `frontend`、`backend`、`ai-service`。
- 找到前端 API 目录：`frontend/src/lib/api`。
- 找到后端模块目录：`backend/src/main/java/com/familyagent/module`。
- 找到 AI 接口目录：`ai-service/app/api`。
- 找到数据库迁移目录：`backend/src/main/resources/db/migration`。

### 阶段成果

你能用 2 分钟讲清楚这个项目由哪几块组成。

---

## 阶段 1：会启动、会看错误

建议时间：3-5 天

### 目标

项目出问题时，先判断是哪一层坏了。

### 需要掌握

- 怎么启动前端、后端、AI 服务、基础设施。
- 每个服务的端口。
- 浏览器 Console 和 Network 怎么看。
- 后端日志怎么看。
- AI 服务日志怎么看。
- 常见错误码是什么意思。

### 常见端口

```text
3000   前端
8080   后端
8000   AI 服务
5432   PostgreSQL
6379   Redis
5672   RabbitMQ
15672  RabbitMQ 管理后台
9000   MinIO API
9001   MinIO 控制台
```

### 常见错误

```text
401  没登录或 token 失效
403  没权限
404  接口路径不对
500  服务端异常
Connection refused  服务没启动或地址错
timeout  AI、数据库、网络或队列太慢
```

### 练习任务

- 打开一个页面，观察 Network 里的请求。
- 故意停掉 AI 服务，看看前端和后端分别怎么报错。
- 故意访问不存在的接口，看看 404。
- 看一次后端日志，找到异常栈最关键的一行。
- 看一次 AI 服务日志，找到请求入口和返回状态。

### 阶段成果

你能说出一个问题大概率在前端、后端、AI 服务、数据库，还是基础设施。

---

## 阶段 2：掌握一条完整功能链路

建议时间：1 周

### 目标

不再只看单个文件，而是能沿着一个功能从前端追到数据库或 AI。

### 推荐功能

优先选择下面三条之一：

```text
登录流程
AI 聊天流程
保存记忆流程
```

建议先从 AI 聊天流程开始，因为它最能代表这个项目。

### 通用追踪路线

```text
页面按钮 / 输入框
  -> frontend/src/lib/api/*
  -> Next.js 代理 /api 或 /ai-proxy
  -> backend Controller 或 ai-service API
  -> Service
  -> 数据库 / 模型 / Redis / RabbitMQ
  -> 返回前端展示
```

### 练习任务

- 找到页面发请求的地方。
- 找到对应的 API 封装。
- 找到后端或 AI 服务接口。
- 找到真正处理业务的 Service。
- 画一张简单流程图。

### 阶段成果

你能讲清楚“用户点击发送后，系统内部发生了什么”。

---

## 阶段 3：后端基础

建议时间：2 周

### 目标

看懂 Java 后端的主结构，能判断 AI 改得对不对。

### 需要掌握

```text
Controller          接收请求
Service             处理业务逻辑
Repository / Mapper 访问数据库
DTO / VO            请求和响应结构
Entity              数据库实体
Result<T>           统一返回结构
application.yml     配置
Flyway              数据库迁移
```

### 必须理解的工程规则

- 权限判断必须在后端做，不能只靠前端。
- 业务状态不要散落字符串，要用枚举或常量。
- 大 Service 不能无限堆逻辑。
- 外部调用 AI、Redis、RabbitMQ、MinIO 时要考虑失败。
- 新接口要同步前端 API 和类型。
- Repository 按接口注入。
- 不要跨模块乱调用，必要时用 Facade 隔离。

### 练习任务

- 找一个 Controller，看它调用哪个 Service。
- 找一个 Service，看它保存到哪张表。
- 找一个 DTO，看前端传了哪些字段。
- 找一个 Flyway SQL，看数据库表结构。
- 给已有接口加一个简单参数校验。

### 阶段成果

AI 修改后端代码时，你能看懂主要改动是否合理。

---

## 阶段 4：前端基础

建议时间：1-2 周

### 目标

能看懂页面、状态和接口调用。

### 需要掌握

- Next.js 页面结构。
- React 组件。
- TypeScript 类型。
- `frontend/src/lib/api/*` 里的请求封装。
- `frontend/src/types/index.ts` 类型定义。
- Zustand 状态管理。
- 表单提交、加载状态、错误提示。

### 练习任务

- 找一个页面组件。
- 找到它调用的 API。
- 看请求参数和响应数据。
- 修改一个按钮文案或加载状态。
- 新增一个简单字段展示。

### 阶段成果

前端出问题时，你能判断是页面状态问题、接口问题，还是数据结构不匹配。

---

## 阶段 5：AI 服务基础

建议时间：2 周

### 目标

理解 AI 服务不是黑盒，知道它负责什么、不负责什么。

### 需要掌握

- FastAPI 接口怎么定义。
- LiteLLM 为什么用于统一调用模型。
- Prompt 在哪里组织。
- AI 返回结构如何校验。
- SSE 流式输出是什么。
- embedding 和 pgvector 的作用。
- 为什么 AI 输出不能直接信任。

### 核心边界

```text
AI 负责生成、总结、判断、建议。
后端负责权限、数据事实、最终保存。
```

### 练习任务

- 找到一个 `ai-service/app/api/*.py` 接口。
- 找到它调用的 service。
- 找到模型调用位置。
- 看一次 AI 返回数据结构。
- 找一次失败处理或重试逻辑。

### 阶段成果

你能判断一个能力应该放在 AI 服务，还是 Java 后端。

---

## 阶段 6：数据库和数据流

建议时间：1-2 周

### 目标

知道数据在哪里、怎么变、谁有权改。

### 需要掌握

- PostgreSQL 是主事实库。
- Flyway 管理表结构变化。
- Redis 不存核心长期事实。
- RabbitMQ 处理异步任务。
- MinIO 存文件和照片。
- pgvector 用于相似度检索。

### 练习任务

- 看一张核心表结构。
- 找到对应 Entity。
- 找到对应 Mapper 或 Repository。
- 找到哪个接口会写入它。
- 画出一次数据写入流程。

### 阶段成果

你能回答“这个数据最终存在哪里”。

---

## 阶段 7：调试与验收能力

建议时间：长期训练

### 目标

从“让 AI 修”变成“让 AI 修，但我能验收”。

### 每次 AI 修 Bug 后都要问

```text
1. 问题原因是什么？
2. 修改了哪些文件？
3. 为什么这样改？
4. 怎么验证它真的好了？
```

### 自己验收时检查

- 是否破坏接口返回结构。
- 是否漏了权限校验。
- 是否新增魔法字符串。
- 是否把逻辑继续塞进大 Service。
- 是否漏了失败处理。
- 是否需要同步前端类型。
- 是否需要补测试。
- 是否引入跨模块直接调用。

### 阶段成果

你能对 AI 生成的代码说：“这里不合理，需要重改。”

---

## 推荐时间安排

```text
第 1 周：
项目结构 + 启动 + 看日志 + 看 Network

第 2 周：
完整追踪 AI 聊天流程

第 3-4 周：
学习 Java 后端 Controller / Service / Mapper / DTO

第 5 周：
学习前端页面 / API / 类型 / 状态

第 6-7 周：
学习 AI 服务 FastAPI / Prompt / LiteLLM / SSE

第 8 周：
学习数据库 / Redis / RabbitMQ / MinIO 数据流

第 9 周以后：
开始做小功能、小修复、小重构，并让 AI 解释每一步
```

---

## 每日小练习

每天只做一个小任务，20-40 分钟即可。

```text
周一：找一个页面对应的 API
周二：找一个 API 对应的后端 Controller
周三：找 Controller 调用的 Service
周四：找 Service 操作的表
周五：看一次浏览器 Network
周六：让 AI 带你读一个错误日志
周日：复盘这一周你掌握的一条功能链路
```

---

## 推荐起点：AI 聊天流程

第一条深入学习路线建议选择 AI 聊天流程。

原因：

- 它会经过前端页面。
- 它会经过前端 API 封装。
- 它会经过 Next.js 代理。
- 它会进入 AI 服务。
- 它可能涉及后端业务数据。
- 它会调用大模型。
- 它可能涉及流式响应、错误处理和记忆保存。

你可以让 AI 按下面这个方式辅助你学习：

```text
帮我从代码里追踪“AI 聊天流程”，从前端按钮/API 开始，到后端/AI 服务，再到模型调用和返回。
不要改代码，只解释每个文件的作用，并告诉我每一步应该观察什么。
```

这会让 AI 从“替你修代码的人”变成“带你理解项目的教练”。
