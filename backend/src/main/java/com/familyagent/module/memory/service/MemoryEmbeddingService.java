package com.familyagent.module.memory.service;

import com.familyagent.infra.ai.AIServiceClient;
import com.familyagent.infra.ai.dto.EmbeddingRequest;
import com.familyagent.infra.ai.dto.EmbeddingResponse;
import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.diary.repository.DiaryEntryRepository;
import com.familyagent.module.family.service.FamilyService;
import com.familyagent.module.growth.entity.GrowthGuardRecord;
import com.familyagent.module.growth.repository.GrowthGuardRecordRepository;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryEmbeddingService {

    private static final int EMBEDDING_DIMENSIONS = 1536;

    private final AIServiceClient aiServiceClient;
    private final MemoryEmbeddingWriteRepository embeddingWriteRepository;
    private final DiaryEntryRepository diaryRepository;
    private final MemoryEntryRepository memoryRepository;
    private final GrowthGuardRecordRepository growthRecordRepository;
    private final FamilyService familyService;
    private final EmbeddingAsyncProcessor asyncProcessor;

    public RebuildEmbeddingResponse rebuildFamilyEmbeddings(Long familyId, int limit) {
        familyService.checkMembership(familyId);
        int normalizedLimit = normalizeLimit(limit);
        List<DiaryEntry> diaries = diaryRepository.findActiveByFamilyForIndexing(familyId, normalizedLimit);
        List<MemoryEntry> memories = memoryRepository.findActiveByFamilyForIndexing(familyId, normalizedLimit);
        List<GrowthGuardRecord> growthRecords = growthRecordRepository.findActiveByFamilyForIndexing(familyId, normalizedLimit);
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
        familyService.checkMembership(familyId);
        int normalizedLimit = normalizeLimit(limit);
        List<DiaryEntry> diaries = diaryRepository.findActiveByFamilyForIndexing(familyId, normalizedLimit);
        List<MemoryEntry> memories = memoryRepository.findActiveByFamilyForIndexing(familyId, normalizedLimit);
        List<GrowthGuardRecord> growthRecords = growthRecordRepository.findActiveByFamilyForIndexing(familyId, normalizedLimit);

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
        diaryRepository.updateById(entry);
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
        growthRecordRepository.updateById(record);
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
        if (familyId == null || userId == null || isBlank(text)) {
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
                "类型：家族经验",
                "主题：" + safe(entry.getType()),
                "摘要：" + safe(entry.getSummary()),
                "内容：" + safe(entry.getContent()),
                "元数据：" + safe(entry.getMetadata()));
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

    private static String safe(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
