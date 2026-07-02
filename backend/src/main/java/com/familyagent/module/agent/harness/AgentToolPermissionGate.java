package com.familyagent.module.agent.harness;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.module.family.service.FamilyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Central permission gate for Agent tool calls.
 */
@Component
@RequiredArgsConstructor
public class AgentToolPermissionGate {

    private final FamilyService familyService;

    public void assertAllowed(AgentRunContext context, AgentToolDescriptor descriptor, Object input) {
        if (context == null || context.familyId() == null || context.viewerUserId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Agent tool context is incomplete");
        }
        if (descriptor == null || descriptor.name() == null || descriptor.name().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Agent tool descriptor is incomplete");
        }

        try {
            familyService.getFamilyMember(context.familyId(), context.viewerUserId());
        } catch (BusinessException e) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Agent tool permission denied");
        }
    }
}
