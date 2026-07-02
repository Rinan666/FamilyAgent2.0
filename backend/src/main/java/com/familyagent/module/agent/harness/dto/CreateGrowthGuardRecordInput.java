package com.familyagent.module.agent.harness.dto;

import java.time.LocalDate;

public record CreateGrowthGuardRecordInput(
        Long targetUserId,
        String category,
        String content,
        Integer severity,
        LocalDate observedAt,
        LocalDate followUpAt,
        String visibility
) {
}
