package com.familyagent.module.memory.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.familyagent.common.constant.EntityStatus;
import com.familyagent.common.constant.MemoryScope;
import com.familyagent.common.constant.MemoryType;
import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.memory.dto.EmbeddingCallObservation;
import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.eval.dto.MemoryRecallQualityEvalCase;
import com.familyagent.module.memory.eval.dto.MemoryRecallQualityEvalReport;
import com.familyagent.module.memory.service.AuthorizedMemoryRecallEmbeddingService;
import com.familyagent.module.memory.service.AuthorizedMemoryRecallRankingService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final AuthorizedMemoryRecallRankingService rankingService =
            new AuthorizedMemoryRecallRankingService(embeddingService, jdbcTemplate);
    private final MemoryRecallQualityEvalService evalService = new MemoryRecallQualityEvalService();

    @Test
    void syntheticRecallSuiteCoversKeywordSemanticFallbackDegradedAndPrivacy() throws Exception {
        List<MemoryRecallQualityEvalCase> cases = List.of(
                exactKeywordCase(),
                semanticTopicCase(),
                degradedEmbeddingCase(),
                authorizedCandidateCase());

        MemoryRecallQualityEvalReport report = evalService.evaluate(
                SUITE_VERSION,
                AuthorizedMemoryRecallRankingService.ALGORITHM_VERSION,
                cases);

        assertEquals(4, report.metrics().caseCount());
        assertEquals(4, report.metrics().passedCount());
        assertEquals(1.0d, report.metrics().expectedTopKHitRate());
        assertEquals(1.0d, report.metrics().firstResultHitRate());
        assertEquals(0, report.metrics().unauthorizedResultCount());
        assertTrue(report.metrics().privacyGatePassed());
        verifyNoInteractions(jdbcTemplate);

        String json = new ObjectMapper().writeValueAsString(report);
        assertFalse(json.contains("bedtime routine"));
        assertFalse(json.contains("孩子最近睡眠怎么样"));
        assertFalse(json.contains("private excluded memory"));
    }

    private MemoryRecallQualityEvalCase exactKeywordCase() {
        List<MemoryEntry> candidates = List.of(
                memory(1L, "Family picnic notes", "outdoor activity", null),
                memory(2L, "Grandma's bedtime routine", "bedtime routine", null));
        List<String> actual = sourceIds(rankingService.rank(
                FAMILY_ID, "bedtime routine", List.of(), candidates, List.of(), 3, 3, 0));
        return evalCase("exact-keyword", candidates.size(), List.of("memory-2"), List.of(), actual);
    }

    private MemoryRecallQualityEvalCase semanticTopicCase() {
        Map<String, Object> healthIndex = index(List.of("HEALTH"), List.of("成长健康"));
        List<MemoryEntry> candidates = List.of(
                memory(3L, "每天九点关灯，早晨精神更好", "作息经验", healthIndex),
                memory(4L, "周末一起整理相册", "家庭活动", null));
        List<String> actual = sourceIds(rankingService.rank(
                FAMILY_ID, "孩子最近睡眠怎么样", List.of(), candidates, List.of(), 3, 3, 0));
        return evalCase("semantic-topic", candidates.size(), List.of("memory-3"), List.of(), actual);
    }

    private MemoryRecallQualityEvalCase degradedEmbeddingCase() {
        EmbeddingCallObservation observation = new EmbeddingCallObservation(
                true, false, true, "remote", "embedding-model", 0, 12L, "EMBEDDING_PROVIDER_ERROR");
        when(embeddingService.embed(FAMILY_ID, "刷牙习惯"))
                .thenReturn(new AuthorizedMemoryRecallEmbeddingService.RecallQueryEmbedding(List.of(), observation));
        DiaryEntry target = diary(7L, "最近刷牙越来越主动");
        DiaryEntry other = diary(8L, "今天一起搭积木");
        List<String> actual = sourceIds(rankingService.rank(
                FAMILY_ID, "刷牙习惯", List.of(other, target), List.of(), List.of(), 3, 3, 1));
        return evalCase("degraded-text-fallback", 2, List.of("diary-7"), List.of(), actual);
    }

    private MemoryRecallQualityEvalCase authorizedCandidateCase() {
        MemoryEntry allowed = memory(10L, "爷爷以前讲过的家风故事", "家风故事", null);
        List<String> actual = sourceIds(rankingService.rank(
                FAMILY_ID, "爷爷家风", List.of(), List.of(allowed), List.of(), 3, 3, 0));
        return evalCase(
                "permission-scope-exclusion",
                2,
                List.of("memory-10"),
                List.of("memory-99"),
                actual);
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
        List<String> ids = new ArrayList<>();
        ranked.diaries().forEach(entry -> ids.add("diary-" + entry.getId()));
        ranked.memories().forEach(entry -> ids.add("memory-" + entry.getId()));
        ranked.growthRecords().forEach(entry -> ids.add("growth-" + entry.getId()));
        return ids;
    }

    private static MemoryEntry memory(Long id, String content, String summary, Map<String, Object> index) {
        MemoryEntry entry = new MemoryEntry();
        entry.setId(id);
        entry.setFamilyId(FAMILY_ID);
        entry.setUserId(101L);
        entry.setType(MemoryType.ELDER_ADVICE.name());
        entry.setScope(MemoryScope.FAMILY_VISIBLE.name());
        entry.setStatus(EntityStatus.ACTIVE.name());
        entry.setContent(content);
        entry.setSummary(summary);
        entry.setImportance(3);
        entry.setCreatedAt(LocalDateTime.of(2026, 7, 1, 10, 0));
        entry.setUpdatedAt(LocalDateTime.of(2026, 7, 1, 10, 0));
        if (index != null) {
            entry.setMetadata(Map.of("index", index));
        }
        return entry;
    }

    private static DiaryEntry diary(Long id, String text) {
        DiaryEntry entry = new DiaryEntry();
        entry.setId(id);
        entry.setFamilyId(FAMILY_ID);
        entry.setUserId(101L);
        entry.setRawText(text);
        entry.setVisibility(MemoryScope.FAMILY_VISIBLE.name());
        entry.setCreatedAt(LocalDateTime.of(2026, 7, 2, 10, 0));
        return entry;
    }

    private static Map<String, Object> index(List<String> topics, List<String> scenes) {
        Map<String, Object> index = new LinkedHashMap<>();
        index.put("topics", topics);
        index.put("scenes", scenes);
        index.put("temporalLayer", "STABLE");
        index.put("halfLifeDays", 730);
        index.put("minTemporalWeight", 0.56d);
        index.put("indexedAt", "2026-07-01T10:00:00");
        return index;
    }
}
