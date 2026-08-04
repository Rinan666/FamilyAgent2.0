package com.familyagent.module.session.facade;

import com.familyagent.common.constant.AgentContextType;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.module.session.dto.AgentSessionContext;
import com.familyagent.module.session.entity.ChatSession;
import com.familyagent.module.session.repository.ChatSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@RequiredArgsConstructor
public class AgentChatSessionFacade {

    private final ChatSessionRepository sessionRepository;

    public AgentSessionContext requireOwnedContext(
            Long sessionId,
            Long userId,
            Long familyId) {
        if (sessionId == null) {
            return AgentSessionContext.family();
        }
        ChatSession session = sessionRepository.findHeaderById(sessionId);
        if (session == null || !Objects.equals(session.getUserId(), userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (!Objects.equals(session.getFamilyId(), familyId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Chat session does not belong to the requested family");
        }
        return new AgentSessionContext(
                contextType(session.getAgentContextType()),
                session.getTargetUserId(),
                session.getTargetPersonaId());
    }

    public void updateOwnedContext(
            Long sessionId,
            Long userId,
            Long familyId,
            AgentSessionContext context) {
        if (sessionId == null || context == null) {
            return;
        }
        requireOwnedContext(sessionId, userId, familyId);
        sessionRepository.updateAgentContext(
                sessionId,
                context.contextType().name(),
                context.targetUserId(),
                context.targetPersonaId());
    }

    private static AgentContextType contextType(String value) {
        try {
            return AgentContextType.valueOf(value == null ? "FAMILY" : value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return AgentContextType.FAMILY;
        }
    }
}
