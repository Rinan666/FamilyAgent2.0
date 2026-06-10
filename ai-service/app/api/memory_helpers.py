"""Pure helpers for family memory save-plan routing and shared heuristics."""

import re

def _sanitize_save_tool_plan(data: dict) -> dict:
    content = _normalize_save_content(data.get("content", ""))
    raw_tool = _choice(data.get("tool"), {"NONE", "DIARY", "FAMILY_MEMORY", "GROWTH_GUARD"}, "NONE")
    if _looks_like_save_command_only(content):
        return _blocked_save_tool_plan("只有保存指令，没有可沉淀的具体内容。")
    if _looks_like_prompt_injection(content):
        return _blocked_save_tool_plan("疑似提示词注入或越权指令，不适合保存为家族记忆。")
    if _lacks_substantive_save_value(content):
        return _blocked_save_tool_plan("内容缺乏具体人物、事件、观察、情绪强度或可跟进经验，不应沉淀为家族记忆。")
    tool = _infer_save_tool(content, raw_tool)
    should_save = (
        (bool(data.get("should_save")) or _has_durable_save_value(content))
        and tool != "NONE"
        and len(content) >= 4
    )
    if not should_save:
        tool = "NONE"

    visibility = _normalize_save_visibility(
        tool,
        _choice(
            data.get("visibility"),
            {"PRIVATE", "FAMILY_VISIBLE", "CARE_VISIBLE", "LEGACY_VISIBLE"},
            "PRIVATE",
        ),
        content,
    )
    scope = _normalize_save_scope(
        tool,
        _choice(
            data.get("scope"),
            {"PRIVATE", "CARE_VISIBLE", "FAMILY_VISIBLE", "PARENT_VISIBLE"},
            "PRIVATE" if visibility == "PRIVATE" else visibility,
        ),
        visibility,
    )
    category = _choice(
        data.get("category"),
        {
            "POSTURE",
            "DENTAL",
            "VISION",
            "SLEEP",
            "EXERCISE",
            "SCREEN_TIME",
            "EMOTION",
            "COMMUNICATION",
            "OTHER",
        },
        "OTHER",
    )
    if tool == "GROWTH_GUARD":
        category = _infer_growth_category(content, category)
    memory_type = _choice(
        data.get("memory_type"),
        {"FAMILY_STORY", "ELDER_ADVICE", "HEALTH_REMINDER", "GROWTH_RISK", "VALUE", "PLAN"},
        "ELDER_ADVICE",
    )
    if tool == "FAMILY_MEMORY":
        memory_type = _infer_family_memory_type(content, memory_type)
    entry_type = _choice(
        data.get("entry_type"),
        {"DAILY", "IMPORTANT_EVENT", "LESSON", "EMOTION", "MESSAGE_TO_FAMILY", "SELF_REFLECTION"},
        "DAILY",
    )
    if tool == "DIARY":
        entry_type = _infer_diary_entry_type(content, entry_type)

    return {
        "should_save": should_save,
        "tool": tool,
        "content": content,
        "title": str(data.get("title", "")).strip()[:24] or _default_save_title(tool),
        "summary": str(data.get("summary", content)).strip()[:80],
        "visibility": visibility,
        "entry_type": entry_type,
        "memory_type": memory_type,
        "scope": scope,
        "category": category,
        "severity": _bounded_int(data.get("severity"), 1, 5, _default_growth_severity(content)),
        "importance": _bounded_int(data.get("importance"), 1, 5, 3),
        "tags": _compact_string_list(data.get("tags"), 6, 18),
        "reason": _save_plan_reason(data.get("reason"), tool),
        "confirmation_message": str(
            data.get("confirmation_message", _default_save_confirmation(tool))
        ).strip()[:120] or _default_save_confirmation(tool),
    }

def _normalize_save_content(value: object, *, max_chars: int = 1200) -> str:
    content = re.sub(r"\s+", " ", str(value or "").strip())
    if len(content) <= 500:
        return content

    sentences = _split_sentences(content)
    if not sentences:
        return content[:max_chars].strip()

    selected: list[str] = []
    budget = max(240, min(max_chars, 420))
    for sentence in sentences:
        if not _sentence_has_save_value(sentence):
            continue
        if sum(len(item) for item in selected) + len(sentence) > budget:
            continue
        selected.append(sentence)
        if len(selected) >= 5:
            break

    if not selected:
        selected = sentences[:3]

    summary = "；".join(item.strip("，。；; ") for item in selected if item.strip())
    if len(summary) > max_chars:
        summary = summary[: max_chars - 1].rstrip("，。；; ") + "…"
    return summary.strip()

