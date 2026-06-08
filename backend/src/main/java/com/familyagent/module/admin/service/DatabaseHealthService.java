package com.familyagent.module.admin.service;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.admin.dto.DatabaseHealthResponse;
import com.familyagent.module.admin.dto.DatabaseTableCount;
import com.familyagent.module.admin.dto.EmbeddingStatusSummary;
import com.familyagent.module.admin.dto.FailedEmbeddingSummary;
import com.familyagent.module.admin.dto.FamilyDatabaseSummary;
import com.familyagent.module.admin.dto.MemoryRecallDiagnosticRequest;
import com.familyagent.module.admin.dto.MemoryRecallDiagnosticResponse;
import com.familyagent.module.family.repository.FamilyMemberRepository;
import com.familyagent.module.memory.dto.AuthorizedMemoryRecallResult;
import com.familyagent.module.memory.service.AuthorizedMemoryRecallService;
import com.familyagent.module.user.entity.User;
import com.familyagent.module.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DatabaseHealthService {

    private static final List<TableDefinition> TABLES = List.of(
            new TableDefinition("users", "Users", false),
            new TableDefinition("families", "Families", false),
            new TableDefinition("family_members", "Family members", false),
            new TableDefinition("family_relationships", "Relationship labels", false),
            new TableDefinition("care_authorizations", "Care authorizations", false),
            new TableDefinition("diary_entries", "Life records", false),
            new TableDefinition("memory_entries", "Family experience", false),
            new TableDefinition("growth_guard_records", "Growth observations", false),
            new TableDefinition("growth_guard_reports", "Growth reports", false),
            new TableDefinition("memory_embeddings", "RAG embeddings", false),
            new TableDefinition("mirror_agent_data", "Mirror profiles", false),
            new TableDefinition("heritage_tasks", "Heritage tasks", false),
            new TableDefinition("chat_sessions", "Chat sessions", false),
            new TableDefinition("questions", "Legacy questions", true),
            new TableDefinition("knowledge_points", "Legacy knowledge points", true),
            new TableDefinition("test_records", "Legacy tests", true),
            new TableDefinition("wrong_question_records", "Legacy wrong questions", true),
            new TableDefinition("ability_profiles", "Legacy ability profiles", true)
    );

    private final JdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final AuthorizedMemoryRecallService memoryRecallService;

    public DatabaseHealthResponse getHealth() {
        requirePlatformAdmin();

        List<DatabaseTableCount> tableCounts = TABLES.stream()
                .map(table -> DatabaseTableCount.builder()
                        .tableName(table.tableName())
                        .label(table.label())
                        .legacy(table.legacy())
                        .count(countTable(table.tableName()))
                        .build())
                .toList();

        long totalCoreRecords = countTable("diary_entries")
                + countTable("memory_entries")
                + countTable("growth_guard_records");
        long totalEmbeddings = countTable("memory_embeddings");
        long readyEmbeddings = countEmbeddingsByStatus("READY");
        long failedEmbeddings = countEmbeddingsByStatus("FAILED");

        return DatabaseHealthResponse.builder()
                .generatedAt(LocalDateTime.now())
                .databaseName(queryDatabaseName())
                .pgvectorInstalled(isPgvectorInstalled())
                .totalUsers(countTable("users"))
                .totalFamilies(countTable("families"))
                .totalCoreRecords(totalCoreRecords)
                .totalEmbeddings(totalEmbeddings)
                .readyEmbeddings(readyEmbeddings)
                .failedEmbeddings(failedEmbeddings)
                .tableCounts(tableCounts)
                .embeddingStatuses(queryEmbeddingStatuses())
                .families(queryFamilySummaries())
                .recentFailedEmbeddings(queryRecentFailedEmbeddings())
                .build();
    }

    public MemoryRecallDiagnosticResponse diagnoseMemoryRecall(MemoryRecallDiagnosticRequest request) {
        requirePlatformAdmin();
        if (request == null || request.getFamilyId() == null || request.getViewerUserId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "familyId and viewerUserId are required");
        }
        if (familyMemberRepository.findByFamilyAndUser(request.getFamilyId(), request.getViewerUserId()) == null) {
            throw new BusinessException(ErrorCode.NOT_FAMILY_MEMBER, "The simulated viewer is not a member of this family");
        }

        int diaryLimit = clampLimit(request.getDiaryLimit(), 3, 10);
        int memoryLimit = clampLimit(request.getMemoryLimit(), 3, 10);
        AuthorizedMemoryRecallResult recall = memoryRecallService.recallForFamilyAfterViewerValidated(
                request.getFamilyId(),
                request.getViewerUserId(),
                request.getQuery(),
                diaryLimit,
                memoryLimit);

        return MemoryRecallDiagnosticResponse.builder()
                .familyId(request.getFamilyId())
                .viewerUserId(request.getViewerUserId())
                .query(recall.getQuery())
                .retrievalMode(recall.getRetrievalMode())
                .embeddingReadyCount(recall.getEmbeddingReadyCount())
                .diaryCount(recall.getDiaryCount())
                .memoryCount(recall.getMemoryCount())
                .growthRecordCount(recall.getGrowthRecordCount())
                .sources(recall.getSources() == null ? List.of() : recall.getSources())
                .build();
    }

    private void requirePlatformAdmin() {
        Long userId = CurrentUserGuard.currentUserId();
        User user = userRepository.findBasicById(userId);
        if (user == null || !"ADMIN".equalsIgnoreCase(user.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Platform admin permission is required");
        }
    }

    private static int clampLimit(Integer value, int fallback, int max) {
        if (value == null || value <= 0) {
            return fallback;
        }
        return Math.min(value, max);
    }

    private String queryDatabaseName() {
        return jdbcTemplate.queryForObject("SELECT current_database()", String.class);
    }

    private boolean isPgvectorInstalled() {
        Boolean installed = jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM pg_extension WHERE extname = 'vector'
                )
                """, Boolean.class);
        return Boolean.TRUE.equals(installed);
    }

    private long countTable(String tableName) {
        if (!tableExists(tableName)) {
            return 0;
        }
        Number count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Number.class);
        return count == null ? 0 : count.longValue();
    }

    private long countEmbeddingsByStatus(String status) {
        if (!tableExists("memory_embeddings")) {
            return 0;
        }
        Number count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM memory_embeddings WHERE status = ?
                """, Number.class, status);
        return count == null ? 0 : count.longValue();
    }

    private boolean tableExists(String tableName) {
        Boolean exists = jdbcTemplate.queryForObject("SELECT to_regclass(?) IS NOT NULL", Boolean.class, "public." + tableName);
        return Boolean.TRUE.equals(exists);
    }

    private List<EmbeddingStatusSummary> queryEmbeddingStatuses() {
        if (!tableExists("memory_embeddings")) {
            return List.of();
        }
        return jdbcTemplate.query("""
                SELECT family_id, source_type, status, COUNT(*) AS count, MAX(updated_at) AS last_updated_at
                FROM memory_embeddings
                GROUP BY family_id, source_type, status
                ORDER BY family_id ASC, source_type ASC, status ASC
                """, (rs, rowNum) -> EmbeddingStatusSummary.builder()
                .familyId(rs.getLong("family_id"))
                .sourceType(rs.getString("source_type"))
                .status(rs.getString("status"))
                .count(rs.getLong("count"))
                .lastUpdatedAt(rs.getTimestamp("last_updated_at") == null
                        ? null
                        : rs.getTimestamp("last_updated_at").toLocalDateTime())
                .build());
    }

    private List<FamilyDatabaseSummary> queryFamilySummaries() {
        if (!tableExists("families")) {
            return List.of();
        }
        return jdbcTemplate.query("""
                SELECT
                    f.id AS family_id,
                    f.name AS family_name,
                    (SELECT COUNT(*) FROM family_members fm WHERE fm.family_id = f.id) AS member_count,
                    (SELECT COUNT(*) FROM diary_entries de WHERE de.family_id = f.id) AS diary_count,
                    (SELECT COUNT(*) FROM memory_entries me WHERE me.family_id = f.id) AS memory_count,
                    (SELECT COUNT(*) FROM growth_guard_records gr WHERE gr.family_id = f.id) AS growth_record_count,
                    (SELECT COUNT(*) FROM memory_embeddings em WHERE em.family_id = f.id AND em.status = 'READY') AS ready_embedding_count,
                    (SELECT COUNT(*) FROM memory_embeddings em WHERE em.family_id = f.id AND em.status = 'FAILED') AS failed_embedding_count
                FROM families f
                ORDER BY f.updated_at DESC NULLS LAST, f.id DESC
                LIMIT 50
                """, (rs, rowNum) -> FamilyDatabaseSummary.builder()
                .familyId(rs.getLong("family_id"))
                .familyName(rs.getString("family_name"))
                .memberCount(rs.getLong("member_count"))
                .diaryCount(rs.getLong("diary_count"))
                .memoryCount(rs.getLong("memory_count"))
                .growthRecordCount(rs.getLong("growth_record_count"))
                .readyEmbeddingCount(rs.getLong("ready_embedding_count"))
                .failedEmbeddingCount(rs.getLong("failed_embedding_count"))
                .build());
    }

    private List<FailedEmbeddingSummary> queryRecentFailedEmbeddings() {
        if (!tableExists("memory_embeddings")) {
            return List.of();
        }
        return jdbcTemplate.query("""
                SELECT id, family_id, source_type, source_id, metadata->>'error' AS error, updated_at
                FROM memory_embeddings
                WHERE status = 'FAILED'
                ORDER BY updated_at DESC
                LIMIT 10
                """, (rs, rowNum) -> FailedEmbeddingSummary.builder()
                .id(rs.getLong("id"))
                .familyId(rs.getLong("family_id"))
                .sourceType(rs.getString("source_type"))
                .sourceId(rs.getLong("source_id"))
                .error(rs.getString("error"))
                .updatedAt(rs.getTimestamp("updated_at") == null
                        ? null
                        : rs.getTimestamp("updated_at").toLocalDateTime())
                .build());
    }

    private record TableDefinition(String tableName, String label, boolean legacy) {
    }
}
