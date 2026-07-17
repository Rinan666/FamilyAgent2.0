package com.familyagent.module.memorylibrary.service;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.family.facade.MemoryLibraryFamilyFacade;
import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.facade.MemoryIndexingFacade;
import com.familyagent.module.memory.facade.MemoryLibraryMemoryFacade;
import com.familyagent.module.memory.service.MemoryIndexMetadataBuilder;
import com.familyagent.module.memorylibrary.dto.MemoryLibrarySearchRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 家族经验古文改写，从 MemoryLibraryService 拆出。
 */
@Component
@RequiredArgsConstructor
public class MemoryLibraryClassicalizeService {

    private final MemoryLibraryFamilyFacade familyService;
    private final MemoryLibraryMemoryFacade memoryEntryRepository;
    private final MemoryIndexingFacade memoryEmbeddingService;

    @Transactional
    public void classicalize(
            Long familyId,
            String itemId,
            String classicalText,
            String plainSummary,
            String styleNote) {
        if (familyId == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "familyId 不能为空");
        familyService.checkMembership(familyId);
        MemoryLibrarySupport.ParsedItemId parsed = MemoryLibrarySupport.parseItemId(itemId);
        if (!"memory".equals(parsed.prefix())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "当前仅支持对家族经验进行古文提炼");
        }

        String normalizedText = MemoryLibrarySupport.blankToNull(classicalText);
        if (normalizedText == null || normalizedText.length() < 8) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "古文稿过短，无法回写");
        }
        String normalizedSummary = MemoryLibrarySupport.blankToNull(plainSummary);
        if (normalizedSummary == null) normalizedSummary = MemoryLibrarySupport.previewText(normalizedText, 120);
        String normalizedStyleNote = MemoryLibrarySupport.blankToNull(styleNote);
        if (normalizedStyleNote == null) normalizedStyleNote = "MEMORY_LIBRARY_CLASSICALIZE";

        MemoryEntry entry = requireActiveFamilyExperienceMemory(familyId, parsed.id());
        familyService.ensureCreatorOrOwner(familyId, entry.getUserId(),
                "只能改写自己创建的经验，或由家族创建者改写");

        Map<String, Object> metadata = MemoryLibrarySupport.mutableMap(entry.getMetadata());
        Map<String, Object> classicalization = MemoryLibrarySupport.mutableMap(metadata.get("classicalization"));
        classicalization.putIfAbsent("originalContent", entry.getContent());
        if (MemoryLibrarySupport.blankToNull(entry.getSummary()) != null) {
            classicalization.putIfAbsent("originalSummary", entry.getSummary());
        }
        classicalization.put("plainSummary", normalizedSummary);
        classicalization.put("styleNote", normalizedStyleNote);
        classicalization.put("classicalizedAt", LocalDateTime.now().toString());
        classicalization.put("classicalizedBy", CurrentUserGuard.currentUserId());
        classicalization.put("source", "MEMORY_LIBRARY_CLASSICALIZE");
        metadata.put("classicalization", classicalization);

        entry.setContent(normalizedText.trim());
        entry.setSummary(MemoryLibrarySupport.truncateText(normalizedSummary, 200));
        entry.setMetadata(MemoryIndexMetadataBuilder.enrichFamilyMemory(
                metadata, entry.getContent(), entry.getSummary(),
                entry.getType(), entry.getImportance() == null ? 3 : entry.getImportance()));
        memoryEntryRepository.update(entry);
        memoryEmbeddingService.indexMemoryAfterCommit(entry);
    }

    private MemoryEntry requireActiveFamilyExperienceMemory(Long familyId, Long memoryId) {
        MemoryEntry entry = requireActiveFamilyMemory(familyId, memoryId);
        if (MemoryLibrarySupport.isLegacyAiSummary(entry.getMetadata())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "AI 摘要不能作为家族经验进行改写");
        }
        return entry;
    }

    private MemoryEntry requireActiveFamilyMemory(Long familyId, Long memoryId) {
        MemoryEntry entry = memoryEntryRepository.findById(memoryId);
        if (entry == null || !familyId.equals(entry.getFamilyId())
                || !"ACTIVE".equals(entry.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return entry;
    }
}
