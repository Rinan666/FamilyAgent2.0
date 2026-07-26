"""Deterministic safety and value signals for memory save planning."""

import re


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
    if _looks_like_reusable_learning_strategy(text):
        return "FAMILY_MEMORY"
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
        r"(保存|存起来|记下来|记录一下|记录下来|沉淀下来|帮我记|帮我存|帮我保存|帮我记录|"
        r"保存到记忆库|加入记忆库|放到记忆库|收进记忆库)"
        r"(一下|起来)?(吧)?[。.!！?？]*",
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


def _looks_like_reusable_learning_strategy(text: str) -> bool:
    return bool(
        re.search(r"(学习|作业|应用题|题意|列式|错题|考试|计算|线段图|等量关系)", text)
        and re.search(
            r"(复述|画图|线段图|拆.{0,6}题意|先.{0,24}再|错题.{0,12}复盘|分步|验算)",
            text,
        )
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
