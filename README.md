# FamilyAgent（家族软资产传承 AI）

> 面向高净值家族的软资产管理与传承 AI 系统。

FamilyAgent 的核心目标不是和题库、拍照答疑、作业帮类产品竞争，而是帮助家族管理更难传承的“软资产”：家风、价值观、长辈经验、创业和投资教训、家庭关系、下一代成长观察和成员理解。

一句话来说：

**FamilyAgent 不是做更强的 AI 家教，而是做更懂这个家族的 AI。**

当前项目处于 **MVP 原型验证阶段**，已经具备在线体验能力，重点验证高净值家族是否认可“家族软资产传承 AI”这一方向。

## 当前产品方向

FamilyAgent 当前聚焦五个闭环：

- **家族日记**：成员记录日常、重要事件、成长观察、自我复盘和给家人的话，并按可见范围共享给家庭成员。
- **家族经验**：沉淀长者建议、家族故事、家风价值观、健康提醒、成长风险、创业和投资教训。经验支持可见范围控制，并用“适用场景”描述它适合什么阶段或场景。
- **镜像 Agent**：基于授权日记和家族经验做“风格参考、价值观参考、自我复盘辅助”。它不是本人，也不代表真实想法；记录不足时必须说明不确定。
- **家族陪伴 AI**：原“AI家教”已调整为家庭陪伴入口。它支持自由对话、学习陪伴和家庭记忆问答，并能在权限允许时参考学习记忆、家族经验、家族日记和成长守护安全摘要。
- **成长守护**：照护者可以记录体态、牙齿、视力、睡眠、运动、屏幕时间、情绪和沟通等观察，生成温和的每周成长提醒，并跟进“待观察 / 继续关注 / 已有改善 / 暂不跟进”状态。它现在作为家族日记的一种结构化模板和照护辅助能力。
- **隐私授权**：日记、照护记录、家族经验、镜像画像摘要进入 AI 前都必须经过后端权限过滤，称呼和家庭角色不能自动推断隐私访问权。

学习工具已从普通用户主入口降级。数学诊断、作业批改、错题本、学习报告和题库页面的代码暂时保留，用于兼容历史数据、管理员维护和后续“下一代成长信号”接入；但它们不再作为 FamilyAgent 的产品主线，也不再和作业帮、猿辅导等产品竞争解题效率。

当前不建议硬删除这些模块：它们仍可能为家族日记、成长守护和家庭陪伴 AI 提供辅助信号。真正需要删除或继续隐藏的，是那些要求用户围绕题库和练习建立使用习惯的入口、文案和流程。

## 核心设计

### 家族空间优先

系统以“家族空间”为主要业务单元。每个家族有自己的成员、经验、成长记录、镜像画像和报告，不同家族的数据互相隔离。面向高净值家族时，家族空间也可以承载私有化部署、高端定制、访谈整理和长期知识库建设。

### 身份、称呼和授权分三层

同一个账号在不同家族中可以有不同软件身份：

- `OWNER`：创建者
- `ADMIN`：家庭管理员
- `GUARDIAN`：照护者
- `MEMBER`：普通成员
- `STUDENT`：学习者
- `GUEST`：访客

前端会根据当前选中的家族 `activeFamilyId` 判断视图和入口。平台管理员 `users.role=ADMIN` 是系统能力，不等于自动获得所有家庭隐私访问权。

但这些软件身份不等于真实亲属关系。FamilyAgent 现在把家族关系拆成三层：

- **软件权限**：`family_members.role`，用于判断谁能管理成员、维护资源或查看授权内容。
- **视角称呼**：`family_relationships.from_user_id -> to_user_id`，表示“我怎么称呼 TA”，例如同一个人可以被不同成员称为“妈妈”“二姐”“小姨”。它只影响显示名和镜像 Agent 的视角表达，不产生权限。
- **照护授权**：`care_authorizations.subject_user_id -> caregiver_user_id`，用于表达谁被授权查看某个成员的照护类日记、成长守护、健康提醒或未成年人数据。当前已接入日记、家族经验召回和成长守护记录/报告，不能从“爸爸/妈妈/爷爷”等称呼自动推断。

因此，“创建者/管理员”是软件里的权限角色，不是现实家庭身份；“爸爸/妈妈/爷爷/孩子”是当前用户视角下的称呼，也不应被写死为全局枚举。

### 隐私与可见范围

家族经验和成长守护记录支持：

- `全家可见`
- `照护者可见`
- `仅自己可见`

成长守护记录面向学习者使用时不会原文暴露，只会被转为温和、泛化、非指责的安全摘要。

AI 服务在处理家庭上下文前会先经过轻量隐私守门：手机号、身份证号、邮箱、学校、班级和显式地址等信息会被自动替换为占位符，并提示模型不要还原或猜测。当前实现是规则版 `PrivacyGuard`，用于先建立稳定边界；后续可在同一接口下替换或增强为 Presidio。

镜像 Agent 的上下文召回遵循“后端先过滤权限，再做相关性召回”。当前已建立 `memory_embeddings` 表和授权召回服务；日记和家族经验创建后会异步生成 embedding 并写入 pgvector。召回时会优先在已授权候选记录内做向量排序，失败或缺少向量时自动回退到文本相关性。镜像画像摘要也已接入后端授权过滤，不能绕过日记、照护授权或家庭权限边界进入 AI 上下文。

