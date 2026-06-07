package com.familyagent.module.memory.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.familyagent.infra.ai.AIServiceClient;
import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.diary.repository.DiaryEntryRepository;
import com.familyagent.module.family.service.FamilyService;
import com.familyagent.module.memory.dto.RebuildEmbeddingResponse;
import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.repository.MemoryEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryEmbeddingService {

    private static final int EMBEDDING_DIMENSIONS = 1536;

    private final AIServiceClient aiServiceClient;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final DiaryEntryRepository diaryRepository;
    private final MemoryEntryRepository memoryRepository;
    private final FamilyService familyService;

    public RebuildEmbeddingResponse rebuildFamilyEmbeddings(Long familyId, int limit) {
        familyService.checkMembership(familyId);
        int normalizedLimit = normalizeLimit(limit);
        List<DiaryEntry> diaries = diaryRepository.findActiveByFamilyForIndexing(familyId, normalizedLimit);
        List<MemoryEntry> memories = memoryRepository.findActiveByFamilyForIndexing(familyId, normalizedLimit);
        diaries.forEach(this::indexDiaryAfterCommit);
        memories.forEach(this::indexMemoryAfterCommit);
        return RebuildEmbeddingResponse.builder()
                .familyId(familyId)
                .diaryCount(diaries.size())
                .memoryCount(memories.size())
                .scheduledCount(diaries.size() + memories.size())
                .build();
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

    private void scheduleAfterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    CompletableFuture.runAsync(task);
                }
            });
            return;
        }
        CompletableFuture.runAsync(task);
    }

    private void index(String sourceType, Long sourceId, Long familyId, Long userId, String text) {
        if (familyId == null || userId == null || isBlank(text)) {
            return;
        }
        String contentHash = sha256(text);
        Long embeddingId = upsertPending(sourceType, sourceId, familyId, userId, contentHash);

        try {
            Map<String, Object> response = aiServiceClient.embedText(Map.of(
                    "text", text,
                    "dimensions", EMBEDDING_DIMENSIONS));
            if (!Boolean.TRUE.equals(response.get("success"))) {
                markFailed(embeddingId, String.valueOf(response.getOrDefault("error", "embedding failed")));
                return;
            }

            Object rawEmbedding = response.get("embedding");
            if (!(rawEmbedding instanceof List<?> values) || values.isEmpty()) {
                markFailed(embeddingId, "embedding response is empty");
                return;
            }

            String vector = toVectorLiteral(values);
            String model = String.valueOf(response.getOrDefault("model", ""));
            String metadata = objectMapper.writeValueAsString(Map.of(
                    "privacyCategories", response.getOrDefault("privacy_categories", List.of()),
                    "dimensions", values.size()));
            jdbcTemplate.update("""
                    UPDATE memory_embeddings
                    SET embedding_model = ?,
                        embedding = ?::vector,
                        status = 'READY',
                        metadata = ?::jsonb,
                        updated_at = NOW()
                    WHERE id = ?
                    """, model, vector, metadata, embeddingId);
        } catch (Exception e) {
            log.warn("Memory embedding indexing failed: sourceType={}, sourceId={}, error={}",
                    sourceType, sourceId, e.getMessage());
            markFailed(embeddingId, e.getMessage());
        }
    }

    private Long upsertPending(String sourceType, Long sourceId, Long familyId, Long userId, String contentHash) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO memory_embeddings (
                    family_id, user_id, source_type, source_id, content_hash, status, metadata
                )
                VALUES (?, ?, ?, ?, ?, 'PENDING', '{}'::jsonb)
                ON CONFLICT (source_type, source_id, content_hash)
                DO UPDATE SET status = 'PENDING', updated_at = NOW()
                RETURNING id
                """, Long.class, familyId, userId, sourceType, sourceId, contentHash);
    }

    private void markFailed(Long id, String error) {
        if (id == null) {
            return;
        }
        jdbcTemplate.update("""
                UPDATE memory_embeddings
                SET status = 'FAILED',
                    metadata = jsonb_build_object('error', ?),
                    updated_at = NOW()
                WHERE id = ?
                """, error == null ? "unknown" : error, id);
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

    private static String toVectorLiteral(List<?> values) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(Double.parseDouble(String.valueOf(values.get(i))));
        }
        return builder.append(']').toString();
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
