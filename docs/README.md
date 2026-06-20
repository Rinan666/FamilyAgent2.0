# FamilyAgent

## 介绍

FamilyAgent 是一个面向家庭场景的智能协作系统，目标不是做通用聊天工具，也不是继续沿着传统家教产品扩展，而是围绕“家庭关系、共同记忆、成长记录、经验传承”建立长期可沉淀的数据与服务闭环。系统把前端交互、业务权限、AI 理解和记忆召回拆成独立层次，让家庭成员既能方便记录内容，也能在授权范围内获得更贴近本家庭语境的智能陪伴、整理建议与连续性支持。

镜像 Agent 的目标是基于授权资料做高沉浸的表达与认知仿真：它会参考目标成员的公开/授权记录、抽象后的私有风格统计、家庭关系和记忆片段，尽量还原其表达节奏、价值权重和思考惯性；同时不泄露未授权原文、私密事件或后端风格参考。

## 使用说明

本地开发时，先准备根目录 `.env`，再启动 PostgreSQL、Redis、RabbitMQ 和 MinIO 等依赖服务。前端位于 `frontend`，使用 `npm install` 与 `npm run dev`；后端位于 `backend`，使用 `.\mvnw.cmd spring-boot:run`；AI 服务位于 `ai-service`，先创建 `.venv` 并安装 `requirements.txt`，再执行 `start.bat`。默认访问入口为前端 `http://localhost:3000`，后端 `http://localhost:8080`，AI 文档 `http://localhost:8090/docs`。
