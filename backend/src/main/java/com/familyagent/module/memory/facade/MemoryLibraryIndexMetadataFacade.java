package com.familyagent.module.memory.facade;

import com.familyagent.module.memory.service.MemoryIndexMetadataBuilder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;

@Component
public class MemoryLibraryIndexMetadataFacade {

    public Map<String, Object> enrichDiary(
            Map<String, Object> metadata,
            String content,
            String entryType,
            String mood,
            String[] tags) {
        return MemoryIndexMetadataBuilder.enrichDiary(metadata, content, entryType, mood, tags);
    }

    public Map<String, Object> enrichMemory(
            Map<String, Object> metadata,
            String content,
            String summary,
            String memoryType,
            int importance) {
        return MemoryIndexMetadataBuilder.enrichFamilyMemory(
                metadata,
                content,
                summary,
                memoryType,
                importance);
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
