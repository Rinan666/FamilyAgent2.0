package com.familyagent.module.agent.service;

import com.familyagent.module.agent.dto.AgentChatStreamRequest;
import com.familyagent.module.agent.dto.AgentIntentPlan;
import com.familyagent.common.constant.AgentContextType;
import com.familyagent.common.constant.MemoryRecallDepth;
import com.familyagent.module.agent.harness.AgentRunContext;
import com.familyagent.module.agent.harness.AgentToolExecutor;
import com.familyagent.module.agent.harness.constant.AgentToolName;
import com.familyagent.module.agent.harness.dto.AgentToolCallRequest;
import com.familyagent.module.agent.harness.dto.AgentToolCallResult;
import com.familyagent.module.agent.harness.dto.RecallFamilyMemoryInput;
import com.familyagent.module.agent.harness.dto.RecallFamilyMemoryOutput;
import com.familyagent.module.family.facade.AgentPersonaContextFacade;
import com.familyagent.module.mirror.facade.AgentMirrorContextFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Resolves trusted memory context for Agent chat requests.
 */
@Component
@RequiredArgsConstructor
public class AgentChatMemoryContextResolver {

    private final AgentToolExecutor toolExecutor;
    private final AgentMirrorContextFacade mirrorContextFacade;
    private final AgentPersonaContextFacade personaContextFacade;
    private final AgentMemoryRecallPlanFactory recallPlanFactory;

    public AgentChatMemoryResolution resolve(
            AgentChatStreamRequest request,
            AgentIntentPlan intentPlan,
            AgentRunContext runContext) {
        if (intentPlan.contextType() == AgentContextType.MIRROR) {
            String mirrorContext = mirrorContextFacade.buildMirrorAgentContext(
                    request.getFamilyId(),
                    intentPlan.targetUserId(),
                    intentPlan.effectiveMessage());
            AgentChatMemoryResolution recalled = buildFamilyMemoryContext(request, intentPlan, runContext);
            String context = recalled.context().isBlank()
                    ? mirrorContext
                    : mirrorContext + "\n\nauthorized_record_reference:\n" + recalled.context();
            return new AgentChatMemoryResolution(context, recalled.metadata());
        }
        if (intentPlan.contextType() == AgentContextType.PERSONA) {
            String personaContext = personaContextFacade.buildPersonaAgentContext(
                    request.getFamilyId(),
                    intentPlan.targetPersonaId());
            if (intentPlan.responsePlan().recallDepth() == MemoryRecallDepth.NONE) {
                return AgentChatMemoryResolution.contextOnly(personaContext);
            }
            AgentChatMemoryResolution familyContext = buildFamilyMemoryContext(request, intentPlan, runContext);
            String context = familyContext.context().isBlank()
                    ? personaContext
                    : personaContext + "\n\nfamily_visible_reference:\n" + familyContext.context();
            return new AgentChatMemoryResolution(context, familyContext.metadata());
        }
        if (intentPlan.responsePlan().recallDepth() == MemoryRecallDepth.NONE) {
            return AgentChatMemoryResolution.empty();
        }
        return buildFamilyMemoryContext(request, intentPlan, runContext);
    }

    private AgentChatMemoryResolution buildFamilyMemoryContext(
            AgentChatStreamRequest request,
            AgentIntentPlan intentPlan,
            AgentRunContext runContext) {
        RecallFamilyMemoryInput input = new RecallFamilyMemoryInput(
                intentPlan.effectiveMessage(),
                request.userHistoryContents(),
                recallPlanFactory.create(request, intentPlan));
        AgentToolCallResult<RecallFamilyMemoryOutput> result = toolExecutor.execute(new AgentToolCallRequest<>(
                AgentToolName.RECALL_FAMILY_MEMORY.value(),
                runContext,
                input));
        if (!result.success() || result.data() == null) {
            return AgentChatMemoryResolution.empty();
        }
        return new AgentChatMemoryResolution(result.data().context(), result.data().metadata());
    }
}
