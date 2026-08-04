package com.familyagent.module.agent.service;

import com.familyagent.common.constant.AgentAnswerDepth;
import com.familyagent.common.constant.AgentContextScope;
import com.familyagent.common.constant.AgentContextType;
import com.familyagent.common.constant.AgentWebSearchPolicy;
import com.familyagent.common.constant.MemoryRecallDepth;
import com.familyagent.module.agent.dto.AgentChatStreamRequest;
import com.familyagent.module.agent.dto.AgentIntentPlan;
import com.familyagent.module.agent.dto.AgentResponsePlan;
import com.familyagent.module.family.facade.AgentContextTarget;
import com.familyagent.module.family.facade.AgentContextTargetCatalog;
import com.familyagent.module.family.facade.AgentContextTargetFacade;
import com.familyagent.module.session.dto.AgentSessionContext;
import com.familyagent.module.session.facade.AgentChatSessionFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class AgentChatIntentResolver {

    private static final Pattern SESSION_SWITCH = Pattern.compile(
            "(?:切换到|切到|接下来(?:请)?(?:和|让)|以后(?:请)?(?:和|让))([^，。！？,.!?]{1,24})");
    private static final Pattern TURN_PERSPECTIVE = Pattern.compile(
            "(?:从|以)([^，。！？,.!?]{1,24}?)(?:的)?(?:角度|视角|立场)(?:来看|分析|说说|回答)?");
    private static final List<String> FAMILY_RESET_TERMS = List.of(
            "回到家庭agent", "切回家庭agent", "回到家庭助手", "退出镜像", "退出精神成员", "恢复普通对话");
    private static final List<String> BRIEF_TERMS = List.of(
            "简单说", "简短", "概括", "大概", "一句话", "brief", "short answer");
    private static final List<String> DEEP_TERMS = List.of(
            "详细", "深入", "全面", "完整梳理", "系统分析", "展开说", "深挖", "deep dive");
    private static final List<String> DECISION_TERMS = List.of(
            "怎么选", "该不该", "要不要", "怎么办", "建议", "决定", "选择", "取舍", "迷茫", "复盘", "利弊", "值不值得");
    private static final List<String> NO_RECALL_TERMS = List.of(
            "润色", "翻译", "改写", "写代码", "计算", "生成标题", "检查语法");
    private static final List<String> GREETINGS = List.of(
            "你好", "嗨", "hello", "hi", "谢谢", "在吗");
    private static final List<String> WEB_REQUIRED_TERMS = List.of(
            "联网", "搜索", "查一下", "最新", "新闻", "政策", "价格", "天气", "现任", "实时");
    private static final List<String> WEB_DISABLED_TERMS = List.of(
            "不要联网", "不用联网", "只根据家族", "只看家里", "不查网络");

    private final AgentChatSessionFacade sessionFacade;
    private final AgentContextTargetFacade targetFacade;

    public AgentIntentPlan resolve(AgentChatStreamRequest request, Long userId) {
        AgentSessionContext storedContext = sessionFacade.requireOwnedContext(
                request.getSessionId(), userId, request.getFamilyId());
        ResolvedContext current = currentContext(request, storedContext);
        persistManualContextSelection(request, userId, storedContext, current);
        String message = normalizedMessage(request.getMemberMessage());
        AgentResponsePlan responsePlan = responsePlan(message, current.type());

        if (containsAny(compact(message), FAMILY_RESET_TERMS)) {
            AgentIntentPlan plan = new AgentIntentPlan(
                    AgentContextType.FAMILY,
                    AgentContextScope.SESSION,
                    null,
                    null,
                    "家庭 Agent",
                    removeMatchedPhrase(message, FAMILY_RESET_TERMS),
                    responsePlan,
                    current.type() != AgentContextType.FAMILY,
                    responseTextWhenEmpty(message, removeMatchedPhrase(message, FAMILY_RESET_TERMS), "已切回家庭 Agent。"));
            persistSessionContext(request, userId, plan);
            return plan;
        }

        Matcher sessionMatcher = SESSION_SWITCH.matcher(message);
        if (sessionMatcher.find()) {
            AgentIntentPlan plan = switchTarget(
                    request,
                    userId,
                    current,
                    sessionMatcher.group(1),
                    AgentContextScope.SESSION,
                    removeRange(message, sessionMatcher.start(), sessionMatcher.end()),
                    responsePlan);
            persistSessionContext(request, userId, plan);
            return plan;
        }

        Matcher turnMatcher = TURN_PERSPECTIVE.matcher(message);
        if (turnMatcher.find()) {
            return switchTarget(
                    request,
                    userId,
                    current,
                    turnMatcher.group(1),
                    AgentContextScope.TURN,
                    message,
                    responsePlan);
        }

        return new AgentIntentPlan(
                current.type(),
                AgentContextScope.TURN,
                current.targetUserId(),
                current.targetPersonaId(),
                current.label(),
                message,
                responsePlan,
                false,
                null);
    }

    private AgentIntentPlan switchTarget(
            AgentChatStreamRequest request,
            Long userId,
            ResolvedContext current,
            String requestedName,
            AgentContextScope scope,
            String effectiveMessage,
            AgentResponsePlan responsePlan) {
        AgentContextTargetCatalog catalog = targetFacade.listAuthorizedTargets(request.getFamilyId());
        List<TargetMatch> matches = new ArrayList<>();
        catalog.members().stream()
                .filter(target -> target.matches(requestedName))
                .map(target -> new TargetMatch(AgentContextType.MIRROR, target))
                .forEach(matches::add);
        catalog.personas().stream()
                .filter(target -> target.matches(requestedName))
                .map(target -> new TargetMatch(AgentContextType.PERSONA, target))
                .forEach(matches::add);

        if (matches.size() != 1) {
            String clarification = matches.isEmpty()
                    ? "我没有找到可切换的授权成员。你可以点开上下文列表选择成员。"
                    : "这个称呼对应多个成员，请在上下文列表中明确选择一个。";
            return new AgentIntentPlan(
                    current.type(),
                    AgentContextScope.TURN,
                    current.targetUserId(),
                    current.targetPersonaId(),
                    current.label(),
                    effectiveMessage,
                    responsePlan,
                    false,
                    clarification);
        }

        TargetMatch match = matches.get(0);
        boolean changed = match.type() != current.type()
                || (match.type() == AgentContextType.MIRROR
                && !match.target().id().equals(current.targetUserId()))
                || (match.type() == AgentContextType.PERSONA
                && !match.target().id().equals(current.targetPersonaId()));
        String directResponse = responseTextWhenEmpty(
                request.getMemberMessage(),
                effectiveMessage,
                match.type() == AgentContextType.MIRROR
                        ? "已切换到“" + match.target().displayName() + "”镜像参考。"
                        : "已切换到精神成员“" + match.target().displayName() + "”。");
        return new AgentIntentPlan(
                match.type(),
                scope,
                match.type() == AgentContextType.MIRROR ? match.target().id() : null,
                match.type() == AgentContextType.PERSONA ? match.target().id() : null,
                match.target().displayName(),
                effectiveMessage,
                responsePlan,
                changed,
                directResponse);
    }

    private void persistSessionContext(AgentChatStreamRequest request, Long userId, AgentIntentPlan plan) {
        if (!plan.contextChanged() || plan.contextScope() != AgentContextScope.SESSION || plan.hasDirectResponse()
                && plan.targetLabel() == null) {
            return;
        }
        sessionFacade.updateOwnedContext(
                request.getSessionId(),
                userId,
                request.getFamilyId(),
                new AgentSessionContext(plan.contextType(), plan.targetUserId(), plan.targetPersonaId()));
    }

    private ResolvedContext currentContext(AgentChatStreamRequest request, AgentSessionContext stored) {
        if (request.getTargetUserId() != null) {
            AgentContextTarget target = targetFacade.requireMember(request.getFamilyId(), request.getTargetUserId());
            return new ResolvedContext(AgentContextType.MIRROR, target.id(), null, target.displayName());
        }
        if (request.getTargetPersonaId() != null) {
            AgentContextTarget target = targetFacade.requirePersona(request.getFamilyId(), request.getTargetPersonaId());
            return new ResolvedContext(AgentContextType.PERSONA, null, target.id(), target.displayName());
        }
        if ("family_memory".equalsIgnoreCase(request.getKnowledgePoint())) {
            return new ResolvedContext(AgentContextType.FAMILY, null, null, "家庭 Agent");
        }
        if (stored.contextType() == AgentContextType.MIRROR && stored.targetUserId() != null) {
            AgentContextTarget target = targetFacade.requireMember(request.getFamilyId(), stored.targetUserId());
            return new ResolvedContext(AgentContextType.MIRROR, target.id(), null, target.displayName());
        }
        if (stored.contextType() == AgentContextType.PERSONA && stored.targetPersonaId() != null) {
            AgentContextTarget target = targetFacade.requirePersona(request.getFamilyId(), stored.targetPersonaId());
            return new ResolvedContext(AgentContextType.PERSONA, null, target.id(), target.displayName());
        }
        return new ResolvedContext(AgentContextType.FAMILY, null, null, "家庭 Agent");
    }

    private void persistManualContextSelection(
            AgentChatStreamRequest request,
            Long userId,
            AgentSessionContext stored,
            ResolvedContext current) {
        if (request.getSessionId() == null) {
            return;
        }
        boolean changed = stored.contextType() != current.type()
                || !java.util.Objects.equals(stored.targetUserId(), current.targetUserId())
                || !java.util.Objects.equals(stored.targetPersonaId(), current.targetPersonaId());
        if (changed) {
            sessionFacade.updateOwnedContext(
                    request.getSessionId(),
                    userId,
                    request.getFamilyId(),
                    new AgentSessionContext(current.type(), current.targetUserId(), current.targetPersonaId()));
        }
    }

    private static AgentResponsePlan responsePlan(String message, AgentContextType contextType) {
        String normalized = compact(message);
        AgentAnswerDepth answerDepth = containsAny(normalized, DEEP_TERMS)
                ? AgentAnswerDepth.DEEP
                : containsAny(normalized, BRIEF_TERMS) ? AgentAnswerDepth.BRIEF : AgentAnswerDepth.STANDARD;
        boolean decisionSupport = containsAny(normalized, DECISION_TERMS);
        MemoryRecallDepth recallDepth;
        if (isGreeting(normalized) || containsAny(normalized, NO_RECALL_TERMS)) {
            recallDepth = MemoryRecallDepth.NONE;
        } else if (answerDepth == AgentAnswerDepth.DEEP) {
            recallDepth = MemoryRecallDepth.DEEP;
        } else if (answerDepth == AgentAnswerDepth.BRIEF) {
            recallDepth = MemoryRecallDepth.BRIEF;
        } else {
            recallDepth = MemoryRecallDepth.STANDARD;
        }
        if (contextType != AgentContextType.FAMILY && recallDepth == MemoryRecallDepth.NONE && !isGreeting(normalized)) {
            recallDepth = MemoryRecallDepth.BRIEF;
        }
        AgentWebSearchPolicy webPolicy = containsAny(normalized, WEB_DISABLED_TERMS)
                ? AgentWebSearchPolicy.NONE
                : containsAny(normalized, WEB_REQUIRED_TERMS)
                ? AgentWebSearchPolicy.REQUIRED
                : AgentWebSearchPolicy.AUTO;
        return new AgentResponsePlan(answerDepth, recallDepth, webPolicy, decisionSupport, false);
    }

    private static boolean isGreeting(String normalized) {
        return normalized.length() <= 12 && GREETINGS.stream().anyMatch(normalized::equals);
    }

    private static String responseTextWhenEmpty(String original, String effective, String response) {
        return effective == null || effective.isBlank() || compact(original).equals(compact(response)) ? response : null;
    }

    private static String removeMatchedPhrase(String message, List<String> phrases) {
        String result = message;
        for (String phrase : phrases) {
            result = result.replace(phrase, " ");
        }
        return result.replaceAll("^[，。！？,.!?\s]+|[，。！？,.!?\s]+$", "").trim();
    }

    private static String removeRange(String value, int start, int end) {
        return (value.substring(0, start) + " " + value.substring(end))
                .replaceAll("^[，。！？,.!?\s]+|[，。！？,.!?\s]+$", "")
                .trim();
    }

    private static boolean containsAny(String text, List<String> terms) {
        return terms.stream().anyMatch(text::contains);
    }

    private static String normalizedMessage(String value) {
        return value == null ? "" : value.trim();
    }

    private static String compact(String value) {
        return normalizedMessage(value).toLowerCase(Locale.ROOT).replaceAll("\s+", "");
    }

    private record ResolvedContext(
            AgentContextType type,
            Long targetUserId,
            Long targetPersonaId,
            String label) {
    }

    private record TargetMatch(AgentContextType type, AgentContextTarget target) {
    }
}
