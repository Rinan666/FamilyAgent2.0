package com.familyagent.module.memory.dto;

import com.familyagent.common.constant.DiaryRecallSource;
import com.familyagent.common.constant.MemoryLibraryKind;
import com.familyagent.common.constant.MemoryOriginType;
import com.familyagent.common.constant.MemoryRecallSourceType;
import com.familyagent.module.memory.entity.MemoryEntry;

import java.util.Objects;

public record AuthorizedMemoryRecallCandidate(
        MemoryEntry entry,
        MemoryRecallSourceType sourceType,
        DiaryRecallSource mirrorSource) {

    public AuthorizedMemoryRecallCandidate {
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(sourceType, "sourceType");
    }

    public static AuthorizedMemoryRecallCandidate from(MemoryEntry entry) {
        return new AuthorizedMemoryRecallCandidate(entry, resolveSourceType(entry), null);
    }

    public AuthorizedMemoryRecallCandidate withMirrorSource(DiaryRecallSource source) {
        return new AuthorizedMemoryRecallCandidate(entry, sourceType, source);
    }

    public Long vectorSourceId() {
        return switch (sourceType) {
            case LIFE_RECORD, GROWTH_OBSERVATION ->
                    entry.getOriginId() == null ? entry.getId() : entry.getOriginId();
            case FAMILY_EXPERIENCE, PERSONAL_MEMORY -> entry.getId();
        };
    }

    public String publicId() {
        return sourceType.publicIdPrefix() + "-" + vectorSourceId();
    }

    public Long authorUserId() {
        return entry.getUserId();
    }

    public Long subjectUserId() {
        return entry.getRelatedUserId() == null ? entry.getUserId() : entry.getRelatedUserId();
    }

    private static MemoryRecallSourceType resolveSourceType(MemoryEntry entry) {
        if (MemoryLibraryKind.PERSONAL.name().equals(entry.getLibraryKind())) {
            return MemoryRecallSourceType.PERSONAL_MEMORY;
        }
        if (MemoryOriginType.DIARY.name().equals(entry.getOriginType())) {
            return MemoryRecallSourceType.LIFE_RECORD;
        }
        if (MemoryOriginType.GROWTH.name().equals(entry.getOriginType())) {
            return MemoryRecallSourceType.GROWTH_OBSERVATION;
        }
        return MemoryRecallSourceType.FAMILY_EXPERIENCE;
    }
}
