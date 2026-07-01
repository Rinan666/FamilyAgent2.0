package com.familyagent.module.agent.harness.dto;

import java.util.List;

public record CreateDiaryEntryInput(
        String content,
        String entryType,
        String title,
        String mood,
        List<String> tags,
        String visibility
) {
}
