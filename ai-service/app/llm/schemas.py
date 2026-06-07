"""
LLM 结构化输出 Schema

定义 Agent 输出的 JSON Schema，用于 OpenAI structured output / Claude tool use
"""

# ============================================
# 批改结果 Schema
# ============================================
GRADE_RESULT_SCHEMA = {
    "type": "json_schema",
    "json_schema": {
        "name": "grade_result",
        "description": "题目批改结果",
        "schema": {
            "type": "object",
            "properties": {
                "overall_score": {
                    "type": "number",
                    "description": "总分 (0-100)",
                    "minimum": 0,
                    "maximum": 100,
                },
                "is_correct": {
                    "type": "boolean",
                    "description": "最终答案是否正确",
                },
                "step_grades": {
                    "type": "array",
                    "items": {
                        "type": "object",
                        "properties": {
                            "step_number": {"type": "integer"},
                            "step_name": {
                                "type": "string",
                                "description": "解题步骤名称",
                            },
                            "student_work": {
                                "type": "string",
                                "description": "学生的作答内容",
                            },
                            "is_correct": {"type": "boolean"},
                            "score": {
                                "type": "number",
                                "description": "该步骤得分",
                            },
                            "max_score": {"type": "number"},
                            "error_type": {
                                "type": "string",
                                "enum": [
                                    "概念混淆",
                                    "计算失误",
                                    "符号错误",
                                    "步骤遗漏",
                                    "公式误用",
                                    "逻辑错误",
                                    "理解偏差",
                                    "无",
                                ],
                            },
                            "feedback": {
                                "type": "string",
                                "description": "该步骤的评语",
                            },
                        },
                        "required": [
                            "step_number",
                            "step_name",
                            "is_correct",
                            "score",
                            "max_score",
                            "feedback",
                        ],
                    },
                },
                "error_analysis": {
                    "type": "object",
                    "properties": {
                        "primary_error_type": {"type": "string"},
                        "knowledge_gaps": {
                            "type": "array",
                            "items": {"type": "string"},
                            "description": "暴露的知识漏洞",
                        },
                        "suggestion": {
                            "type": "string",
                            "description": "针对性学习建议",
                        },
                        "parent_explanation": {
                            "type": "string",
                            "description": "给家长看的简短解释，说明学习者主要卡在哪里",
                        },
                        "next_suggestion": {
                            "type": "string",
                            "description": "下一步练习或复盘建议",
                        },
                    },
                },
                "overall_feedback": {
                    "type": "string",
                    "description": "整体评语",
                },
            },
            "required": [
                "overall_score",
                "is_correct",
                "step_grades",
                "error_analysis",
                "overall_feedback",
            ],
        },
    },
}

# ============================================
# 出题结果 Schema
# ============================================
GENERATE_QUESTIONS_SCHEMA = {
    "type": "json_schema",
    "json_schema": {
        "name": "generated_questions",
        "description": "AI生成的题目列表",
        "schema": {
            "type": "object",
            "properties": {
                "questions": {
                    "type": "array",
                    "items": {
                        "type": "object",
                        "properties": {
                            "subject": {"type": "string"},
                            "grade": {"type": "string"},
                            "type": {
                                "type": "string",
                                "enum": ["CHOICE", "FILL", "CALCULATION", "PROOF"],
                            },
                            "difficulty": {
                                "type": "integer",
                                "minimum": 1,
                                "maximum": 5,
                            },
                            "kp_id": {"type": "integer"},
                            "content": {
                                "type": "object",
                                "properties": {
                                    "stem": {
                                        "type": "string",
                                        "description": "题干",
                                    },
                                    "options": {
                                        "type": "array",
                                        "items": {"type": "string"},
                                        "description": "选项(选择题时)",
                                    },
                                    "figures": {
                                        "type": "array",
                                        "items": {"type": "string"},
                                        "description": "配图URL",
                                    },
                                },
                                "required": ["stem"],
                            },
                            "answer": {
                                "type": "object",
                                "properties": {
                                    "value": {
                                        "type": "string",
                                        "minLength": 1,
                                        "description": "标准答案",
                                    },
                                    "steps": {
                                        "type": "array",
                                        "items": {"type": "string"},
                                        "minItems": 2,
                                        "description": "解题步骤",
                                    },
                                    "explanation": {
                                        "type": "string",
                                        "minLength": 1,
                                        "description": "详细解析",
                                    },
                                },
                                "required": ["value", "steps", "explanation"],
                            },
                            "tags": {"type": "array", "items": {"type": "string"}},
                        },
                        "required": [
                            "subject",
                            "grade",
                            "type",
                            "difficulty",
                            "content",
                            "answer",
                        ],
                    },
                },
            },
            "required": ["questions"],
        },
    },
}