def _split_sentences(text: str) -> list[str]:
    parts = re.split(r"(?<=[。！？!?；;])|\n+", text)
    return [part.strip() for part in parts if part and part.strip()]

def _sentence_has_save_value(sentence: str) -> bool:
    compact = re.sub(r"\s+", "", sentence)
    if len(compact) < 8 or _looks_like_low_information_noise(compact):
        return False
    return bool(
        _looks_like_growth_observation(compact)
        or _looks_like_family_memory(compact)
        or _has_high_value_save_signal(compact)
        or _has_private_emotion_signal(compact)
        or _has_substantive_insight_signal(compact)
        or _has_concrete_save_anchor(compact)
    )

def _infer_save_tool(content: str, proposed_tool: str) -> str:
    text = content.strip()
    if len(text) < 4:
        return "NONE"
    if _looks_like_save_command_only(text):
        return "NONE"
    if _looks_like_prompt_injection(text):
        return "NONE"
    if _looks_like_growth_observation(text):
        return "GROWTH_GUARD"
    if _looks_like_family_memory(text):
        return "FAMILY_MEMORY"
    if proposed_tool in {"DIARY", "FAMILY_MEMORY", "GROWTH_GUARD"}:
        return proposed_tool
    if _has_high_value_save_signal(text):
        if _looks_like_learning_or_care_strategy(text):
            return "FAMILY_MEMORY"
        return "DIARY"
    if _looks_like_diary(text):
        return "DIARY"
    return proposed_tool

def _looks_like_save_command_only(text: str) -> bool:
    normalized = re.sub(r"\s+", "", text.strip())
    if not normalized:
        return True
    return bool(re.fullmatch(
        r"(请|麻烦)?(帮我)?(把)?(上面|刚才|前面|上一段|前一段|之前|刚刚|这件事|这段话|这些内容)?"
        r"(提到的|说的|讲的)?(事情|内容|记录)?"
        r"(保存|保存一下|存起来|记下来|记录一下|记录下来|沉淀下来|帮我记|帮我存|帮我保存|帮我记录)"
        r"[。.!！?？]*",
        normalized,
    ))

def _has_durable_save_value(text: str) -> bool:
    content = text.strip()
    if len(content) < 6 or _looks_like_save_command_only(content) or _looks_like_prompt_injection(content):
        return False
    if _lacks_substantive_save_value(content):
        return False
    if _looks_like_growth_observation(content) or _looks_like_family_memory(content):
        return True
    if _has_high_value_save_signal(content):
        return True
    if _has_private_emotion_signal(content) or _has_substantive_insight_signal(content):
        return True
    if re.search(r"(今天|昨天|最近|这次|那天|小时候|当年|以前|刚才).{0,80}(发生|遇到|聊|说|决定|选择|记录|看见|想到|感受)", content):
        return True
    if re.search(r"(想对|留给|告诉).{0,20}(家人|孩子|后辈|未来的自己|以后的我)", content):
        return True
    return False

def _should_skip_save_planning(message: str, conversation_context: str = "") -> bool:
    content = str(message or "").strip()
    if not content:
        return True
    if _looks_like_prompt_injection(content):
        return False
    if _looks_like_save_command_only(content):
        return not _has_context_save_anchor(conversation_context)
    return _is_definitely_low_value_save_input(content)

def _lacks_substantive_save_value(text: str) -> bool:
    content = re.sub(r"\s+", "", text.strip())
    if not content:
        return True
    if _looks_like_save_command_only(content) or _looks_like_prompt_injection(content):
        return False
    if _looks_like_growth_observation(content) or _looks_like_family_memory(content):
        return False
    if _has_high_value_save_signal(content):
        return False
    if _has_private_emotion_signal(content) or _has_substantive_insight_signal(content):
        return False
    if _has_concrete_save_anchor(content):
        return False
    if _looks_like_low_information_noise(content):
        return True
    abstract_signal = re.search(
        r"(价值|成长|未来|意义|哲理|深刻|温柔|积极|沉淀|经验|家族|人生|长期主义)",
        content,
    )
    concrete_signal = re.search(
        r"(今天|昨天|最近|这次|那天|当年|以前|孩子|家人|爸爸|妈妈|爷爷|奶奶).{0,40}"
        r"(发生|遇到|选择|决定|说|聊|做|提醒|观察|担心|难过|焦虑)",
        content,
    )
    return bool(abstract_signal and not concrete_signal)

