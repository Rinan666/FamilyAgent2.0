# FamilyAgent

## 项目定位

FamilyAgent 是一个以**个人与家族真实人生经验**为基础的 AI 系统，具有两层核心价值：

- **对个人，它是决策与生活辅助工具**：帮助用户认识自己、理解处境，并从个人与家族成员经历过的选择、代价、结果和复盘中获得思考燃料、行动参考与可效仿的人生样本。
- **对家族，它是精神财富传承工具**：持续沉淀成员的真实经历、价值判断、处世经验和成长故事，使这些内容能够按权限共享、长期保存，并被当代成员与后人继续理解和调用。

系统可以结合授权记录给出有依据的倾向性建议，但不会替用户承担最终决定；它保存和传承的也不是脱离场景的人生道理，而是道理背后的真实处境、判断过程、行动代价和后续复盘。

FamilyAgent 重点解决三个问题：

1. **辅助决策与生活**：在迷茫、选择、关系和成长场景中，召回相似的个人与家族经历，说明哪些经验值得参考、哪些地方不能照搬。
2. **了解自己与家人**：通过持续积累的真实记录，帮助成员理解彼此的近况、价值排序、判断方式和成长变化。
3. **离线成员理解**：当真实成员不在线时，用户可以与其镜像对话，从授权资料中了解对方可能的视角和表达方式。

精神成员是用户自建的补充榜样和扩展视角；记录整理、确认式保存、复盘提醒与 Action Agent 能力用于支撑长期积累，不是产品本身的第一定位。

---

## 当前产品基线

当前代码基线已经形成以下核心能力：

1. **账号、家庭与权限底座**
   - 支持邀请制注册、登录、资料和密码管理
   - 注册邀请码与家庭邀请码相互独立；登录用户可创建或加入家庭
   - 支持家庭成员、所有权转移、关系称谓、照护授权和强确认删除

2. **个人与家族经验库**
   - 个人记忆和家族记录使用不同的所有权与共享规则
   - 支持搜索、浏览、编辑、归档、恢复、删除和可见范围维护
   - 支持手动录入，以及用户明确触发后的 AI 草稿、编辑和确认保存

3. **授权上下文对话**
   - 支持持久 Agent 会话，以及家庭 Agent、真实成员镜像和精神成员目标
   - 当前提供 Quick / Think 模式；Think 可使用权限优先的 RAG 和受限公共 Web Search
   - 支持展示回答所使用的记录与网络来源，并区分原始记录、经验与模型推断

4. **真实成员镜像**
   - 基于授权记录生成某位家族成员的视角模拟
   - 用于帮助理解其可能的表达方式、价值排序与判断习惯
   - **镜像不等同于该成员本人，也不能替代其作出现实承诺**

5. **精神成员模式**
   - 家庭成员可以查看和选择精神成员；家庭所有者负责创建、编辑、删除和材料维护
   - 每个家庭最多维护 3 个精神成员，AI 材料整理只生成可编辑草稿
   - 作为真实个人与家族经验之外的补充榜样和扩展视角

6. **AI 整理、确认与追溯**
   - 当前保存流程由用户明确触发，不会在普通聊天后自动落库
   - 写入动作通过权限、确认、幂等、审计和 provenance 链路执行
   - Agent run、工具调用、确认和来源关联可用于回放与诊断

7. **平台管理与运行基线**
   - 平台管理员可查看数据库、Embedding、会话和 SkillRun 健康摘要，并执行受限 RAG 诊断
   - Backend 是家庭数据、权限、召回、写入和审计的业务权威
   - 当前安全措施是生产导向的工程基线，不代表合规认证或绝对安全保证

---

## 产品演进方向

- 保持一个统一 AI 对话入口，通过自然语言切换家庭上下文、真实成员镜像和精神成员。
- 取消要求用户选择“快速 / 思考”等模型模式，由系统根据意图决定回答深度、召回深度和联网策略。
- 使用统一记录进行全局授权召回，不再按日记、记忆和成长观察分配固定召回名额。
- 为决策场景提供可追溯的家族参考，包括处境、选择、代价、结果、复盘和与当前问题的差异。
- 将 Action Agent 定位为整理、保存、复盘和提醒的支撑能力，而不是通用家庭事务执行器。

---

## 产品边界

为避免误解，当前版本需要明确以下边界：

