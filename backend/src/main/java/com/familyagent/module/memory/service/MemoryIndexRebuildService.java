package com.familyagent.module.memory.service;

import com.familyagent.module.family.facade.FamilyMembershipFacade;
import com.familyagent.module.memory.dto.RebuildEmbeddingResponse;
import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.repository.MemoryEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MemoryIndexRebuildService {

    private final MemoryEntryRepository memoryRepository;
    private final FamilyMembershipFacade familyMembershipFacade;
    private final UnifiedMemoryIndexMetadataAssembler metadataAssembler;

    public RebuildEmbeddingResponse rebuildFamilyIndexes(Long familyId, int limit) {
        familyMembershipFacade.checkMembership(familyId);
        List<MemoryEntry> entries = memoryRepository.findActiveFamilyEntriesForIndexing(
                familyId,
                normalizeLimit(limit));
        entries.forEach(this::rebuildIndex);
        UnifiedMemorySourceCounts counts = UnifiedMemorySourceCounts.from(entries);
        return RebuildEmbeddingResponse.builder()
                .familyId(familyId)
                .diaryCount(counts.diaries())
                .memoryCount(counts.memories())
                .growthRecordCount(counts.growthRecords())
                .indexedCount(entries.size())
                .build();
    }

    private void rebuildIndex(MemoryEntry entry) {
        if (entry == null || entry.getId() == null || entry.getContent() == null || entry.getContent().isBlank()) {
            return;
        }
        entry.setMetadata(metadataAssembler.enrich(entry));
        memoryRepository.updateById(entry);
    }

    private static int normalizeLimit(int limit) {
        if (limit <= 0) {
            return 200;
        }
        return Math.min(limit, 1000);
    }

}
