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
                                        "description": "标准答案",
                                    },
                                    "steps": {
                                        "type": "array",
                                        "items": {"type": "string"},
                                        "description": "解题步骤",
                                    },
                                    "explanation": {
                                        "type": "string",
                                        "description": "详细解析",
                                    },
                                },
                                "required": ["value", "steps"],
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