def _is_definitely_low_value_save_input(text: str) -> bool:
    content = re.sub(r"\s+", "", text.strip())
    if not content:
        return True
    if _looks_like_save_command_only(content) or _looks_like_prompt_injection(content):
        return False
    if (
        _looks_like_growth_observation(content)
        or _looks_like_family_memory(content)
        or _has_high_value_save_signal(content)
        or _has_private_emotion_signal(content)
        or _has_substantive_insight_signal(content)
        or _has_concrete_save_anchor(content)
        or _has_ambiguous_but_potentially_valuable_signal(content)
    ):
        return False
    if _looks_like_low_information_noise(content):
        return True
    abstract_signal = re.search(
        r"(价值|成长|未来|意义|哲理|深刻|温柔|积极|沉淀|经验|家族|人生|长期主义)",
        content,
    )
    concrete_signal = re.search(
        r"(孩子|家人|爸爸|妈妈|爷爷|奶奶|我|我们|今天|昨天|最近|这次|那天).{0,50}"
        r"(发生|遇到|选择|决定|说|聊|做|提醒|观察|担心|难过|焦虑|沉默|变化|不愿意|愿意)",
        content,
    )
    return bool(abstract_signal and not concrete_signal)

def _has_ambiguous_but_potentially_valuable_signal(text: str) -> bool:
    return bool(
        len(text) >= 16
        and re.search(r"(孩子|儿子|女儿|学生|家人|爸爸|妈妈|爷爷|奶奶|我|我们)", text)
        and re.search(
            r"(在意|不对劲|沉默|犹豫|愿意|不愿意|变化|反应|提到|聊|说|问|担心|别扭|卡住|躲开|试试)",
            text,
        )
    )

def _has_high_value_save_signal(text: str) -> bool:
    return _save_value_score(text) >= 5

def _save_value_score(text: str) -> int:
    content = re.sub(r"\s+", "", text.strip())
    if not content or _looks_like_low_information_noise(content) or _looks_like_prompt_injection(content):
        return 0

    score = 0
    if len(content) >= 24:
        score += 1
    if re.search(r"(孩子|儿子|女儿|学生|家人|爸爸|妈妈|爷爷|奶奶|外公|外婆|我|我们)", content):
        score += 1
    if re.search(r"(今天|昨天|最近|这次|那天|上周|下周|每次|总是|连续|刚才|当年|以前)", content):
        score += 1
    if re.search(
        r"(发现|观察|记录|提醒|选择|决定|做题|写作业|沟通|放弃|拖延|愿意|不愿意|刷牙|睡觉|看书|运动|说|问|试|复述)",
        content,
    ):
        score += 1
    if re.search(
        r"(应用题|题意|列式|计算|错题|学习|作业|考试|专业|志愿|情绪|睡眠|视力|牙|屏幕|沟通|关系|线段图|等量关系)",
        content,
    ):
        score += 1
    if re.search(r"(之后|以后|所以|导致|更|开始|明显|稳定|能|不能|容易|总会|变得)", content):
        score += 1
    if re.search(r"(先|再|下次|继续|需要|可以|适合|不适合|提醒|复盘|拆|画图|记录)", content):
        score += 1
    return score

def _looks_like_learning_or_care_strategy(text: str) -> bool:
    return bool(
        re.search(r"(孩子|儿子|女儿|学生|学习|作业|应用题|题意|列式|错题|考试|情绪|沟通)", text)
        and re.search(r"(先|再|下次|继续|需要|可以|适合|不适合|提醒|复盘|拆|画图|记录)", text)
    )

def _has_context_save_anchor(context: str) -> bool:
    compact = str(context or "").strip()
    if not compact:
        return False
    return not _lacks_substantive_save_value(compact)