# ============================================
# 学力评估 Schema
# ============================================
ASSESSMENT_PROFILE_SCHEMA = {
    "type": "json_schema",
    "json_schema": {
        "name": "assessment_profile",
        "description": "学力评估报告",
        "schema": {
            "type": "object",
            "properties": {
                "overall_level": {
                    "type": "string",
                    "enum": ["基础薄弱", "需要加强", "基本掌握", "熟练掌握", "优秀"],
                },
                "strengths": {
                    "type": "array",
                    "items": {"type": "string"},
                    "description": "优势知识点",
                },
                "weaknesses": {
                    "type": "array",
                    "items": {"type": "string"},
                    "description": "薄弱知识点",
                },
                "learning_suggestions": {
                    "type": "array",
                    "items": {"type": "string"},
                    "description": "学习建议",
                },
                "recommended_focus": {
                    "type": "array",
                    "items": {
                        "type": "object",
                        "properties": {
                            "kp_name": {"type": "string"},
                            "priority": {
                                "type": "string",
                                "enum": ["高", "中", "低"],
                            },
                            "reason": {"type": "string"},
                        },
                    },
                },
            },
        },
    },
}

# ============================================
# Skill Workflow Schemas
# ============================================
MISTAKE_REVIEW_SCHEMA = {
    "type": "json_schema",
    "json_schema": {
        "name": "mistake_review",
        "description": "错题复盘结果",
        "schema": {
            "type": "object",
            "properties": {
                "error_category": {
                    "type": "string",
                    "description": "主要错因分类",
                },
                "correct_solution_summary": {
                    "type": "string",
                    "description": "正确解法摘要",
                },
                "correction_note": {
                    "type": "string",
                    "description": "一句话订正",
                },
                "error_pattern": {
                    "type": "string",
                    "description": "可复用的错误模式总结",
                },
                "similar_question_suggestions": {
                    "type": "array",
                    "items": {"type": "string"},
                    "description": "同类题练习建议",
                },
                "spaced_review_plan": {
                    "type": "array",
                    "items": {
                        "type": "object",
                        "properties": {
                            "day_offset": {"type": "integer"},
                            "action": {"type": "string"},
                        },
                        "required": ["day_offset", "action"],
                    },
                },
                "parent_explanation": {
                    "type": "string",
                    "description": "给家长看的简短说明",
                },
                "missing_info": {
                    "type": "array",
                    "items": {"type": "string"},
                    "description": "仍需补充的信息",
                },
            },
            "required": [
                "error_category",
                "correct_solution_summary",
                "correction_note",
                "error_pattern",
                "similar_question_suggestions",
                "spaced_review_plan",
                "parent_explanation",
                "missing_info",
            ],
        },
    },
}


