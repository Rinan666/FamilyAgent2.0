"""
讲题Agent — 苏格拉底式引导教学
"""
from typing import AsyncIterator, Optional

from app.agents.base import BaseAgent
from app.llm.client import llm_client
from app.llm.prompts import tutor as tutor_prompts
from app.services.web_search import build_web_context, build_web_search_context
from app.utils.safety_limits import (
    validate_no_prompt_leak_attempt,
    validate_no_role_hijack_attempt,
    validate_text_budget,
)


class TutorAgent(BaseAgent):
    """讲题Agent"""

    def __init__(self):
        super().__init__(
            name="TutorAgent",
            system_prompt=tutor_prompts.SYSTEM_PROMPT,
        )

    def _build_context(
        self,
        question_content: str,
        answer: str,
        steps: str,
        grade: str = "初中",
        subject: str = "数学",
        knowledge_point: str = "未知",
        mastery_level: str = "中",
        common_errors: str = "无历史数据",
        teaching_style: str = "guided",
        memory_context: str = "暂无已检索到的长期记忆或家族知识库上下文。",
        viewer_role: str = "STUDENT",
        target_role: str = "STUDENT",
        client_timestamp: str = "",
        client_timezone: str = "",
        public_web_context: str = "",
    ) -> str:
        """构建讲题上下文"""
        style_instruction = (
            tutor_prompts.DIRECT_STYLE_INSTRUCTION
            if teaching_style == "direct"
            else tutor_prompts.GUIDED_STYLE_INSTRUCTION
        )
        context = self.system_prompt.format(
            grade=grade,
            subject=subject,
            knowledge_point=knowledge_point,
            mastery_level=mastery_level,
            common_errors=common_errors,
            question_content=question_content,
            answer=answer,
            steps=steps,
            teaching_style_instruction=style_instruction,
            memory_context=memory_context or "暂无已检索到的长期记忆或家族知识库上下文。",
            role_instruction=self._role_instruction(viewer_role, target_role),
            current_time_context=self._current_time_context(client_timestamp, client_timezone),
            public_web_context=public_web_context or "未触发联网搜索。",
        )
        if teaching_style == "direct":
            context += tutor_prompts.DIRECT_STYLE_OVERRIDE
        else:
            context += tutor_prompts.GUIDED_STYLE_OVERRIDE
        return context

    def _build_chat_context(
        self,
        grade: str = "初中",
        subject: str = "数学",
        knowledge_point: str = "未知",
        mastery_level: str = "中",
        common_errors: str = "无历史数据",
        memory_context: str = "暂无已检索到的长期记忆或家族知识库上下文。",
        viewer_role: str = "STUDENT",
        target_role: str = "STUDENT",
        client_timestamp: str = "",
        client_timezone: str = "",
        public_web_context: str = "",
    ) -> str:
        """构建普通对话上下文"""
        if self._is_mirror_mode(subject, knowledge_point):
            return tutor_prompts.MIRROR_CHAT_SYSTEM_PROMPT.format(
                subject=subject or "家族记忆",
                knowledge_point=knowledge_point or "镜像 Agent",
                mastery_level=mastery_level,
                memory_context=memory_context or "暂无已检索到的授权家族记忆上下文。",
                role_instruction=self._role_instruction(viewer_role, target_role),
                current_time_context=self._current_time_context(client_timestamp, client_timezone),
                public_web_context=public_web_context or "未触发联网搜索。",
            )
        return tutor_prompts.CHAT_SYSTEM_PROMPT.format(
            grade=grade or "未设置",
            subject=subject,
            knowledge_point=knowledge_point,
            mastery_level=mastery_level,
            common_errors=common_errors,
            memory_context=memory_context or "暂无已检索到的长期记忆或家族知识库上下文。",
            role_instruction=self._role_instruction(viewer_role, target_role),
            current_time_context=self._current_time_context(client_timestamp, client_timezone),
            public_web_context=public_web_context or "未触发联网搜索。",
        )

    @staticmethod
    def _is_mirror_mode(subject: str = "", knowledge_point: str = "") -> bool:
        """镜像 Agent 走专用 prompt，避免被普通学习陪伴语气稀释边界。"""
        text = f"{subject or ''} {knowledge_point or ''}".lower()
        return "镜像" in text or "mirror" in text

    def _role_instruction(self, viewer_role: str = "STUDENT", target_role: str = "STUDENT") -> str:
        """当前视图角色说明。家庭角色是可调整的上下文，不是永久身份。"""
        viewer = (viewer_role or "STUDENT").upper()
        target = (target_role or "STUDENT").upper()
        base = (
            f"- 当前查看/对话视图：{viewer}\n"
            f"- 当前学习对象角色：{target}\n"
            "- 家庭角色表示当前协作分工，可随阶段调整；不要把“学习者”描述成永久身份。\n"
            "- 家族知识库、日记、关键事件等上下文必须已经由后端权限过滤；不要主动扩展或猜测隐私信息。"
        )
        if viewer == "PARENT":
            return base + (
                "\n- 面向家长时，使用报告式摘要、风险提示和可执行陪伴建议。"
                "\n- 只总结与学习支持直接相关的信息，不输出学习者的完整情绪隐私或无关私密细节。"
            )
        if viewer == "ADMIN":
            return base + (
                "\n- 面向管理员时，侧重内容质量、题库/知识点配置、系统流程和风险边界。"
                "\n- 不展示个人隐私细节，除非请求中已经明确提供且与管理任务直接相关。"
            )
        return base + (
            "\n- 面向学习者时，保持鼓励、陪伴和讲解式表达，优先帮助其自己理解和推进下一步。"
        )

    @staticmethod
    def _current_time_context(client_timestamp: str = "", client_timezone: str = "") -> str:
        """用户提问时的时间基准，用于解释相对日期。"""
        if not client_timestamp:
            return "- 当前用户提问时间：未提供。"
        timezone = client_timezone or "未知"
        return (
            f"- 当前用户提问时间：{client_timestamp}\n"
            f"- 用户本地时区：{timezone}\n"
            "- 当用户提到今天、明天、本周、下周、最近、刚才、稍后或截止时间时，必须以这个时间为基准；不要凭模型训练时间推断当前日期。"
        )

    async def explain(
        self,
        question_content: str,
        answer: str,
        steps: str,
        student_message: str,
        history: Optional[list[dict]] = None,
        grade: str = "初中",
        subject: str = "数学",
        knowledge_point: str = "未知",
        mastery_level: str = "中",
        common_errors: str = "无历史数据",
        teaching_style: str = "guided",
        mode: str = "explain",
        memory_context: str = "",
        viewer_role: str = "STUDENT",
        target_role: str = "STUDENT",
        client_timestamp: str = "",
        client_timezone: str = "",
    ) -> str:
        """
        讲题（非流式）

        Args:
            question_content: 题目内容
            answer: 标准答案
            steps: 解题步骤
            student_message: 学生当前消息
            history: 对话历史
            grade: 年级
            subject: 学科
            knowledge_point: 知识点
            mastery_level: 掌握程度
            common_errors: 常见错误类型
        """
        validate_no_prompt_leak_attempt(student_message)
        validate_no_role_hijack_attempt(student_message)
        validate_text_budget({
            "question_content": question_content,
            "answer": answer,
            "steps": steps,
            "student_message": student_message,
            "history": history or [],
            "memory_context": memory_context,
        }, label="tutor request")

        if mode == "chat":
            public_web_context = await build_web_context(student_message)
            context = self._build_chat_context(
                grade, subject, knowledge_point,
                mastery_level, common_errors, memory_context,
                viewer_role, target_role,
                client_timestamp, client_timezone,
                public_web_context,
            )
            messages = [{"role": "system", "content": context}]
            if history:
                messages.extend(history)
            messages.append({"role": "user", "content": student_message})
        else:
            public_web_context = await build_web_context(student_message)
            context = self._build_context(
                question_content, answer, steps,
                grade, subject, knowledge_point,
                mastery_level, common_errors, teaching_style,
                memory_context,
                viewer_role, target_role,
                client_timestamp, client_timezone,
                public_web_context,
            )

            # 每次请求都携带完整题目上下文，避免历史对话续聊时丢失题目和讲题风格。
            if teaching_style == "direct":
                messages = [
                    {"role": "system", "content": context},
                    {"role": "system", "content": tutor_prompts.DIRECT_STYLE_OVERRIDE},
                    {"role": "user", "content": student_message},
                ]
            elif not history:
                messages = [
                    {"role": "system", "content": context},
                    {"role": "user", "content": student_message},
                ]
            else:
                messages = [
                    {"role": "system", "content": context},
                    *history,
                    {"role": "user", "content": student_message},
                ]

        return await llm_client.chat(messages, temperature=0.7)

    async def explain_stream(
        self,
        question_content: str,
        answer: str,
        steps: str,
        student_message: str,
        history: Optional[list[dict]] = None,
        grade: str = "初中",
        subject: str = "数学",
        knowledge_point: str = "未知",
        mastery_level: str = "中",
        common_errors: str = "无历史数据",
        teaching_style: str = "guided",
        mode: str = "explain",
        memory_context: str = "",
        viewer_role: str = "STUDENT",
        target_role: str = "STUDENT",
        client_timestamp: str = "",
        client_timezone: str = "",
    ) -> AsyncIterator[dict]:
        """
        讲题（流式输出）

        使用SSE推送，前端实现打字机效果
        """
        validate_no_prompt_leak_attempt(student_message)
        validate_no_role_hijack_attempt(student_message)
        validate_text_budget({
            "question_content": question_content,
            "answer": answer,
            "steps": steps,
            "student_message": student_message,
            "history": history or [],
            "memory_context": memory_context,
        }, label="tutor stream request")

        if mode == "chat":
            web_search_context = await build_web_search_context(student_message)
            public_web_context = web_search_context.prompt_context
            yield {
                "type": "metadata",
                "web_search": {
                    "needed": web_search_context.needed,
                    "used": len(web_search_context.results) > 0,
                    "result_count": len(web_search_context.results),
                    "sources": [
                        {
                            "title": item.title,
                            "url": item.url,
                            "snippet": item.snippet,
                        }
                        for item in web_search_context.results
                    ],
                },
            }
            context = self._build_chat_context(
                grade, subject, knowledge_point,
                mastery_level, common_errors, memory_context,
                viewer_role, target_role,
                client_timestamp, client_timezone,
                public_web_context,
            )
            messages = [{"role": "system", "content": context}]
            if history:
                messages.extend(history)
            messages.append({"role": "user", "content": student_message})
        else:
            web_search_context = await build_web_search_context(student_message)
            public_web_context = web_search_context.prompt_context
            yield {
                "type": "metadata",
                "web_search": {
                    "needed": web_search_context.needed,
                    "used": len(web_search_context.results) > 0,
                    "result_count": len(web_search_context.results),
                    "sources": [
                        {
                            "title": item.title,
                            "url": item.url,
                            "snippet": item.snippet,
                        }
                        for item in web_search_context.results
                    ],
                },
            }
            context = self._build_context(
                question_content, answer, steps,
                grade, subject, knowledge_point,
                mastery_level, common_errors, teaching_style,
                memory_context,
                viewer_role, target_role,
                client_timestamp, client_timezone,
                public_web_context,
            )

            if teaching_style == "direct":
                messages = [
                    {"role": "system", "content": context},
                    {"role": "system", "content": tutor_prompts.DIRECT_STYLE_OVERRIDE},
                    {"role": "user", "content": student_message},
                ]
            elif not history:
                messages = [
                    {"role": "system", "content": context},
                    {"role": "user", "content": student_message},
                ]
            else:
                messages = [
                    {"role": "system", "content": context},
                    *history,
                    {"role": "user", "content": student_message},
                ]

        async for chunk in llm_client.chat_stream(messages, temperature=0.7):
            yield {"type": "content", "content": chunk}


# 全局单例
tutor_agent = TutorAgent()