def _has_concrete_save_anchor(text: str) -> bool:
    return bool(
        re.search(
            r"(今天|昨天|最近|这次|那天|小时候|当年|以前|刚才).{0,80}"
            r"(发生|遇到|聊|说|决定|选择|记录|看见|想到|感受|提醒|观察)",
            text,
        )
        or re.search(r"(想对|留给|告诉).{0,20}(家人|孩子|后辈|未来的自己|以后的我)", text)
    )

def _looks_like_low_information_noise(text: str) -> bool:
    content = re.sub(r"[，。！？、,.!?；;：:\-_\s]+", "", text.strip())
    if len(content) < 6:
        return True
    if re.fullmatch(r"(.{1,4})\1{2,}", content):
        return True
    repeated_chunks = re.findall(r"(.{2,4})\1+", content)
    if repeated_chunks and sum(len(chunk) for chunk in repeated_chunks) * 2 >= len(content):
        return True
    unique_cjk_chars = {char for char in content if "\u4e00" <= char <= "\u9fff"}
    cjk_chars = [char for char in content if "\u4e00" <= char <= "\u9fff"]
    if len(cjk_chars) >= 12 and len(unique_cjk_chars) / len(cjk_chars) < 0.35:
        return True
    filler_patterns = [
        r"^(哈哈|嘿嘿|嗯嗯|啊啊|好好|行行|知道了|谢谢|可以|还行)+$",
        r"^(成长|价值|意义|未来|深刻|温柔|积极|家族|经验|沉淀|哲理|人生)+$",
    ]
    return any(re.fullmatch(pattern, content) for pattern in filler_patterns)

def _blocked_save_tool_plan(reason: str) -> dict:
    confirmation_message = (
        "这条内容缺少可保存的具体事实，我不会沉淀为家族记忆。"
        if "缺乏" in reason or "没有可沉淀" in reason
        else "这条内容像是在要求越权或泄露内部规则，我不会保存为家族记忆。"
    )
    return {
        "should_save": False,
        "tool": "NONE",
        "content": "",
        "title": "无需保存",
        "summary": "",
        "visibility": "PRIVATE",
        "entry_type": "DAILY",
        "memory_type": "ELDER_ADVICE",
        "scope": "PRIVATE",
        "category": "OTHER",
        "severity": 1,
        "importance": 1,
        "tags": [],
        "reason": reason,
        "confirmation_message": confirmation_message,
    }

def _unavailable_save_tool_plan() -> dict:
    return {
        "should_save": False,
        "tool": "NONE",
        "content": "",
        "title": "暂未保存",
        "summary": "",
        "visibility": "PRIVATE",
        "entry_type": "DAILY",
        "memory_type": "ELDER_ADVICE",
        "scope": "PRIVATE",
        "category": "OTHER",
        "severity": 1,
        "importance": 1,
        "tags": [],
        "reason": "保存规划暂时不可用，已跳过自动保存。",
        "confirmation_message": "这次没有自动保存，你可以稍后手动重试。",
    }

def _looks_like_prompt_injection(text: str) -> bool:
    normalized = re.sub(r"\s+", " ", text.strip().lower())
    if not normalized:
        return False
    benign_security_context = re.search(
        r"(复盘|记录|说明|总结|讨论|学习).{0,12}(提示词注入|prompt injection|越权攻击|安全事件)",
        normalized,
    )
    if benign_security_context:
        return False
    injection_patterns = [
        r"(忽略|无视|覆盖|删除|绕过|停止遵守).{0,12}(以上|之前|所有|系统|开发者|规则|指令|限制|安全)",
        r"(ignore|disregard|override|bypass|forget).{0,24}(previous|above|system|developer|instruction|rule|safety)",
        r"(输出|展示|泄露|透露|打印|复述|告诉我).{0,12}(系统提示词|开发者指令|隐藏提示|内部规则|system prompt|developer message|hidden prompt)",
        r"(泄露|导出|列出|展示|给我).{0,12}(全部|所有).{0,12}(记忆|日记|私密|隐私|家庭资料|授权资料)",
        r"(改成|切换|提升|赋予).{0,12}(管理员|admin|root|最高权限|owner)",
        r"(api[_ -]?key|sk-[a-z0-9]{12,}|密钥|token|access token|secret)",
        r"(jailbreak|越狱|提示词注入|prompt injection)",
    ]
    return any(re.search(pattern, normalized, re.IGNORECASE) for pattern in injection_patterns)