- FamilyAgent 提供思考燃料和倾向性建议，但最终决定始终由用户作出
- 家族经验可能片面、过时或不适用于当前处境，不能被包装成绝对正确答案
- 镜像模式是**基于授权资料的视角模拟**，不是本人身份延续
- 精神成员是**基于设定与材料的补充视角**，不是权威导师或事实真人
- AI 保存能力当前由用户明确触发，并以**可编辑草稿和确认式保存**为主，不是完全自动落库
- 公共 Web Search 只在符合条件的 Think 请求中使用，不会把家庭私密记录直接作为搜索 query，也不保证信息必然最新或权威
- 回答依据表示生成时使用的来源，不代表每一句模型输出都被来源逐字证明
- 系统旨在帮助成员增进理解，不保证能够完整、无偏差地还原任何人的真实想法
- 当前基线以**用户触发**为主，主动式 AI 推荐、提醒与长期任务属于后续演进方向
- 当前安全措施是工程基线，不代表合规认证、绝对安全或无需持续运维

---

## 文档导航

### 项目主文档

- 产品定位与能力基线：[docs/product/产品定位.md](docs/product/产品定位.md)

### 其它说明

- docs 目录导航：`docs/README.md`
- 部署文档：`docs/deployment/docker-stack.md`

---

## 本地启动（Docker Compose）

本地完整应用栈统一由 Docker Compose 启动，包括 PostgreSQL、Redis、RabbitMQ、MinIO、AI Service、后端和前端。生产环境通过 `deploy/compose/production.yml` 额外启用 Cloudflare Tunnel，并保持内部服务不直接暴露。宿主机不需要分别安装 Node.js、Java、Python。仓库不再提供 PowerShell 启动器，Docker Compose 是唯一标准启动入口。

### 1. 准备环境变量

在项目根目录准备 `.env`。首次配置可以参考 `.env.example`，并至少确认数据库、Redis、RabbitMQ、MinIO、内部服务令牌和 AI Provider 等配置。

`COMPOSE_PROJECT_NAME` 必须与当前环境已有的 Compose 项目名保持一致。本机现有环境使用 `familyagent`；云服务器如果已有 `fa-*` 容器，应保持：

```env
COMPOSE_PROJECT_NAME=fa
```

不要直接修改已有服务器的项目名，否则 Docker 会把它识别为另一套应用，可能创建重复容器并产生端口冲突。项目名只决定 Compose 对容器的归属，PostgreSQL、Redis、RabbitMQ 和 MinIO 仍复用显式命名的 `fa_*` 数据卷。

生产环境使用 Cloudflare Tunnel 时，还需要配置命名隧道 ID 和凭据文件的绝对路径：

```env
CLOUDFLARED_TUNNEL_ID=00000000-0000-0000-0000-000000000000
CLOUDFLARED_CREDENTIALS_FILE=C:/Users/your-name/.cloudflared/00000000-0000-0000-0000-000000000000.json
```

凭据文件只读挂载到容器，不会复制到镜像中。

### 2. 构建并启动

```powershell
docker compose --env-file .env -f compose.yml -f deploy/compose/local.yml config --quiet
docker compose --env-file .env -f compose.yml -f deploy/compose/local.yml up -d --build
```

首次构建需要下载 Maven、npm 和 Python 依赖，后续构建会复用 Docker 缓存。日常启动无需重新构建：

```powershell
docker compose --env-file .env -f compose.yml -f deploy/compose/local.yml up -d
```

### 3. 检查状态

```powershell
docker compose --env-file .env -f compose.yml -f deploy/compose/local.yml ps
docker compose --env-file .env -f compose.yml -f deploy/compose/local.yml logs --tail=200
```

默认访问入口：

- 前端：`http://localhost:3000`
- 后端健康检查：`http://localhost:8080/actuator/health`
- AI 健康检查：`http://localhost:8000/ai/health/ready`
- AI 文档：`http://localhost:8000/docs`
- RabbitMQ 管理界面：`http://localhost:15672`
- MinIO 控制台：`http://localhost:9001`
- 已配置生产环境时的公网入口：`https://app.familyagent.cn`

### 4. 停止

```powershell
docker compose --env-file .env -f compose.yml -f deploy/compose/local.yml down
```

停止命令默认保留 PostgreSQL、Redis、RabbitMQ 和 MinIO 数据卷。更完整的生产配置与故障排查说明见 `docs/deployment/docker-stack.md`。
