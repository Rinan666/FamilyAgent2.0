# FamilyAgent 需求-功能映射矩阵

> 版本：V1.0  
> 用途：把产品需求与当前代码基线进行映射，区分“已实现”“部分实现”“规划中”  
> 更新时间：2026-07-02

---

## 1. 使用说明

本文档用于把 FamilyAgent 的需求分析与当前实现基线对应起来，便于：

- 判断需求是否已有落地支撑
- 识别哪些能力只是方向而非现状
- 后续排定版本优先级
- 统一产品、设计、开发和论文文档口径

状态定义：

- **已实现**：代码中已有明确能力支撑
- **部分实现**：已有基础能力，但产品形态或闭环仍不完整
- **规划中**：需求方向明确，但当前基线不应宣称已完成

---

## 2. 映射矩阵

| 需求主题 | 需求说明 | 当前实现状态 | 主要实现载体 | 备注 |
|---|---|---|---|---|
| 家族精神财富沉淀 | 保存对自己和后代有价值的经历、洞见、价值观与策略 | 已实现 | 记忆库、写入入口、AI 保存规划 | 当前已具备基础沉淀框架 |
| 高价值内容保存 | 支持人工保存与 AI 辅助判断后保存 | 已实现 | `save_memory` 技能、写入接口、前端保存流程 | AI 保存为确认式，不是全自动 |
| 家族记忆库 | 支持搜索、浏览、归档、恢复、删除、可见性维护 | 已实现 | Memory Library 前后端 | 是当前基线的重要中枢 |
| 家庭对话上下文召回 | 家庭 Agent 对话时可调取家庭授权记录 | 已实现 | `useChat`、family recall、memory controller | 当前召回以家庭记录为主 |
| 流水近况沉淀 | 保存最近一周、一月、一年的阶段性或流水状态 | 部分实现 | diary / memory / visibility / recall | 记录能力已有，但无独立近况流 |
| 家族近况共享 | 家族成员可在权限控制前提下了解彼此近况 | 部分实现 | visibility + recall + mirror | 更像能力组合，不是专门 feed |
| 迷茫时获取家族参考 | 在迷茫、选择、反思时获得家族内部参考 | 已实现 | 家庭对话 + recall + 已沉淀记录 | 质量依赖记录质量 |
| 成员镜像模式 | 基于真实成员授权记录进行视角模拟 | 已实现 | mirror API、mirror prompt、agent page | 不等同于真人本人 |
| 精神成员管理 | 创建、编辑、删除精神成员 | 已实现 | persona member 前后端 | 已形成 CRUD 闭环 |
| 精神成员材料整理 | 粘贴材料 -> AI 草稿 -> 编辑确认 -> 保存 | 已实现 | persona material draft + material APIs | 原始长文本不直接落库 |
| 精神成员对话 | 在聊天中切换为 persona 模式进行视角回应 | 已实现 | agent page + persona mode | 当前强调忠于设定 |
| 忠于设定的角色表达 | 精神成员尽量稳定遵循设定与材料 | 部分实现 | prompt + persona profile + materials | 是设计目标，非绝对保证 |
| 深度理解家族成员 | 非面对面情况下深入理解彼此 | 部分实现 | 近况、记录、镜像、共享 | 当前更适合表述为“增进理解” |
| AI 主动识别和推荐 | 主动发现高价值内容并建议保存或推荐参考 | 规划中 | 暂无完整主动链路 | 当前仍以用户触发为主 |
| 长期精神财富再加工 | 把精彩回答再沉淀、再组织、再利用 | 规划中 | 现有体系未形成完整闭环 | 可作为下一阶段方向 |
| 权限化家族共享 | 基于可见范围与授权边界共享内容 | 已实现 | visibility、backend filtering | 当前口径必须强调权限边界 |
| 部署运行基线 | 系统具备可部署运行能力 | 已实现 | stack、CI、health 基础 | 可部署，不等于完全成熟运营 |
| 运维成熟度与健康观测 | 更深的 readiness、观测、告警能力 | 部分实现 | health endpoint、actuator 基础 | 仍需继续完善 |

---

## 3. 关键实现映射

### 3.1 前端

- `frontend/src/app/(dashboard)/dashboard/agent/page.tsx`
- `frontend/src/app/(dashboard)/dashboard/memory-library/page.tsx`
- `frontend/src/components/memory-library/MemoryLibraryWorkbench.tsx`
- `frontend/src/components/family/PersonaMembersPanel.tsx`
- `frontend/src/hooks/useChat.ts`

### 3.2 后端

- `backend/src/main/java/com/familyagent/module/memory/controller/WriteMemoryController.java`
- `backend/src/main/java/com/familyagent/module/mirror/controller/MirrorContextController.java`
- `backend/src/main/java/com/familyagent/module/family/controller/FamilyPersonaMemberController.java`
- `backend/src/main/java/com/familyagent/module/memorylibrary/controller/MemoryLibraryController.java`

### 3.3 AI 服务

- `ai-service/app/agents/family_skill_registry.py`
- `ai-service/app/llm/prompts/mirror.py`
- `ai-service/app/api/memory.py`
- `ai-service/app/api/memory_contracts.py`

---

## 4. 使用建议

建议结合以下文档一起使用：

- `docs/product/FAMILYAGENT_REQUIREMENTS_ANALYSIS.md`
- `docs/product/FAMILYAGENT_REQUIREMENT_REVISIONS.md`
- `docs/plans/SPIRITUAL_MEMBER_PLAN.md`
- `README.md`

这几份文档的职责建议如下：

- **需求分析文档**：说明产品为什么存在、解决什么问题、当前边界是什么
- **需求修订清单**：统一口径，修正容易说过头的表达
- **映射矩阵**：把需求和代码基线连接起来
- **README**：对外或对项目整体进行简明介绍
