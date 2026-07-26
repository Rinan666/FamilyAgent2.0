package com.familyagent.module.memory.service;

import com.familyagent.infra.ai.AIServiceClient;
import com.familyagent.infra.ai.dto.EmbeddingRequest;
import com.familyagent.infra.ai.dto.EmbeddingResponse;
import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.diary.facade.MemoryIndexDiaryFacade;
import com.familyagent.module.family.facade.FamilyMembershipFacade;
import com.familyagent.module.growth.entity.GrowthGuardRecord;
import com.familyagent.module.growth.facade.MemoryIndexGrowthFacade;
import com.familyagent.module.memory.dto.RebuildEmbeddingResponse;
import com.familyagent.common.constant.MemoryLibraryKind;
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
    private final MemoryIndexDiaryFacade diaryIndexFacade;
    private final MemoryEntryRepository memoryRepository;
    private final MemoryIndexGrowthFacade growthIndexFacade;
    private final FamilyMembershipFacade familyMembershipFacade;
    private final EmbeddingAsyncProcessor asyncProcessor;
    private final MemoryIndexRebuildService indexRebuildService;

    public RebuildEmbeddingResponse rebuildFamilyEmbeddings(Long familyId, int limit) {
        familyMembershipFacade.checkMembership(familyId);
        int normalizedLimit = normalizeLimit(limit);
        List<DiaryEntry> diaries = diaryIndexFacade.findActiveByFamily(familyId, normalizedLimit);
        List<MemoryEntry> memories = memoryRepository.findActiveByFamilyForIndexing(familyId, normalizedLimit);
        List<GrowthGuardRecord> growthRecords = growthIndexFacade.findActiveByFamily(familyId, normalizedLimit);
        diaries.forEach(this::indexDiaryAfterCommit);
        memories.forEach(this::indexMemoryAfterCommit);
        growthRecords.forEach(this::indexGrowthAfterCommit);
        return RebuildEmbeddingResponse.builder()
                .familyId(familyId)
                .diaryCount(diaries.size())
                .memoryCount(memories.size())
                .growthRecordCount(growthRecords.size())
                .scheduledCount(diaries.size() + memories.size() + growthRecords.size())
                .build();
    }

    public RebuildEmbeddingResponse rebuildFamilyIndexes(Long familyId, int limit) {
        return indexRebuildService.rebuildFamilyIndexes(familyId, limit);
    }

    public void indexDiaryAfterCommit(DiaryEntry entry) {
        if (entry == null || entry.getId() == null || isBlank(entry.getRawText())) {
            return;
        }
        scheduleAfterCommit(() -> index(
                "DIARY",
                entry.getId(),
                entry.getFamilyId(),
                entry.getUserId(),
                buildDiaryText(entry)));
    }

    public void indexMemoryAfterCommit(MemoryEntry entry) {
        if (entry == null || entry.getId() == null || isBlank(entry.getContent())) {
            return;
        }
        scheduleAfterCommit(() -> index(
                "MEMORY",
                entry.getId(),
                entry.getFamilyId(),
                entry.getUserId(),
                buildMemoryText(entry)));
    }

    public void indexGrowthAfterCommit(GrowthGuardRecord record) {
        if (record == null || record.getId() == null || isBlank(record.getContent())) {
            return;
        }
        scheduleAfterCommit(() -> index(
                "GROWTH_OBSERVATION",
                record.getId(),
                record.getFamilyId(),
                record.getCreatedBy(),
                buildGrowthText(record)));
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

    private void index(String sourceType, Long sourceId, Long familyId, Long userId, String text) {
        if (userId == null || isBlank(text)) {
            return;
        }
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
        } catch (Exception e) {
            log.warn(
                    "Memory embedding indexing failed: sourceType={}, sourceId={}, errorType={}",
                    sourceType,
                    sourceId,
                    e.getClass().getSimpleName());
            embeddingWriteRepository.markFailed(embeddingId, "embedding indexing failed");
        }
    }

    private static String buildDiaryText(DiaryEntry entry) {
        return String.join("\n",
                "类型：家族日记",
                "心情：" + safe(entry.getMood()),
                "标签：" + String.join(" ", entry.getTags() == null ? new String[0] : entry.getTags()),
                "内容：" + safe(entry.getRawText()),
                "结构：" + safe(entry.getStructured()));
    }

    private static String buildMemoryText(MemoryEntry entry) {
        return String.join("\n",
                "library: " + (MemoryLibraryKind.PERSONAL.name().equals(entry.getLibraryKind())
                        ? "personal memory"
                        : "family memory"),
                "type: " + safe(entry.getType()),
                "summary: " + safe(entry.getSummary()),
                "content: " + safe(entry.getContent()),
                "metadata: " + safe(entry.getMetadata()));
    }

    private static String buildGrowthText(GrowthGuardRecord record) {
        return String.join("\n",
                "type: growth observation",
                "category: " + safe(record.getCategory()),
                "severity: " + safe(record.getSeverity()),
                "observedAt: " + safe(record.getObservedAt()),
                "content: " + safe(record.getContent()),
                "metadata: " + safe(record.getMetadata()));
    }

    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static int normalizeLimit(int limit) {
        if (limit <= 0) {
            return 200;
        }
        return Math.min(limit, 1000);
    }

    private static String safe(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
