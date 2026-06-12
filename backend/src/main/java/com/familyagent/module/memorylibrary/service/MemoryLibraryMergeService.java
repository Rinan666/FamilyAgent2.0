package com.familyagent.module.memorylibrary.service;

import com.familyagent.common.constant.EntityStatus;
import com.familyagent.common.constant.MemoryType;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.family.service.FamilyService;
import com.familyagent.module.memory.dto.CreateFamilyMemoryRequest;
import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.repository.MemoryEntryRepository;
import com.familyagent.module.memory.service.MemoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 记忆库条目合并，从 MemoryLibraryService 拆出。
 */
@Component
@RequiredArgsConstructor
public class MemoryLibraryMergeService {

    private final FamilyService familyService;
    private final MemoryEntryRepository memoryEntryRepository;
    private final MemoryService memoryService;

    @Transactional
    public void mergeItems(Long familyId, String primaryItemId, String secondaryItemId) {
        if (familyId == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "familyId 不能为空");
        if (primaryItemId == null || secondaryItemId == null || primaryItemId.equals(secondaryItemId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择两条不同的经验记录再合并");
        }
        familyService.checkMembership(familyId);

        MemoryLibrarySupport.ParsedItemId primaryParsed = MemoryLibrarySupport.parseItemId(primaryItemId);
        MemoryLibrarySupport.ParsedItemId secondaryParsed = MemoryLibrarySupport.parseItemId(secondaryItemId);
        if (!"memory".equals(primaryParsed.prefix()) || !"memory".equals(secondaryParsed.prefix())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "当前仅支持合并家族经验类记忆");
        }

        MemoryEntry primary = requireActiveFamilyMemory(familyId, primaryParsed.id());
        MemoryEntry secondary = requireActiveFamilyMemory(familyId, secondaryParsed.id());
        MemoryLibrarySupport.ensureCreatorOrFamilyOwner(familyService, familyId, primary.getUserId(),
                "只能合并自己创建的经验，或由家族创建者合并");
        MemoryLibrarySupport.ensureCreatorOrFamilyOwner(familyService, familyId, secondary.getUserId(),
                "只能合并自己创建的经验，或由家族创建者合并");
        if (!primary.getType().equals(secondary.getType()) || !primary.getScope().equals(secondary.getScope())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅支持合并同类型、同可见范围的经验记录");
        }

        CreateFamilyMemoryRequest request = new CreateFamilyMemoryRequest();
        request.setFamilyId(familyId);
        request.setContent(secondary.getContent());
        request.setType(secondary.getType());
        request.setScope(secondary.getScope());
        request.setSummary(secondary.getSummary());
        request.setImportance(secondary.getImportance());
        request.setMemoryCard(memoryCardFromMetadata(secondary.getMetadata()));
        request.setMetadata(Map.of(
                "source", "MEMORY_LIBRARY_MERGE",
                "scenario", String.valueOf(
                        MemoryLibrarySupport.mutableMap(secondary.getMetadata()).getOrDefault("scenario", ""))));

        Map<String, Object> incomingMetadata = MemoryLibrarySupport.mutableMap(secondary.getMetadata());
        incomingMetadata.put("source", "MEMORY_LIBRARY_MERGE");
        incomingMetadata.put("mergedItemId", "memory-" + secondary.getId());
        incomingMetadata.put("mergedItemPreview", MemoryLibrarySupport.previewText(secondary.getContent(), 80));

        memoryService.mergeFamilyMemory(primary, request, incomingMetadata, CurrentUserGuard.currentUserId());

        Map<String, Object> secondaryMetadata = MemoryLibrarySupport.mutableMap(secondary.getMetadata());
        secondaryMetadata.put("archivedBy", CurrentUserGuard.currentUserId());
        secondaryMetadata.put("archivedAt", LocalDateTime.now().toString());
        secondaryMetadata.put("archiveSource", "MEMORY_LIBRARY_MERGE");
        secondaryMetadata.put("mergedIntoItemId", "memory-" + primary.getId());
        secondaryMetadata.put("mergedIntoSummary", MemoryLibrarySupport.previewText(primary.getSummary(), 120));
        secondary.setMetadata(secondaryMetadata);
        secondary.setStatus(EntityStatus.ARCHIVED.name());
        memoryEntryRepository.updateById(secondary);
    }

    private MemoryEntry requireActiveFamilyMemory(Long familyId, Long memoryId) {
        MemoryEntry entry = memoryEntryRepository.selectById(memoryId);
        if (entry == null || !familyId.equals(entry.getFamilyId())
                || !EntityStatus.ACTIVE.name().equals(entry.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (!MemoryType.names().contains(entry.getType())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "当前仅支持合并经验沉淀类记忆");
        }
        return entry;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> memoryCardFromMetadata(Object metadata) {
        if (metadata instanceof Map<?, ?> map) {
            Object card = map.get("memoryCard");
            if (card instanceof Map<?, ?> cardMap) return new java.util.HashMap<>((Map<String, Object>) cardMap);
        }
        return Map.of();
    }
}
