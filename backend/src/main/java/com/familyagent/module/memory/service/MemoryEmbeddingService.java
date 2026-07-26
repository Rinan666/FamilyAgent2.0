package com.familyagent.module.memory.service;

import com.familyagent.common.constant.EntityStatus;
import com.familyagent.common.constant.MemoryEmbeddingSourceType;
import com.familyagent.common.constant.MemoryLibraryKind;
import com.familyagent.infra.ai.AIServiceClient;
import com.familyagent.infra.ai.dto.EmbeddingRequest;
import com.familyagent.infra.ai.dto.EmbeddingResponse;
import com.familyagent.module.family.facade.FamilyMembershipFacade;
import com.familyagent.module.memory.dto.RebuildEmbeddingResponse;
import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.repository.MemoryEmbeddingWriteRepository;
import com.familyagent.module.memory.repository.MemoryEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryEmbeddingService {

    private static final int EMBEDDING_DIMENSIONS = 1536;

    private final AIServiceClient aiServiceClient;
    private final MemoryEmbeddingWriteRepository embeddingWriteRepository;
    private final MemoryEntryRepository memoryRepository;
    private final FamilyMembershipFacade familyMembershipFacade;
    private final EmbeddingAsyncProcessor asyncProcessor;
    private final MemoryIndexRebuildService indexRebuildService;

    public RebuildEmbeddingResponse rebuildFamilyEmbeddings(Long familyId, int limit) {
        familyMembershipFacade.checkMembership(familyId);
        List<MemoryEntry> entries = memoryRepository.findActiveFamilyEntriesForIndexing(
                familyId,
                normalizeLimit(limit));
        entries.forEach(this::indexMemoryAfterCommit);
        UnifiedMemorySourceCounts counts = UnifiedMemorySourceCounts.from(entries);
        return RebuildEmbeddingResponse.builder()
                .familyId(familyId)
                .diaryCount(counts.diaries())
                .memoryCount(counts.memories())
                .growthRecordCount(counts.growthRecords())
                .scheduledCount(entries.size())
                .build();
    }

    public RebuildEmbeddingResponse rebuildFamilyIndexes(Long familyId, int limit) {
        return indexRebuildService.rebuildFamilyIndexes(familyId, limit);
    }

    public void indexMemoryAfterCommit(MemoryEntry entry) {
        if (entry == null
                || entry.getId() == null
                || isBlank(entry.getContent())
                || !EntityStatus.ACTIVE.name().equals(entry.getStatus())) {
            return;
        }
        scheduleAfterCommit(() -> indexCurrentMemory(entry.getId()));
    }

    public void deleteMemoryIndexAfterCommit(Long memoryId) {
        if (memoryId == null) {
            return;
        }
        scheduleAfterCommit(() -> embeddingWriteRepository.deleteBySource(
                MemoryEmbeddingSourceType.MEMORY.name(),
                memoryId));
    }

    private void scheduleAfterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    asyncProcessor.execute(task);
                }
            });
            return;
        }
        asyncProcessor.execute(task);
    }

    private void indexCurrentMemory(Long memoryId) {
        MemoryEntry current = memoryRepository.selectById(memoryId);
        if (current == null || !EntityStatus.ACTIVE.name().equals(current.getStatus())) {
            embeddingWriteRepository.deleteBySource(MemoryEmbeddingSourceType.MEMORY.name(), memoryId);
            return;
        }
        index(
                current.getId(),
                current.getFamilyId(),
                current.getUserId(),
                buildMemoryText(current));
    }

    private void index(Long sourceId, Long familyId, Long userId, String text) {
        if (userId == null || isBlank(text)) {
            return;
        }
        String sourceType = MemoryEmbeddingSourceType.MEMORY.name();
        String contentHash = sha256(text);
        Long embeddingId = embeddingWriteRepository.upsertPending(
                sourceType,
                sourceId,
                familyId,
                userId,
                contentHash);

        try {
            EmbeddingResponse response = aiServiceClient.embedText(EmbeddingRequest.builder()
                    .text(text)
                    .dimensions(EMBEDDING_DIMENSIONS)
                    .sourceType(sourceType)
                    .familyId(familyId)
                    .userId(userId)
                    .build());
            EmbeddingVectorValidator.Result validation = EmbeddingVectorValidator.validate(
                    response,
                    EMBEDDING_DIMENSIONS);
            if (!validation.valid()) {
                embeddingWriteRepository.markFailed(embeddingId, validation.error());
                return;
            }

            List<Double> values = validation.values();
            embeddingWriteRepository.markReady(
                    embeddingId,
                    response.getModel(),
                    values,
                    response.getPrivacyCategories(),
                    response.getProvider(),
                    response.getDimensions() == null ? values.size() : response.getDimensions());
        } catch (Exception error) {
            log.warn(
                    "Memory embedding indexing failed: sourceType={}, sourceId={}, errorType={}",
                    sourceType,
                    sourceId,
                    error.getClass().getSimpleName());
            embeddingWriteRepository.markFailed(embeddingId, "embedding indexing failed");
        }
    }

    private static String buildMemoryText(MemoryEntry entry) {
        return String.join("\n",
                "library: " + (MemoryLibraryKind.PERSONAL.name().equals(entry.getLibraryKind())
                        ? "personal memory"
                        : "family memory"),
                "origin: " + safe(entry.getOriginType()),
                "type: " + safe(entry.getType()),
                "title: " + safe(entry.getTitle()),
                "summary: " + safe(entry.getSummary()),
                "content: " + safe(entry.getContent()),
                "metadata: " + safe(entry.getMetadata()));
    }

    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
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

    private static String safe(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

}
