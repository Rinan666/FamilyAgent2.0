package com.familyagent.module.admin.service;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.common.response.PageResult;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.admin.dto.AdminUserSummary;
import com.familyagent.module.admin.dto.DatabaseHealthResponse;
import com.familyagent.module.admin.dto.DatabaseTableCount;
import com.familyagent.module.admin.dto.EmbeddingStatusSummary;
import com.familyagent.module.admin.dto.FailedEmbeddingSummary;
import com.familyagent.module.admin.dto.FailedSkillRunSummary;
import com.familyagent.module.admin.dto.FamilyDatabaseSummary;
import com.familyagent.module.admin.dto.MemoryRecallDiagnosticRequest;
import com.familyagent.module.admin.dto.MemoryRecallDiagnosticResponse;
import com.familyagent.module.admin.dto.SessionArchiveRangeSummary;
import com.familyagent.module.admin.dto.SessionStorageHealthSummary;
import com.familyagent.module.admin.dto.SuspiciousFamilySummary;
import com.familyagent.module.family.dto.FamilyMemberVO;
import com.familyagent.module.family.repository.FamilyRepository;
import com.familyagent.module.family.repository.FamilyMemberRepository;
import com.familyagent.module.family.service.FamilyLifecycleService;
import com.familyagent.module.memory.dto.AuthorizedMemoryRecallResult;
import com.familyagent.module.memory.service.AuthorizedMemoryRecallService;
import com.familyagent.module.user.entity.User;
import com.familyagent.module.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class DatabaseHealthService {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 50;

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
            new TableDefinition("chat_session_messages", "Chat session messages", false),
            new TableDefinition("chat_session_archives", "Chat session archives", false),
            new TableDefinition("skill_runs", "Skill run audit", false)
    );

    private final JdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;
    private final FamilyRepository familyRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final FamilyLifecycleService familyLifecycleService;
    private final AuthorizedMemoryRecallService memoryRecallService;

    @Transactional
    public void deleteUser(Long userId) {
        requirePlatformAdmin();
        if (userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "userId is required");
        }

        Long operatorUserId = CurrentUserGuard.currentUserId();
        if (userId.equals(operatorUserId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Platform admin cannot delete the current account");
        }

        User target = userRepository.findBasicById(userId);
        if (target == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if ("ADMIN".equalsIgnoreCase(target.getRole())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Platform admin accounts cannot be deleted");
        }

        familyLifecycleService.prepareFamiliesForUserDeletion(userId);

        updateNullIfTableExists("families", "created_by", userId);
        updateNullIfTableExists("invite_codes", "created_by", userId);
        updateNullIfTableExists("family_relationships", "created_by", userId);
        updateNullIfTableExists("family_relationships", "updated_by", userId);
        updateNullIfTableExists("care_authorizations", "created_by", userId);
        updateNullIfTableExists("care_authorizations", "updated_by", userId);
        updateNullIfTableExists("growth_guard_records", "target_user_id", userId);
        updateNullIfTableExists("growth_guard_reports", "target_user_id", userId);
        updateNullIfTableExists("heritage_tasks", "completed_by", userId);

        deleteIfTableExists("family_relationships", "from_user_id", userId);
        deleteIfTableExists("family_relationships", "to_user_id", userId);
        deleteIfTableExists("care_authorizations", "subject_user_id", userId);
        deleteIfTableExists("care_authorizations", "caregiver_user_id", userId);
        deleteIfTableExists("growth_guard_staleness_votes", "user_id", userId);
        deleteIfTableExists("memory_entry_votes", "user_id", userId);
        deleteIfTableExists("heritage_tasks", "created_by", userId);
        deleteIfTableExists("growth_guard_reports", "created_by", userId);
        deleteIfTableExists("growth_guard_records", "created_by", userId);
        deleteIfTableExists("memory_embeddings", "user_id", userId);
        deleteIfTableExists("skill_runs", "triggered_by", userId);
        deleteIfTableExists("chat_sessions", "user_id", userId);
        deleteIfTableExists("diary_entries", "user_id", userId);
        deleteIfTableExists("memory_entries", "user_id", userId);
        deleteIfTableExists("mirror_agent_data", "user_id", userId);
        deleteIfTableExists("family_members", "user_id", userId);

        int deleted = jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
        if (deleted == 0) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
    }

    public List<FamilyMemberVO> listFamilyMembers(Long familyId) {
        requirePlatformAdmin();
        if (familyId == null || familyId <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "familyId is required");
        }
        if (familyRepository.selectById(familyId) == null) {
            throw new BusinessException(ErrorCode.FAMILY_NOT_FOUND);
        }
        return familyMemberRepository.findMemberViewsByFamilyId(familyId);
    }

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
        long totalSkillRuns = countTable("skill_runs");
        long failedSkillRuns = countSkillRunsByStatus("FAILED");
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
                .totalSkillRuns(totalSkillRuns)
                .failedSkillRuns(failedSkillRuns)
                .totalEmbeddings(totalEmbeddings)
                .readyEmbeddings(readyEmbeddings)
                .failedEmbeddings(failedEmbeddings)
                .tableCounts(tableCounts)
                .embeddingStatuses(queryEmbeddingStatuses())
                .families(queryFamilySummaries())
                .suspiciousFamilies(querySuspiciousFamilies())
                .sessionStorageHealth(querySessionStorageHealth())
                .sessionArchiveRanges(querySessionArchiveRanges())
                .recentFailedEmbeddings(queryRecentFailedEmbeddings())
                .recentFailedSkillRuns(queryRecentFailedSkillRuns())
                .build();
    }

    public List<AdminUserSummary> listUsers() {
        requirePlatformAdmin();
        if (!tableExists("users")) {
            return List.of();
        }

        return jdbcTemplate.query("""
                SELECT id, username, nickname, role, status
                FROM users
                ORDER BY id ASC
                """, (rs, rowNum) -> AdminUserSummary.builder()
                .id(rs.getLong("id"))
                .username(rs.getString("username"))
                .nickname(rs.getString("nickname"))
                .role(rs.getString("role"))
                .status(rs.getString("status"))
                .build()).stream()
                .filter(Objects::nonNull)
                .toList();
    }

    public PageResult<AdminUserSummary> searchUsers(String keyword, int page, int pageSize) {
        requirePlatformAdmin();
        if (!tableExists("users")) {
            return PageResult.of(List.of(), 1, normalizePageSize(pageSize), 0);
        }

        String normalizedKeyword = normalizeKeyword(keyword);
        StringBuilder fromSql = new StringBuilder("""
                FROM users
                WHERE 1 = 1
                """);
        List<Object> args = new ArrayList<>();
        if (normalizedKeyword != null) {
            String likeKeyword = likeKeyword(normalizedKeyword);
            fromSql.append("""
                    AND (
                        CAST(id AS TEXT) ILIKE ?
                        OR COALESCE(username, '') ILIKE ?
                        OR COALESCE(nickname, '') ILIKE ?
                        OR COALESCE(role, '') ILIKE ?
                        OR COALESCE(status, '') ILIKE ?
                    )
                    """);
            for (int i = 0; i < 5; i += 1) {
                args.add(likeKeyword);
            }
        }

        int normalizedPageSize = normalizePageSize(pageSize);
        long total = queryCount("SELECT COUNT(*) " + fromSql, args);
        long resolvedPage = resolvePage(page, normalizedPageSize, total);
        long offset = (resolvedPage - 1L) * normalizedPageSize;

        List<AdminUserSummary> items = total == 0
                ? List.of()
                : jdbcTemplate.query(
                """
                        SELECT id, username, nickname, role, status
                        """
                        + fromSql
                        + """
                        ORDER BY id ASC
                        LIMIT ? OFFSET ?
                        """,
                (rs, rowNum) -> AdminUserSummary.builder()
                        .id(rs.getLong("id"))
                        .username(rs.getString("username"))
                        .nickname(rs.getString("nickname"))
                        .role(rs.getString("role"))
                        .status(rs.getString("status"))
                        .build(),
                concatArgs(args, normalizedPageSize, offset)).stream()
                .filter(Objects::nonNull)
                .toList();

        return PageResult.of(items, resolvedPage, normalizedPageSize, total);
    }

    public PageResult<FamilyDatabaseSummary> searchFamilies(String keyword, int page, int pageSize) {
        requirePlatformAdmin();
        if (!tableExists("families")) {
            return PageResult.of(List.of(), 1, normalizePageSize(pageSize), 0);
        }

        String normalizedKeyword = normalizeKeyword(keyword);
        StringBuilder fromSql = new StringBuilder("""
                FROM families f
                WHERE 1 = 1
                """);
        List<Object> args = new ArrayList<>();
        if (normalizedKeyword != null) {
            String likeKeyword = likeKeyword(normalizedKeyword);
            fromSql.append("""
                    AND (
                        CAST(f.id AS TEXT) ILIKE ?
                        OR COALESCE(f.name, '') ILIKE ?
                    )
                    """);
            args.add(likeKeyword);
            args.add(likeKeyword);
        }

        int normalizedPageSize = normalizePageSize(pageSize);
        long total = queryCount("SELECT COUNT(*) " + fromSql, args);
        long resolvedPage = resolvePage(page, normalizedPageSize, total);
        long offset = (resolvedPage - 1L) * normalizedPageSize;

        List<FamilyDatabaseSummary> items = total == 0
                ? List.of()
                : jdbcTemplate.query(
                """
                        SELECT
                            f.id AS family_id,
                            f.name AS family_name,
                            owner.owner_user_id,
                            owner.owner_display_name,
                            COALESCE(owner.owner_count, 0) = 0 AS owner_missing,
                            (SELECT COUNT(*) FROM family_members fm WHERE fm.family_id = f.id) AS member_count,
                            (SELECT COUNT(*) FROM diary_entries de WHERE de.family_id = f.id) AS diary_count,
                            (SELECT COUNT(*) FROM memory_entries me WHERE me.family_id = f.id) AS memory_count,
                            (SELECT COUNT(*) FROM growth_guard_records gr WHERE gr.family_id = f.id) AS growth_record_count,
                            (SELECT COUNT(*) FROM skill_runs sr WHERE sr.family_id = f.id) AS skill_run_count,
                            (SELECT COUNT(*) FROM skill_runs sr WHERE sr.family_id = f.id AND sr.status = 'FAILED') AS failed_skill_run_count,
                            (SELECT COUNT(*) FROM memory_embeddings em WHERE em.family_id = f.id AND em.status = 'READY') AS ready_embedding_count,
                            (SELECT COUNT(*) FROM memory_embeddings em WHERE em.family_id = f.id AND em.status = 'FAILED') AS failed_embedding_count
                        """
                        + """
                        LEFT JOIN LATERAL (
                            SELECT
                                MIN(fm.user_id) FILTER (WHERE UPPER(COALESCE(fm.role, '')) = 'OWNER') AS owner_user_id,
                                MIN(COALESCE(NULLIF(u.nickname, ''), u.username)) FILTER (WHERE UPPER(COALESCE(fm.role, '')) = 'OWNER') AS owner_display_name,
                                COUNT(*) FILTER (WHERE UPPER(COALESCE(fm.role, '')) = 'OWNER') AS owner_count
                            FROM family_members fm
                            LEFT JOIN users u ON u.id = fm.user_id
                            WHERE fm.family_id = f.id
                        ) owner ON TRUE
                        """
                        + fromSql
                        + """
                        ORDER BY f.updated_at DESC NULLS LAST, f.id DESC
                        LIMIT ? OFFSET ?
                        """,
                (rs, rowNum) -> FamilyDatabaseSummary.builder()
                        .familyId(rs.getLong("family_id"))
                        .familyName(rs.getString("family_name"))
                        .ownerUserId((Long) rs.getObject("owner_user_id"))
                        .ownerDisplayName(rs.getString("owner_display_name"))
                        .ownerMissing(rs.getBoolean("owner_missing"))
                        .memberCount(rs.getLong("member_count"))
                        .diaryCount(rs.getLong("diary_count"))
                        .memoryCount(rs.getLong("memory_count"))
                        .growthRecordCount(rs.getLong("growth_record_count"))
                        .skillRunCount(rs.getLong("skill_run_count"))
                        .failedSkillRunCount(rs.getLong("failed_skill_run_count"))
                        .readyEmbeddingCount(rs.getLong("ready_embedding_count"))
                        .failedEmbeddingCount(rs.getLong("failed_embedding_count"))
                        .build(),
                concatArgs(args, normalizedPageSize, offset));

        return PageResult.of(items, resolvedPage, normalizedPageSize, total);
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

    private static int normalizePageSize(int pageSize) {
        if (pageSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private static long resolvePage(int page, int pageSize, long total) {
        long normalizedPage = Math.max(page, 1);
        if (total <= 0) {
            return normalizedPage;
        }
        long totalPages = (total + pageSize - 1L) / pageSize;
        return Math.min(normalizedPage, totalPages);
    }

    private static String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String normalized = keyword.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static String likeKeyword(String keyword) {
        return "%" + keyword + "%";
    }

    private long queryCount(String sql, List<Object> args) {
        Long total = jdbcTemplate.queryForObject(sql, Long.class, args.toArray());
        return total == null ? 0 : total;
    }

    private static Object[] concatArgs(List<Object> args, Object... tail) {
        Object[] result = new Object[args.size() + tail.length];
        for (int i = 0; i < args.size(); i += 1) {
            result[i] = args.get(i);
        }
        for (int i = 0; i < tail.length; i += 1) {
            result[args.size() + i] = tail[i];
        }
        return result;
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

    private long countSkillRunsByStatus(String status) {
        if (!tableExists("skill_runs")) {
            return 0;
        }
        Number count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM skill_runs WHERE status = ?
                """, Number.class, status);
        return count == null ? 0 : count.longValue();
    }

    private void deleteIfTableExists(String tableName, String columnName, Long userId) {
        if (!tableExists(tableName)) {
            return;
        }
        jdbcTemplate.update("DELETE FROM " + tableName + " WHERE " + columnName + " = ?", userId);
    }

    private void updateNullIfTableExists(String tableName, String columnName, Long userId) {
        if (!tableExists(tableName)) {
            return;
        }
        jdbcTemplate.update("UPDATE " + tableName + " SET " + columnName + " = NULL WHERE " + columnName + " = ?", userId);
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
                    owner.owner_user_id,
                    owner.owner_display_name,
                    COALESCE(owner.owner_count, 0) = 0 AS owner_missing,
                    (SELECT COUNT(*) FROM family_members fm WHERE fm.family_id = f.id) AS member_count,
                    (SELECT COUNT(*) FROM diary_entries de WHERE de.family_id = f.id) AS diary_count,
                    (SELECT COUNT(*) FROM memory_entries me WHERE me.family_id = f.id) AS memory_count,
                    (SELECT COUNT(*) FROM growth_guard_records gr WHERE gr.family_id = f.id) AS growth_record_count,
                    (SELECT COUNT(*) FROM skill_runs sr WHERE sr.family_id = f.id) AS skill_run_count,
                    (SELECT COUNT(*) FROM skill_runs sr WHERE sr.family_id = f.id AND sr.status = 'FAILED') AS failed_skill_run_count,
                    (SELECT COUNT(*) FROM memory_embeddings em WHERE em.family_id = f.id AND em.status = 'READY') AS ready_embedding_count,
                    (SELECT COUNT(*) FROM memory_embeddings em WHERE em.family_id = f.id AND em.status = 'FAILED') AS failed_embedding_count
                FROM families f
                LEFT JOIN LATERAL (
                    SELECT
                        MIN(fm.user_id) FILTER (WHERE UPPER(COALESCE(fm.role, '')) = 'OWNER') AS owner_user_id,
                        MIN(COALESCE(NULLIF(u.nickname, ''), u.username)) FILTER (WHERE UPPER(COALESCE(fm.role, '')) = 'OWNER') AS owner_display_name,
                        COUNT(*) FILTER (WHERE UPPER(COALESCE(fm.role, '')) = 'OWNER') AS owner_count
                    FROM family_members fm
                    LEFT JOIN users u ON u.id = fm.user_id
                    WHERE fm.family_id = f.id
                ) owner ON TRUE
                ORDER BY f.updated_at DESC NULLS LAST, f.id DESC
                LIMIT 50
                """, (rs, rowNum) -> FamilyDatabaseSummary.builder()
                .familyId(rs.getLong("family_id"))
                .familyName(rs.getString("family_name"))
                .ownerUserId((Long) rs.getObject("owner_user_id"))
                .ownerDisplayName(rs.getString("owner_display_name"))
                .ownerMissing(rs.getBoolean("owner_missing"))
                .memberCount(rs.getLong("member_count"))
                .diaryCount(rs.getLong("diary_count"))
                .memoryCount(rs.getLong("memory_count"))
                .growthRecordCount(rs.getLong("growth_record_count"))
                .skillRunCount(rs.getLong("skill_run_count"))
                .failedSkillRunCount(rs.getLong("failed_skill_run_count"))
                .readyEmbeddingCount(rs.getLong("ready_embedding_count"))
                .failedEmbeddingCount(rs.getLong("failed_embedding_count"))
                .build());
    }

    private List<SuspiciousFamilySummary> querySuspiciousFamilies() {
        if (!tableExists("families") || !tableExists("family_members")) {
            return List.of();
        }
        return jdbcTemplate.query("""
                SELECT
                    f.id AS family_id,
                    f.name AS family_name,
                    COUNT(fm.id) AS member_count,
                    COUNT(*) FILTER (WHERE UPPER(COALESCE(fm.role, '')) = 'OWNER') AS owner_count
                FROM families f
                LEFT JOIN family_members fm ON fm.family_id = f.id
                GROUP BY f.id, f.name
                HAVING COUNT(fm.id) = 0
                    OR COUNT(*) FILTER (WHERE UPPER(COALESCE(fm.role, '')) = 'OWNER') = 0
                    OR COUNT(*) FILTER (WHERE UPPER(COALESCE(fm.role, '')) = 'OWNER') > 1
                ORDER BY f.id ASC
                """, (rs, rowNum) -> SuspiciousFamilySummary.builder()
                .familyId(rs.getLong("family_id"))
                .familyName(rs.getString("family_name"))
                .memberCount(rs.getLong("member_count"))
                .ownerCount(rs.getLong("owner_count"))
                .build());
    }

    private List<SessionStorageHealthSummary> querySessionStorageHealth() {
        if (!tableExists("chat_sessions")) {
            return List.of();
        }
        return jdbcTemplate.query("""
                SELECT
                    s.id AS session_id,
                    s.family_id,
                    s.message_count,
                    s.archived_before_seq,
                    s.archive_status,
                    COALESCE(live.live_count, 0) AS live_message_rows,
                    COALESCE(arch.archived_count, 0) AS archived_message_rows,
                    COALESCE(live.live_count, 0) + COALESCE(arch.archived_count, 0) AS total_materialized_rows
                FROM chat_sessions s
                LEFT JOIN (
                    SELECT session_id, COUNT(*) AS live_count
                    FROM chat_session_messages
                    GROUP BY session_id
                ) live ON live.session_id = s.id
                LEFT JOIN (
                    SELECT session_id, SUM(message_count) AS archived_count
                    FROM chat_session_archives
                    GROUP BY session_id
                ) arch ON arch.session_id = s.id
                WHERE s.message_count > 0
                ORDER BY s.last_message_at DESC NULLS LAST, s.id DESC
                LIMIT 100
                """, (rs, rowNum) -> SessionStorageHealthSummary.builder()
                .sessionId(rs.getLong("session_id"))
                .familyId((Long) rs.getObject("family_id"))
                .messageCount(rs.getInt("message_count"))
                .archivedBeforeSeq(rs.getInt("archived_before_seq"))
                .archiveStatus(rs.getString("archive_status"))
                .liveMessageRows(rs.getLong("live_message_rows"))
                .archivedMessageRows(rs.getLong("archived_message_rows"))
                .totalMaterializedRows(rs.getLong("total_materialized_rows"))
                .build());
    }

    private List<SessionArchiveRangeSummary> querySessionArchiveRanges() {
        if (!tableExists("chat_session_archives")) {
            return List.of();
        }
        return jdbcTemplate.query("""
                SELECT
                    session_id,
                    id AS archive_id,
                    start_seq,
                    end_seq,
                    message_count,
                    created_at
                FROM chat_session_archives
                ORDER BY session_id ASC, start_seq ASC, id ASC
                LIMIT 200
                """, (rs, rowNum) -> SessionArchiveRangeSummary.builder()
                .sessionId(rs.getLong("session_id"))
                .archiveId(rs.getLong("archive_id"))
                .startSeq(rs.getInt("start_seq"))
                .endSeq(rs.getInt("end_seq"))
                .messageCount(rs.getInt("message_count"))
                .createdAt(rs.getTimestamp("created_at") == null
                        ? null
                        : rs.getTimestamp("created_at").toLocalDateTime())
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

    private List<FailedSkillRunSummary> queryRecentFailedSkillRuns() {
        if (!tableExists("skill_runs")) {
            return List.of();
        }
        return jdbcTemplate.query("""
                SELECT id, family_id, triggered_by, skill_name, source, input_summary, output_summary, updated_at
                FROM skill_runs
                WHERE status = 'FAILED'
                ORDER BY updated_at DESC
                LIMIT 10
                """, (rs, rowNum) -> FailedSkillRunSummary.builder()
                .id(rs.getLong("id"))
                .familyId(rs.getLong("family_id"))
                .triggeredBy(rs.getLong("triggered_by"))
                .skillName(rs.getString("skill_name"))
                .source(rs.getString("source"))
                .inputSummary(rs.getString("input_summary"))
                .outputSummary(rs.getString("output_summary"))
                .updatedAt(rs.getTimestamp("updated_at") == null
                        ? null
                        : rs.getTimestamp("updated_at").toLocalDateTime())
                .build());
    }

    private record TableDefinition(String tableName, String label, boolean legacy) {
    }
}
