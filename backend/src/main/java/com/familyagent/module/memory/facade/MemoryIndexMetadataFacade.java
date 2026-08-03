package com.familyagent.module.memory.facade;

import com.familyagent.module.memory.service.MemoryIndexMetadataBuilder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;

@Component
public class MemoryIndexMetadataFacade {

    public Map<String, Object> enrichDiary(
            Map<String, Object> metadata,
            String content,
            String entryType,
            String mood,
            String[] tags) {
        return MemoryIndexMetadataBuilder.enrichDiary(metadata, content, entryType, mood, tags);
    }

    public Map<String, Object> enrichGrowth(
            Map<String, Object> metadata,
            String content,
            String category,
            int severity,
            LocalDate observedAt) {
        return MemoryIndexMetadataBuilder.enrichGrowth(
                metadata,
                content,
                category,
                severity,
                observedAt);
    }
}
