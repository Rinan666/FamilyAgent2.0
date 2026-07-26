package com.familyagent.module.memory.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.familyagent.common.constant.EntityStatus;
import com.familyagent.common.constant.MemoryOriginType;
import com.familyagent.common.constant.MemoryScope;
import com.familyagent.module.memory.dto.AuthorizedMemoryRecallCandidate;
import com.familyagent.module.memory.dto.EmbeddingCallObservation;
import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.eval.dto.MemoryRecallQualityEvalCase;
import com.familyagent.module.memory.eval.dto.MemoryRecallQualityEvalReport;
import com.familyagent.module.memory.repository.MemoryRecallVectorRepository;
import com.familyagent.module.memory.service.AuthorizedMemoryRecallEmbeddingService;
import com.familyagent.module.memory.service.AuthorizedMemoryRecallRankingService;
import com.familyagent.module.memory.service.AuthorizedMemoryRecallScorer;
import com.familyagent.module.memory.service.AuthorizedMemoryRecallTextRanker;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AuthorizedMemoryRecallQualityEvalTest {

    private static final Long FAMILY_ID = 10L;
    private static final String SUITE_VERSION = "memory.recall.synthetic.v1";

    private final AuthorizedMemoryRecallEmbeddingService embeddingService =
            mock(AuthorizedMemoryRecallEmbeddingService.class);
    private final MemoryRecallVectorRepository vectorRepository = mock(MemoryRecallVectorRepository.class);
    private final AuthorizedMemoryRecallRankingService rankingService = new AuthorizedMemoryRecallRankingService(
            embeddingService,
            vectorRepository,
            new AuthorizedMemoryRecallTextRanker(new AuthorizedMemoryRecallScorer()));
    private final MemoryRecallQualityEvalService evalService = new MemoryRecallQualityEvalService();

    @Test
    void syntheticRecallSuiteCoversKeywordSemanticFallbackDegradedAndPrivacy() throws Exception {
        MemoryRecallQualityEvalReport report = evalService.evaluate(
                SUITE_VERSION,
                AuthorizedMemoryRecallRankingService.ALGORITHM_VERSION,
                List.of(exactKeywordCase(), semanticTopicCase(), degradedEmbeddingCase(), authorizedCandidateCase()));

        assertEquals(4, report.metrics().caseCount());
        assertEquals(4, report.metrics().passedCount());
        assertEquals(1.0d, report.metrics().expectedTopKHitRate());
        assertEquals(1.0d, report.metrics().firstResultHitRate());
        assertEquals(0, report.metrics().unauthorizedResultCount());
        assertTrue(report.metrics().privacyGatePassed());
        verifyNoInteractions(vectorRepository);

        String json = new ObjectMapper().writeValueAsString(report);
        assertFalse(json.contains("bedtime routine"));
        assertFalse(json.contains("private excluded memory"));
    }

    private MemoryRecallQualityEvalCase exactKeywordCase() {
        List<AuthorizedMemoryRecallCandidate> candidates = List.of(
                memory(1L, "Family picnic notes", "outdoor activity", null),
                memory(2L, "Grandma's bedtime routine", "bedtime routine", null));
        List<String> actual = sourceIds(rankingService.rank(
                FAMILY_ID, "bedtime routine", List.of(), candidates, List.of(), 3, 3, 0));
        return evalCase("exact-keyword", candidates.size(), List.of("memory-2"), List.of(), actual);
    }

    private MemoryRecallQualityEvalCase semanticTopicCase() {
        Map<String, Object> healthIndex = index(List.of("HEALTH"), List.of("growth health"));
        List<AuthorizedMemoryRecallCandidate> candidates = List.of(
                memory(3L, "Sleep before nine improves mornings", "routine", healthIndex),
                memory(4L, "Weekend photo sorting", "family activity", null));
        List<String> actual = sourceIds(rankingService.rank(
                FAMILY_ID, "health", List.of(), candidates, List.of(), 3, 3, 0));
        return evalCase("semantic-topic", candidates.size(), List.of("memory-3"), List.of(), actual);
    }

    private MemoryRecallQualityEvalCase degradedEmbeddingCase() {
        EmbeddingCallObservation observation = new EmbeddingCallObservation(
                true, false, true, "remote", "embedding-model", 0, 12L, "EMBEDDING_PROVIDER_ERROR");
        when(embeddingService.embed(FAMILY_ID, "brushing habit"))
                .thenReturn(new AuthorizedMemoryRecallEmbeddingService.RecallQueryEmbedding(List.of(), observation));
        List<AuthorizedMemoryRecallCandidate> candidates = List.of(
                diary(8L, "Today we built blocks"),
                diary(7L, "Brushing habit has improved"));
        List<String> actual = sourceIds(rankingService.rank(
                FAMILY_ID, "brushing habit", candidates, List.of(), List.of(), 3, 3, 1));
        return evalCase("degraded-text-fallback", 2, List.of("diary-7"), List.of(), actual);
    }

    private MemoryRecallQualityEvalCase authorizedCandidateCase() {
        AuthorizedMemoryRecallCandidate allowed = memory(10L, "Grandpa's family story", "family story", null);
        List<String> actual = sourceIds(rankingService.rank(
                FAMILY_ID, "Grandpa family", List.of(), List.of(allowed), List.of(), 3, 3, 0));
        return evalCase("permission-scope-exclusion", 2, List.of("memory-10"), List.of("memory-99"), actual);
    }

    private static MemoryRecallQualityEvalCase evalCase(
            String caseId,
            int candidateCount,
            List<String> expected,
            List<String> unauthorized,
            List<String> actual) {
        return new MemoryRecallQualityEvalCase(caseId, candidateCount, expected, unauthorized, actual);
    }

    private static List<String> sourceIds(AuthorizedMemoryRecallRankingService.RankedRecall ranked) {
        return java.util.stream.Stream.of(ranked.diaries(), ranked.memories(), ranked.growthRecords())
                .flatMap(List::stream)
                .map(AuthorizedMemoryRecallCandidate::publicId)
                .toList();
    }

    private static AuthorizedMemoryRecallCandidate memory(
            Long id,
            String content,
            String summary,
            Map<String, Object> index) {
        MemoryEntry entry = baseEntry(id, content);
        entry.setSummary(summary);
        if (index != null) {
            entry.setMetadata(Map.of("index", index));
        }
        return AuthorizedMemoryRecallCandidate.from(entry);
    }

    private static AuthorizedMemoryRecallCandidate diary(Long originId, String text) {
        MemoryEntry entry = baseEntry(100L + originId, text);
        entry.setOriginType(MemoryOriginType.DIARY.name());
        entry.setOriginId(originId);
        return AuthorizedMemoryRecallCandidate.from(entry);
    }

    private static MemoryEntry baseEntry(Long id, String content) {
        MemoryEntry entry = new MemoryEntry();
        entry.setId(id);
        entry.setFamilyId(FAMILY_ID);
        entry.setLibraryKind("FAMILY");
        entry.setUserId(101L);
        entry.setScope(MemoryScope.FAMILY_VISIBLE.name());
        entry.setStatus(EntityStatus.ACTIVE.name());
        entry.setContent(content);
        entry.setImportance(3);
        entry.setOccurredAt(LocalDateTime.of(2026, 7, 1, 10, 0));
        entry.setCreatedAt(LocalDateTime.of(2026, 7, 1, 10, 0));
        entry.setUpdatedAt(LocalDateTime.of(2026, 7, 1, 10, 0));
        return entry;
    }

    private static Map<String, Object> index(List<String> topics, List<String> scenes) {
        Map<String, Object> index = new LinkedHashMap<>();
        index.put("topics", topics);
        index.put("scenes", scenes);
        index.put("temporalLayer", "STABLE");
        return index;
    }
}