## 技术栈

| 层 | 技术 |
|----|------|
| 前端 | Next.js 16 + React 19 + TypeScript + shadcn/ui + Tailwind CSS |
| 后端 | Java 17 + Spring Boot 3.3 + MyBatis-Plus + Sa-Token |
| AI 服务 | Python 3.12 + FastAPI + LiteLLM + sympy |
| 数据库 | PostgreSQL 16 + pgvector |
| 缓存 | Redis 7 |
| 消息队列 | RabbitMQ 3.13 |
| 对象存储 | MinIO |

## 架构概览

```text
Frontend (Next.js :3000)
  ├─ /api/*  -> Java Backend (:8080)
  └─ /ai/*   -> Python AI Service (:8000)

Backend
  ├─ 用户、家族、权限
  ├─ 家族日记、家族经验、成长守护
  ├─ 授权记忆召回、embedding 索引与 pgvector 排序
  ├─ 会话、学习信号、后台资源维护
  └─ PostgreSQL / Redis / MinIO

AI Service
  ├─ 家庭陪伴 AI / 学习陪伴
  ├─ 学习过程整理与辅助讲解
  ├─ AI 前隐私脱敏
  ├─ 镜像 Agent 上下文整理
  ├─ 家族经验卡整理
  └─ 成长守护周报
```

## 快速开始

### 一键启动（Windows）

双击项目根目录下的：

```text
start-all.bat
```

一键启动会依次启动：

1. Docker 基础设施
2. AI 服务
3. 后端服务
4. 前端服务
5. Cloudflare Tunnel（如果本机已配置 `cloudflared`）

当前 Docker 容器启动后的固定等待时间为 **5 秒**。

启动后访问：

- 本地前端：http://localhost:3000
- 后端接口：http://localhost:8080
- AI 文档：http://localhost:8000/docs
- 公网入口：https://familyagent.cn

停止服务：

```text
stop-all.bat
```

### 本地测试数据

项目提供一组 `FAMILY003` 种子数据，用于测试家族日记、权限共享、家族经验、成长守护和镜像 Agent。

执行方式：

```powershell
docker cp scripts/seed-family003.sql fa-postgres:/tmp/seed-family003.sql
docker exec fa-postgres psql -U fa_user -d familyagent -f /tmp/seed-family003.sql
```

这份脚本可重复执行，会更新同名测试用户和家族，并重建该种子家族下的模拟日记、经验和守护数据。

测试家族：

- 家族名称：`林陈家族测试空间`
- 家族邀请码：`FAMILY003`

测试账号密码统一为：`Test@123456`

| 用户名 | 昵称 | 家族身份 | 用途 |
|--------|------|----------|------|
| `family003_grandpa` | 李明德 | `OWNER` | 创建者、长者经验、镜像参考 |
| `family003_father` | 陈远航 | `ADMIN` | 家庭管理员、体态/运动观察 |
| `family003_mother` | 林秋然 | `GUARDIAN` | 照护者、成长守护记录 |
| `family003_aunt` | 陈知微 | `MEMBER` | 普通成员、兴趣观察 |
| `family003_student` | 陈一诺 | `STUDENT` | 学习者、自我日记和镜像测试 |

种子内容：

- 家族日记：9 条
- 家族经验 / 记忆：6 条
- 成长守护记录：3 条
- 成长守护周报：1 条
- 镜像画像：5 条

### 手动启动

1. 启动基础设施

```bash
docker-compose up -d
```

2. 启动 AI 服务

```bash
cd ai-service
pip install -r requirements.txt
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000
```

3. 启动后端

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

## 项目结构

```text
FamilyAgent/
├── backend/          Java Spring Boot 业务服务
├── ai-service/       Python FastAPI AI 服务
├── frontend/         Next.js 前端
├── docs/             项目文档
├── scripts/          迁移脚本与开发工具
├── start-all.bat     Windows 一键启动入口
├── stop-all.bat      Windows 一键停止入口
└── docker-compose.yml
```

## 常用命令

后端测试：

```bash
cd backend
mvn test
```

AI 服务测试：

```bash
cd ai-service
python -m pytest tests/ -v
ruff check app/
```

前端检查：

```bash
cd frontend
npm run lint
npx tsc --noEmit
npm run build
```

## 文档

当前主文档：

- [最新推进目标](docs/NEXT.md)
- [商业计划](docs/商业计划.md)
- [项目计划](docs/项目计划.md)
- [项目介绍：给老师](docs/项目介绍-给老师.md)
- [系统架构与接口契约](docs/系统架构与接口契约.md)
- [长期规划](docs/长期规划.md)

## 环境变量

复制 `.env.example` 到 `.env` 并按需填写配置：

```bash
cp .env.example .env
```

常见配置文件：

- `.env`：基础设施配置
- `frontend/.env.local`：前端代理与服务地址
- `ai-service/.env`：LLM API Key、后端地址、基础设施地址

## License

私有项目，保留所有权利。