DAILY_PRACTICE_SCHEMA = {
    "type": "json_schema",
    "json_schema": {
        "name": "daily_practice",
        "description": "每日数学短练",
        "schema": {
            "type": "object",
            "properties": {
                "daily_goal": {"type": "string"},
                "warmup_prompt": {"type": "string"},
                "questions": {
                    "type": "array",
                    "items": {
                        "type": "object",
                        "properties": {
                            "stem": {"type": "string"},
                            "answer": {"type": "string"},
                            "explanation": {"type": "string"},
                            "difficulty": {"type": "integer"},
                            "error_tags": {
                                "type": "array",
                                "items": {"type": "string"},
                            },
                        },
                        "required": [
                            "stem",
                            "answer",
                            "explanation",
                            "difficulty",
                            "error_tags",
                        ],
                    },
                },
                "self_check": {
                    "type": "array",
                    "items": {"type": "string"},
                },
                "next_review_action": {"type": "string"},
                "missing_info": {
                    "type": "array",
                    "items": {"type": "string"},
                },
            },
            "required": [
                "daily_goal",
                "warmup_prompt",
                "questions",
                "self_check",
                "next_review_action",
                "missing_info",
            ],
        },
    },
}


EXAM_REVIEW_SCHEMA = {
    "type": "json_schema",
    "json_schema": {
        "name": "exam_review",
        "description": "测评后复习建议",
        "schema": {
            "type": "object",
            "properties": {
                "diagnosis": {"type": "string"},
                "priority_weak_points": {
                    "type": "array",
                    "items": {
                        "type": "object",
                        "properties": {
                            "knowledge_point": {"type": "string"},
                            "priority": {
                                "type": "string",
                                "enum": ["高", "中", "低"],
                            },
                            "reason": {"type": "string"},
                        },
                        "required": ["knowledge_point", "priority", "reason"],
                    },
                },
                "daily_plan": {
                    "type": "array",
                    "items": {
                        "type": "object",
                        "properties": {
                            "day": {"type": "integer"},
                            "focus": {"type": "string"},
                            "tasks": {
                                "type": "array",
                                "items": {"type": "string"},
                            },
                        },
                        "required": ["day", "focus", "tasks"],
                    },
                },
                "timed_practice": {
                    "type": "array",
                    "items": {"type": "string"},
                },
                "mistake_review_actions": {
                    "type": "array",
                    "items": {"type": "string"},
                },
                "next_retest": {"type": "string"},
                "risks": {
                    "type": "array",
                    "items": {"type": "string"},
                },
                "missing_info": {
                    "type": "array",
                    "items": {"type": "string"},
                },
            },
            "required": [
                "diagnosis",
                "priority_weak_points",
                "daily_plan",
                "timed_practice",
                "mistake_review_actions",
                "next_retest",
                "risks",
                "missing_info",
            ],
        },
    },
}


STUDY_PLAN_SCHEMA = {
    "type": "json_schema",
    "json_schema": {
        "name": "study_plan",
        "description": "学习计划",
        "schema": {
            "type": "object",
            "properties": {
                "plan_goal": {"type": "string"},
                "priorities": {
                    "type": "array",
                    "items": {
                        "type": "object",
                        "properties": {
                            "item": {"type": "string"},
                            "priority": {
                                "type": "string",
                                "enum": ["高", "中", "低"],
                            },
                            "reason": {"type": "string"},
                        },
                        "required": ["item", "priority", "reason"],
                    },
                },
                "daily_tasks": {
                    "type": "array",
                    "items": {
                        "type": "object",
                        "properties": {
                            "day": {"type": "integer"},
                            "focus": {"type": "string"},
                            "tasks": {
                                "type": "array",
                                "items": {"type": "string"},
                            },
                            "check_method": {"type": "string"},
                        },
                        "required": ["day", "focus", "tasks", "check_method"],
                    },
                },
                "review_questions": {
                    "type": "array",
                    "items": {"type": "string"},
                },
                "parent_support": {
                    "type": "array",
                    "items": {"type": "string"},
                },
                "missing_info": {
                    "type": "array",
                    "items": {"type": "string"},
                },
            },
            "required": [
                "plan_goal",
                "priorities",
                "daily_tasks",
                "review_questions",
                "parent_support",
                "missing_info",
            ],
        },
    },
}
