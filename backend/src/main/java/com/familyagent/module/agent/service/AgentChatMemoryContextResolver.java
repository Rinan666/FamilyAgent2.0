package com.familyagent.module.agent.service;

import com.familyagent.module.agent.dto.AgentChatStreamRequest;
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

    public AgentChatMemoryResolution resolve(AgentChatStreamRequest request, AgentRunContext runContext) {
        if (request.shouldUseServerMirrorContext()) {
            return AgentChatMemoryResolution.contextOnly(mirrorContextFacade.buildMirrorAgentContext(
                    request.getFamilyId(),
                    request.getTargetUserId(),
                    request.getMemberMessage()));
        }
        if (request.shouldUseServerPersonaContext()) {
            String personaContext = personaContextFacade.buildPersonaAgentContext(
                    request.getFamilyId(),
                    request.getTargetPersonaId());
            if (!request.isThinkMode()) {
                return AgentChatMemoryResolution.contextOnly(personaContext);
            }
            AgentChatMemoryResolution familyContext = buildFamilyMemoryContext(request, runContext);
            String context = familyContext.context().isBlank()
                    ? personaContext
                    : personaContext + "\n\nfamily_visible_reference:\n" + familyContext.context();
            return new AgentChatMemoryResolution(context, familyContext.metadata());
        }
        if (!request.shouldUseServerFamilyMemoryContext()) {
            return AgentChatMemoryResolution.contextOnly(request.getMemoryContext());
        }
        return buildFamilyMemoryContext(request, runContext);
    }

    private AgentChatMemoryResolution buildFamilyMemoryContext(
            AgentChatStreamRequest request,
            AgentRunContext runContext) {
        RecallFamilyMemoryInput input = new RecallFamilyMemoryInput(
                request.getMemberMessage(),
                request.userHistoryContents());
        AgentToolCallResult<RecallFamilyMemoryOutput> result = toolExecutor.execute(new AgentToolCallRequest<>(
                AgentToolName.RECALL_FAMILY_MEMORY.value(),
                runContext,
                input));
        if (!result.success() || result.data() == null) {
            return AgentChatMemoryResolution.empty();
        }
        return new AgentChatMemoryResolution(result.data().context(), result.data().metadata().toMap());
    }
}
