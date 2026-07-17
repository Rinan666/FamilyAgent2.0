package com.familyagent.module.memory.service;

import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.diary.facade.MemoryIndexDiaryFacade;
import com.familyagent.module.family.facade.FamilyMembershipFacade;
import com.familyagent.module.growth.entity.GrowthGuardRecord;
import com.familyagent.module.growth.facade.MemoryIndexGrowthFacade;
import com.familyagent.module.memory.dto.RebuildEmbeddingResponse;
import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.repository.MemoryEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MemoryIndexRebuildService {

    private final MemoryIndexDiaryFacade diaryIndexFacade;
    private final MemoryEntryRepository memoryRepository;
    private final MemoryIndexGrowthFacade growthIndexFacade;
    private final FamilyMembershipFacade familyMembershipFacade;

    public RebuildEmbeddingResponse rebuildFamilyIndexes(Long familyId, int limit) {
        familyMembershipFacade.checkMembership(familyId);
        int normalizedLimit = normalizeLimit(limit);
        List<DiaryEntry> diaries = diaryIndexFacade.findActiveByFamily(familyId, normalizedLimit);
        List<MemoryEntry> memories = memoryRepository.findActiveByFamilyForIndexing(familyId, normalizedLimit);
        List<GrowthGuardRecord> growthRecords = growthIndexFacade.findActiveByFamily(familyId, normalizedLimit);

        diaries.forEach(this::rebuildDiaryIndex);
        memories.forEach(this::rebuildMemoryIndex);
        growthRecords.forEach(this::rebuildGrowthIndex);

        return RebuildEmbeddingResponse.builder()
                .familyId(familyId)
                .diaryCount(diaries.size())
                .memoryCount(memories.size())
                .growthRecordCount(growthRecords.size())
                .indexedCount(diaries.size() + memories.size() + growthRecords.size())
                .build();
    }

    private void rebuildDiaryIndex(DiaryEntry entry) {
        if (entry == null || entry.getId() == null || isBlank(entry.getRawText())) {
            return;
        }
        entry.setMetadata(MemoryIndexMetadataBuilder.enrichDiary(
                mutableMetadata(entry.getMetadata()),
                entry.getRawText(),
                textFromMap(entry.getStructured(), "entryType", "DAILY"),
                entry.getMood(),
                entry.getTags()));
        diaryIndexFacade.update(entry);
    }

    private void rebuildMemoryIndex(MemoryEntry entry) {
        if (entry == null || entry.getId() == null || isBlank(entry.getContent())) {
            return;
        }
        entry.setMetadata(MemoryIndexMetadataBuilder.enrichFamilyMemory(
                mutableMetadata(entry.getMetadata()),
                entry.getContent(),
                entry.getSummary(),
                entry.getType(),
                entry.getImportance() == null ? 3 : entry.getImportance()));
        memoryRepository.updateById(entry);
    }

    private void rebuildGrowthIndex(GrowthGuardRecord record) {
        if (record == null || record.getId() == null || isBlank(record.getContent())) {
            return;
        }
        record.setMetadata(MemoryIndexMetadataBuilder.enrichGrowth(
                mutableMetadata(record.getMetadata()),
                record.getContent(),
                record.getCategory(),
                record.getSeverity() == null ? 3 : record.getSeverity(),
                record.getObservedAt()));
        growthIndexFacade.update(record);
    }

    private static int normalizeLimit(int limit) {
        if (limit <= 0) {
            return 200;
        }
        return Math.min(limit, 1000);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mutableMetadata(Object metadata) {
        if (metadata instanceof Map<?, ?> map) {
            return new HashMap<>((Map<String, Object>) map);
        }
        return new HashMap<>();
    }

    private static String textFromMap(Object value, String key, String fallback) {
        if (value instanceof Map<?, ?> map && map.get(key) != null) {
            return String.valueOf(map.get(key));
        }
        return fallback;
    }
}
