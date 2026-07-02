# 文档迁移清单

> 版本：V1.0  
> 用途：记录 docs 目录本次重组后的新旧位置关系，以及是否建议纳入 Git 跟踪  
> 更新时间：2026-07-02

---

## 1. 迁移原则

本次文档整理采用以下原则：

1. **项目主说明移到仓库根目录**  
   `README.md` 应位于仓库根目录，而不是 `docs/` 内。

2. **正式产品文档集中到 `docs/product/`**  
   包括需求分析、需求修订、需求-功能映射等。

3. **规划类文档集中到 `docs/plans/`**  
   作为阶段性方案和草稿，默认忽略 Git。

4. **参考/边界文档集中到 `docs/reference/`**

5. **部署文档集中到 `docs/deployment/`**

6. **课程/论文/学习材料集中到 `docs/academic/`**  
   默认忽略 Git，避免污染产品主文档。

---

## 2. 迁移映射表

| 原位置 | 新位置 | 文档类型 | 是否建议 Git 跟踪 | 说明 |
|---|---|---|---|---|
| `docs/README.md`（旧项目说明） | `README.md` | 项目主说明 | 是 | 仓库主说明应在根目录 |
| `docs/FAMILYAGENT_REQUIREMENTS_ANALYSIS.md` | `docs/product/FAMILYAGENT_REQUIREMENTS_ANALYSIS.md` | 正式需求文档 | 是 | 产品需求分析主文档 |
| `docs/FAMILYAGENT_REQUIREMENT_REVISIONS.md` | `docs/product/FAMILYAGENT_REQUIREMENT_REVISIONS.md` | 需求修订文档 | 是 | 统一产品口径 |
| `docs/FAMILYAGENT_REQUIREMENT_FEATURE_MATRIX.md` | `docs/product/FAMILYAGENT_REQUIREMENT_FEATURE_MATRIX.md` | 需求映射文档 | 是 | 需求与代码实现映射 |
| `docs/SPIRITUAL_MEMBER_PLAN.md` | `docs/plans/SPIRITUAL_MEMBER_PLAN.md` | 规划文档 | 否（默认） | 阶段性计划，适合忽略 |
| `docs/OPTIMIZATION_PLAN.md` | `docs/plans/OPTIMIZATION_PLAN.md` | 规划文档 | 否（默认） | 优化方案 |
| `docs/PHOTO_EXPANSION_PLAN.md` | `docs/plans/PHOTO_EXPANSION_PLAN.md` | 规划文档 | 否（默认） | 扩展方案 |
| `docs/AI_OPTIMIZATION_NEW_PLAN.md` | `docs/plans/AI_OPTIMIZATION_NEW_PLAN.md` | 规划文档 | 否（默认） | 优化草案 |
| `docs/AI_SERVICE_EXPANSION_PLAN.md` | `docs/plans/AI_SERVICE_EXPANSION_PLAN.md` | 规划文档 | 否（默认） | AI 服务扩展方案 |
| `docs/AI_PROXY_BOUNDARY_INVENTORY.md` | `docs/reference/AI_PROXY_BOUNDARY_INVENTORY.md` | 参考文档 | 是 | 边界与约束盘点 |
| `docs/docker-stack.md` | `docs/deployment/docker-stack.md` | 部署文档 | 是 | 部署与运行说明 |
| `docs/works/课程设计/*` | `docs/academic/课程设计/*` | 学术材料 | 否（默认） | 课程设计材料 |
| `docs/works/毕业设计/*` | `docs/academic/毕业设计/*` | 学术材料 | 否（默认） | 毕业设计材料 |
| `docs/works/*学习*.md` | `docs/academic/*学习*.md` | 学习材料 | 否（默认） | 非正式学习文档 |

---

## 3. 当前 Git 跟踪建议

### 建议保留跟踪

- `README.md`
- `docs/README.md`
- `docs/product/**`
- `docs/reference/**`
- `docs/deployment/**`

### 建议默认忽略

- `docs/plans/**`
- `docs/academic/**`
- `docs/**/.claude/**`

---

## 4. 说明

如果后续某个计划文档从“草稿”变成“正式版本”，建议：

1. 从 `docs/plans/` 中复制或迁移到 `docs/product/` 或 `docs/reference/`
2. 使用更稳定的文件名
3. 再纳入 Git 跟踪

这样可以保持：

- 草稿和正式文档分离
- 产品基线文档稳定
- 学术材料不干扰项目主文档结构
