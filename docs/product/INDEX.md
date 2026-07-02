# Product Docs Index

> 版本：V1.0  
> 用途：为 FamilyAgent 正式产品文档提供阅读顺序、职责说明和目录关系说明  
> 状态：Active  
> 更新时间：2026-07-02

---

本目录用于存放 FamilyAgent 的正式产品文档，重点解决三个问题：

1. **为什么做这个产品**
2. **当前产品到底实现到了哪一步**
3. **产品愿景、当前能力和后续规划如何区分**

---

## 阅读顺序建议

### 1. `FAMILYAGENT_REQUIREMENTS_ANALYSIS.md`
**用途：正式需求分析主文档**

适合先看这份。它回答：
- FamilyAgent 为什么存在
- 解决什么核心问题
- 目标用户是谁
- 当前产品边界是什么
- 哪些能力是当前基线，哪些只是后续方向

如果只能先看一份产品文档，优先看这一份。

---

### 2. `FAMILYAGENT_REQUIREMENT_REVISIONS.md`
**用途：需求口径修订清单**

这份文档主要解决“旧说法容易说过头”的问题。适合用来统一：
- 产品介绍话术
- 需求描述方式
- 对内汇报、论文、答辩、演示中的口径

重点区分：
- 什么是当前事实
- 什么是长期愿景
- 什么说法需要降调

---

### 3. `FAMILYAGENT_REQUIREMENT_FEATURE_MATRIX.md`
**用途：需求与实现映射矩阵**

这份文档连接“产品需求”和“当前代码实现”，适合用于：
- 判断某项需求是否已经落地
- 识别哪些能力只是部分实现
- 后续排优先级和版本规划

如果你想回答“代码到底支不支持这个需求”，优先看这份。

---

## 与其它目录的关系

### `../plans/`
这里放的是：
- 规划稿
- 方案稿
- 优化计划
- 演进设计

这些文档**不等于当前产品现状**，更适合作为后续迭代参考。

### `../reference/`
这里放的是：
- 边界清单
- 约束盘点
- 专题参考说明

适合在定义范围、能力边界和限制条件时配合阅读。

### `../deployment/`
这里放的是部署与运行文档，不属于产品需求主文档。

---

## 当前目录文档职责总结

| 文档 | 作用 |
|---|---|
| `FAMILYAGENT_REQUIREMENTS_ANALYSIS.md` | 正式需求分析主文档 |
| `FAMILYAGENT_REQUIREMENT_REVISIONS.md` | 需求话术修订与口径统一 |
| `FAMILYAGENT_REQUIREMENT_FEATURE_MATRIX.md` | 需求与代码实现映射 |

---

## 推荐使用方式

- 写正式需求、产品定义：先看 `FAMILYAGENT_REQUIREMENTS_ANALYSIS.md`
- 修旧文档、统一话术：看 `FAMILYAGENT_REQUIREMENT_REVISIONS.md`
- 对照代码、排查落地情况：看 `FAMILYAGENT_REQUIREMENT_FEATURE_MATRIX.md`

如果后续新增新的正式产品文档，也建议优先放在本目录，并在本索引中补充其用途与阅读顺序。
