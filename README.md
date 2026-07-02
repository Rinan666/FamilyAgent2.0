# FamilyAgent

## 项目定位

FamilyAgent 是一个面向**家族传承与理解**的 AI 系统。

它的目标不是做通用聊天工具，也不是继续沿着传统家教产品扩展，而是围绕：

- 共同记忆
- 个人近况
- 成长记录
- 经验传承
- 精神财富沉淀

建立长期可积累的数据与服务闭环。

FamilyAgent 的核心价值，不是提供泛化建议，而是把家族成员的真实经历、近况、价值观、看法与洞见沉淀为可持续保存、按权限共享、可在对话中调用的精神资产，帮助成员在迷茫、选择和成长场景中获得更真实、更贴近、更可代入的参考。

---

## 当前产品基线

当前代码基线已经形成以下核心能力：

1. **家族记忆沉淀**
   - 支持保存记忆、日记、成长记录等内容
   - 支持手动保存与 AI 辅助判断后确认保存

2. **家族记忆库**
   - 支持搜索、浏览、归档、恢复、删除和可见范围维护

3. **家庭 Agent 对话**
   - 可在家庭范围内召回授权记录，作为对话上下文

4. **真实成员镜像**
   - 基于授权记录生成某位家族成员的视角模拟
   - 用于帮助理解其可能的表达方式、价值排序与判断习惯
   - **镜像不等同于该成员本人，也不能替代其作出现实承诺**

5. **精神成员模式**
   - 支持创建、编辑、删除精神成员
   - 支持材料整理与 persona 对话
   - 精神成员是忠于设定的精神角色，而不是事实意义上的真人导师

---

## 产品边界

为避免误解，当前版本需要明确以下边界：

- 镜像模式是**基于授权资料的视角模拟**，不是本人身份延续
- 精神成员是**基于设定与材料的精神角色**，不是权威导师或事实真人
- AI 保存能力当前以**确认式保存**为主，不是完全自动落库
- 系统旨在**帮助家族成员增进理解**，不应表述为已经实现无偏差的深度理解
- 当前基线以**用户触发**为主，主动式 AI 推荐与引导属于后续演进方向

---

## 文档导航

### 项目主文档
- 产品需求主文档：`docs/product/FAMILYAGENT_REQUIREMENTS_ANALYSIS.md`
- 需求修订清单：`docs/product/FAMILYAGENT_REQUIREMENT_REVISIONS.md`
- 需求-实现映射矩阵：`docs/product/FAMILYAGENT_REQUIREMENT_FEATURE_MATRIX.md`
- 产品文档索引：`docs/product/INDEX.md`

### 其它说明
- docs 目录导航：`docs/README.md`
- 文档迁移清单：`docs/DOC_MIGRATION_MAP.md`
- 部署文档：`docs/deployment/docker-stack.md`

---

## 本地开发

本地开发时，先准备根目录 `.env`，再启动 PostgreSQL、Redis、RabbitMQ 和 MinIO 等依赖服务。

- 前端位于 `frontend`，使用 `npm install` 与 `npm run dev`
- 后端位于 `backend`，使用 `./mvnw.cmd spring-boot:run`
- AI 服务位于 `ai-service`，先创建 `.venv` 并安装 `requirements.txt`，再执行 `start.bat`

默认访问入口：

- 前端：`http://localhost:3000`
- 后端：`http://localhost:8080`
- AI 文档：`http://localhost:8000/docs`