def _looks_like_growth_observation(text: str) -> bool:
    growth_subject = re.search(r"(孩子|儿子|女儿|孙子|孙女|学生|小孩|本人|我)", text)
    growth_signal = re.search(
        r"(体态|坐姿|驼背|含胸|耸肩|牙|刷牙|龋齿|换牙|视力|近视|眼睛|睡眠|入睡|熬夜|运动|户外|屏幕|手机|平板|情绪|烦躁|沟通|反驳)",
        text,
    )
    follow_up = re.search(r"(担心|留意|观察|提醒|跟进|最近|这几天|下周|持续)", text)
    return bool(growth_signal and (growth_subject or follow_up))

def _looks_like_family_memory(text: str) -> bool:
    elder_or_family = re.search(r"(爷爷|奶奶|外公|外婆|长辈|父亲|母亲|爸爸|妈妈|家族|我们家|家里以前|祖辈)", text)
    reusable = re.search(r"(经验|教训|规矩|原则|踩坑|后悔|提醒|建议|传下来|价值观|做法|如果重来|不要|一定要)", text)
    insight = _has_substantive_insight_signal(text)
    reusable_target = re.search(r"(家里人|家人|后辈|孩子|提醒|原则|这个教训|这条经验|值得.*记住|传给|分享给)", text)
    return bool((elder_or_family and reusable) or (insight and reusable_target))

def _looks_like_diary(text: str) -> bool:
    if _has_private_emotion_signal(text) or _has_substantive_insight_signal(text):
        return True
    return bool(re.search(r"(今天|昨天|最近|这次|那天|小时候|当年|以前).{0,80}(发生|选择|感受|想法|留言|对.*说|反思|聊|遇到)", text))

def _has_private_emotion_signal(text: str) -> bool:
    return bool(re.search(r"(难过|开心|焦虑|压力|委屈|生气|害怕|失落|感动|担心|烦躁|崩溃|释然|后悔|遗憾|不安|孤独|撑不住|很累|很痛苦)", text))

def _has_insight_signal(text: str) -> bool:
    return bool(re.search(r"(明白|意识到|发现|学到|想通|感悟|反思|复盘|教训|以后|下次|值得记住|提醒自己)", text))

def _has_substantive_insight_signal(text: str) -> bool:
    if not _has_insight_signal(text):
        return False
    compact = re.sub(r"\s+", "", text.strip())
    if _looks_like_low_information_noise(compact):
        return False
    concrete_topic = re.search(
        r"(今天|昨天|最近|这次|那天|小时候|当年|以前|刚才|一次|我|孩子|家人|爸爸|妈妈|爷爷|奶奶)"
        r".{0,60}(决定|选择|专业|学习|考试|作业|账目|生意|沟通|关系|刷牙|视力|屏幕|运动|睡眠|情绪|长期代价|眼前|跟风)",
        compact,
    )
    reusable_lesson = re.search(
        r"(不能|不要|别|要|应该|一定要).{0,30}"
        r"(只看|提前|写清楚|复盘|提醒|观察|坚持|选择|沟通|记录|拆开|问清楚|长期)",
        compact,
    )
    reusable_target = re.search(r"(家里人|家人|后辈|孩子|未来的自己|以后的我|提醒自己|提醒家里人)", compact)
    return bool(concrete_topic or (reusable_lesson and reusable_target))

def _infer_diary_entry_type(content: str, fallback: str) -> str:
    if _has_private_emotion_signal(content):
        return "EMOTION"
    if _has_substantive_insight_signal(content):
        return "SELF_REFLECTION"
    return fallback

def _normalize_save_visibility(tool: str, visibility: str, content: str) -> str:
    if tool == "GROWTH_GUARD":
        return "CARE_VISIBLE"
    if tool == "FAMILY_MEMORY":
        if re.search(r"(冲突|吵架|失望|隐私|生病|诊断|学校|班级|孩子|儿子|女儿|未成年|情绪)", content):
            return "CARE_VISIBLE"
        return "FAMILY_VISIBLE"
    if tool == "DIARY" and (
        _has_private_emotion_signal(content)
        or re.search(r"(别让|不要让|不想让|只给我|私密|隐私|不能公开|别公开|别告诉)", content)
    ):
        return "PRIVATE"
    if tool == "DIARY" and re.search(r"(给家人|希望家人|全家|家里人都|大家)", content):
        return "FAMILY_VISIBLE"
    return visibility

