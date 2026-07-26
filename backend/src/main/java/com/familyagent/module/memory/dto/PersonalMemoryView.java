package com.familyagent.module.memory.dto;

import com.familyagent.module.memory.entity.MemoryEntry;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PersonalMemoryView(
        Long id,
        Long userId,
        String libraryKind,
        String type,
        String visibility,
        String content,
        String summary,
        Integer importance,
        BigDecimal confidence,
        String status,
        Object metadata,
        List<Long> selectedFamilyIds,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public PersonalMemoryView {
        selectedFamilyIds = selectedFamilyIds == null ? List.of() : List.copyOf(selectedFamilyIds);
    }

    public static PersonalMemoryView from(MemoryEntry entry, List<Long> selectedFamilyIds) {
        return new PersonalMemoryView(
                entry.getId(),
                entry.getUserId(),
                entry.getLibraryKind(),
                entry.getType(),
                entry.getScope(),
                entry.getContent(),
                entry.getSummary(),
                entry.getImportance(),
                entry.getConfidence(),
                entry.getStatus(),
                entry.getMetadata(),
                selectedFamilyIds,
                entry.getCreatedAt(),
                entry.getUpdatedAt());
    }
}
