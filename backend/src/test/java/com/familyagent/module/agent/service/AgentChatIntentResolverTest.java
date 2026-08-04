package com.familyagent.module.agent.service;

import com.familyagent.common.constant.AgentAnswerDepth;
import com.familyagent.common.constant.AgentContextScope;
import com.familyagent.common.constant.AgentContextType;
import com.familyagent.common.constant.MemoryRecallDepth;
import com.familyagent.module.agent.dto.AgentChatStreamRequest;
import com.familyagent.module.agent.dto.AgentIntentPlan;
import com.familyagent.module.family.facade.AgentContextTarget;
import com.familyagent.module.family.facade.AgentContextTargetCatalog;
import com.familyagent.module.family.facade.AgentContextTargetFacade;
import com.familyagent.module.session.dto.AgentSessionContext;
import com.familyagent.module.session.facade.AgentChatSessionFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentChatIntentResolverTest {

    private final AgentChatSessionFacade sessionFacade = mock(AgentChatSessionFacade.class);
    private final AgentContextTargetFacade targetFacade = mock(AgentContextTargetFacade.class);
    private final AgentChatIntentResolver resolver = new AgentChatIntentResolver(sessionFacade, targetFacade);

    @BeforeEach
    void setUp() {
        when(sessionFacade.requireOwnedContext(88L, 101L, 10L)).thenReturn(AgentSessionContext.family());
        when(targetFacade.listAuthorizedTargets(10L)).thenReturn(new AgentContextTargetCatalog(
                List.of(new AgentContextTarget(202L, "爸爸", List.of("爸爸", "父亲"))),
                List.of(new AgentContextTarget(303L, "苏轼", List.of("苏轼")))));
    }

    @Test
    void explicitSwitch_persistsAuthorizedMirrorContext() {
        AgentChatStreamRequest request = request("切换到爸爸的镜像");

        AgentIntentPlan plan = resolver.resolve(request, 101L);

        assertEquals(AgentContextType.MIRROR, plan.contextType());
        assertEquals(AgentContextScope.SESSION, plan.contextScope());
        assertEquals(202L, plan.targetUserId());
        assertTrue(plan.contextChanged());
        assertTrue(plan.hasDirectResponse());
        verify(sessionFacade).updateOwnedContext(
                org.mockito.ArgumentMatchers.eq(88L),
                org.mockito.ArgumentMatchers.eq(101L),
                org.mockito.ArgumentMatchers.eq(10L),
                any(AgentSessionContext.class));
    }

    @Test
    void perspectiveRequest_isTurnScopedAndUsesDeepDecisionPlan() {
        AgentChatStreamRequest request = request("从爸爸的角度详细分析我该不该辞职");

        AgentIntentPlan plan = resolver.resolve(request, 101L);

        assertEquals(AgentContextType.MIRROR, plan.contextType());
        assertEquals(AgentContextScope.TURN, plan.contextScope());
        assertEquals(AgentAnswerDepth.DEEP, plan.responsePlan().answerDepth());
        assertEquals(MemoryRecallDepth.DEEP, plan.responsePlan().recallDepth());
        assertTrue(plan.responsePlan().decisionSupport());
        assertFalse(plan.hasDirectResponse());
        verify(sessionFacade, never()).updateOwnedContext(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                any(AgentSessionContext.class));
    }

    @Test
    void unknownTarget_doesNotExposeOrSwitchContext() {
        AgentIntentPlan plan = resolver.resolve(request("切换到陌生人的镜像"), 101L);

        assertEquals(AgentContextType.FAMILY, plan.contextType());
        assertTrue(plan.hasDirectResponse());
        assertTrue(plan.directResponseMessage().contains("没有找到可切换的授权成员"));
        verify(sessionFacade, never()).updateOwnedContext(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                any(AgentSessionContext.class));
    }

    @Test
    void greeting_skipsFamilyRecall() {
        AgentIntentPlan plan = resolver.resolve(request("你好"), 101L);

        assertEquals(MemoryRecallDepth.NONE, plan.responsePlan().recallDepth());
    }

    private static AgentChatStreamRequest request(String message) {
        AgentChatStreamRequest request = new AgentChatStreamRequest();
        request.setMemberMessage(message);
        request.setFamilyId(10L);
        request.setSessionId(88L);
        request.setKnowledgePoint("family_memory");
        return request;
    }
}