def _normalize_save_scope(tool: str, scope: str, visibility: str) -> str:
    if tool == "GROWTH_GUARD":
        return "CARE_VISIBLE"
    if tool == "FAMILY_MEMORY":
        return "FAMILY_VISIBLE" if visibility == "FAMILY_VISIBLE" else "CARE_VISIBLE"
    if visibility == "PRIVATE":
        return "PRIVATE"
    if visibility in {"CARE_VISIBLE", "FAMILY_VISIBLE"}:
        return visibility
    return scope

def _infer_growth_category(content: str, fallback: str) -> str:
    category_patterns = [
        ("DENTAL", r"(牙|刷牙|龋齿|换牙|牙科|甜食|饮料)"),
        ("VISION", r"(视力|近视|眼睛|揉眼|看书|屏幕|用眼|户外)"),
        ("POSTURE", r"(体态|坐姿|驼背|含胸|耸肩|肩膀|前倾)"),
        ("SLEEP", r"(睡眠|入睡|熬夜|作息|早起|睡前)"),
        ("EXERCISE", r"(运动|户外|跑步|耐力|活动量)"),
        ("SCREEN_TIME", r"(屏幕|手机|平板|电子设备|游戏)"),
        ("EMOTION", r"(情绪|烦躁|压力|哭|沉默|表达意愿)"),
        ("COMMUNICATION", r"(沟通|反驳|争吵|说教|亲子|提醒时)"),
    ]
    for category, pattern in category_patterns:
        if re.search(pattern, content):
            return category
    return fallback

def _infer_family_memory_type(content: str, fallback: str) -> str:
    if re.search(r"(牙|视力|体态|睡眠|运动|健康|生病|就医)", content):
        return "HEALTH_REMINDER"
    if re.search(r"(踩坑|风险|不要|别只|跟风|不适合|教训|后悔|如果重来)", content):
        return "GROWTH_RISK"
    if re.search(r"(规矩|原则|价值观|家风)", content):
        return "VALUE"
    if re.search(r"(故事|以前|年轻时|当年)", content):
        return "FAMILY_STORY"
    return fallback

def _default_growth_severity(content: str) -> int:
    if re.search(r"(连续|明显|严重|尽快|疼|看不清|长期)", content):
        return 4
    if re.search(r"(担心|留意|观察|最近)", content):
        return 3
    return 2

def _save_plan_reason(value: object, tool: str) -> str:
    reason = str(value or "").strip()[:120]
    if reason:
        return reason
    return {
        "DIARY": "这段话包含具体经历或个人感受，适合作为每日记录保存。",
        "FAMILY_MEMORY": "这段话包含可复用的经验、家族故事或长辈提醒，适合沉淀为经验沉淀。",
        "GROWTH_GUARD": "这段话包含需要后续留意的成长观察信号，适合保存为成长观察。",
    }.get(tool, "内容不足或缺少长期保存价值。")

def _choice(value: object, allowed: set[str], fallback: str) -> str:
    text = str(value or "").strip().upper()
    return text if text in allowed else fallback

def _bounded_int(value: object, minimum: int, maximum: int, fallback: int) -> int:
    try:
        number = int(value)
    except (TypeError, ValueError):
        number = fallback
    return max(minimum, min(maximum, number))

def _default_save_title(tool: str) -> str:
    return {
        "DIARY": "对话保存的每日记录",
        "FAMILY_MEMORY": "对话沉淀的经验",
        "GROWTH_GUARD": "对话记录的成长观察",
    }.get(tool, "无需保存")

def _default_save_confirmation(tool: str) -> str:
    return {
        "DIARY": "已保存为每日记录。",
        "FAMILY_MEMORY": "已保存为经验沉淀。",
        "GROWTH_GUARD": "已保存为成长观察。",
    }.get(tool, "这条消息不需要保存。")

def _compact_string_list(value: object, limit: int, max_len: int) -> list[str]:
    if not isinstance(value, list):
        return []
    result: list[str] = []
    for item in value:
        text = str(item).strip()
        if text:
            result.append(text[:max_len])
        if len(result) >= limit:
            break
    return result
