package com.familyagent.module.agent.harness.dto;

import com.familyagent.module.agent.harness.entity.AgentToolConfirmationRecord;

import java.time.LocalDateTime;

public record AgentToolConfirmationVO(
        Long id,
        String toolName,
        Long familyId,
        Long viewerUserId,
        String requestId,
        String status,
        LocalDateTime expiresAt,
        LocalDateTime decidedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static AgentToolConfirmationVO from(AgentToolConfirmationRecord record) {
        return new AgentToolConfirmationVO(
                record.getId(),
                record.getToolName(),
                record.getFamilyId(),
                record.getViewerUserId(),
                record.getRequestId(),
                record.getStatus(),
                record.getExpiresAt(),
                record.getDecidedAt(),
                record.getCreatedAt(),
                record.getUpdatedAt());
    }
}
