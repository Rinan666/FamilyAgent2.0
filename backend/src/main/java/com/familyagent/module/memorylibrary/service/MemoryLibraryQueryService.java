package com.familyagent.module.memorylibrary.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.common.response.PageResult;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.family.facade.MemoryLibraryFamilyFacade;
import com.familyagent.module.growth.dto.GrowthStalenessStats;
import com.familyagent.module.growth.repository.GrowthGuardStalenessVoteRepository;
import com.familyagent.module.memory.dto.MemoryVoteStats;
import com.familyagent.module.memory.repository.MemoryEntryVoteRepository;
import com.familyagent.module.memorylibrary.dto.MemoryLibraryItem;
import com.familyagent.module.memorylibrary.dto.MemoryLibrarySearchRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 记忆库统一 JDBC 查询，从 MemoryLibraryService 拆出。
 */
@Component
@RequiredArgsConstructor
public class MemoryLibraryQueryService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 12;
    private static final int MAX_PAGE_SIZE = 48;
    private static final Set<String> TYPES = Set.of(
            "ALL", "LIFE_RECORD", "FAMILY_EXPERIENCE", "GROWTH_OBSERVATION", "AI_SUMMARY");

    private final JdbcTemplate jdbcTemplate;
    private final MemoryLibraryFamilyFacade familyService;
    private final ObjectMapper objectMapper;
    private final MemoryEntryVoteRepository memoryEntryVoteRepository;
    private final GrowthGuardStalenessVoteRepository growthGuardStalenessVoteRepository;

    public PageResult<MemoryLibraryItem> search(MemoryLibrarySearchRequest request) {
        return query(request, false);
    }

    public PageResult<MemoryLibraryItem> archived(MemoryLibrarySearchRequest request) {
        return query(request, true);
    }

    PageResult<MemoryLibraryItem> query(MemoryLibrarySearchRequest request, boolean archived) {
        if (request.getFamilyId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "familyId 不能为空");
        }
        familyService.checkMembership(request.getFamilyId());
        Long viewerUserId = CurrentUserGuard.currentUserId();
        String type = normalizeType(request.getType());
        String keyword = MemoryLibrarySupport.blankToNull(request.getKeyword());
        List<String> searchTerms = MemoryLibrarySupport.searchTerms(keyword);
        Long memberUserId = request.getMemberUserId();
        String visibility = MemoryLibrarySupport.blankToNull(request.getVisibility());
        String tag = MemoryLibrarySupport.blankToNull(request.getTag());
        LocalDate dateFrom = request.getDateFrom();
        LocalDate dateTo = request.getDateTo();
        int page = normalizePage(request.getPage());
        int pageSize = normalizePageSize(request.getPageSize());
        int offset = (page - 1) * pageSize;

        Object[] args = concat(
                sectionArgs(request.getFamilyId(), viewerUserId, searchTerms, type, memberUserId, visibility, tag, dateFrom, dateTo),
                sectionArgs(request.getFamilyId(), viewerUserId, searchTerms, type, memberUserId, visibility, tag, dateFrom, dateTo),
                growthSectionArgs(request.getFamilyId(), viewerUserId, searchTerms, type, memberUserId, visibility, tag, dateFrom, dateTo));

        String sql = MemoryLibraryQuerySql.fullQuery(archived);
        long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM (" + sql + ") items", Long.class, args);
        List<MemoryLibraryItem> items = jdbcTemplate.query(
                "SELECT * FROM (" + sql + ") items ORDER BY sort_time DESC, id DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> mapItem(rs),
                concat(args, pageSize, offset));
        items.forEach(item -> attachDynamicSignals(item, viewerUserId));
        return PageResult.of(items, page, pageSize, total);
    }

    private MemoryLibraryItem mapItem(ResultSet rs) throws SQLException {
        return MemoryLibraryItem.builder()
                .id(rs.getString("id"))
                .sourceType(rs.getString("source_type"))
                .type(rs.getString("type"))
                .title(rs.getString("title"))
                .body(rs.getString("body"))
                .familyId(rs.getLong("family_id"))
                .authorUserId(rs.getLong("author_user_id"))
                .memberUserId(rs.getLong("member_user_id"))
                .memberName(rs.getString("member_name"))
                .visibility(rs.getString("visibility"))
                .tags(readStringArray(rs.getArray("tags")))
                .metadata(readMap(rs.getObject("metadata")))
                .createdAt(readDateTime(rs, "created_at"))
                .updatedAt(readDateTime(rs, "updated_at"))
                .build();
    }

    private void attachDynamicSignals(MemoryLibraryItem item, Long viewerUserId) {
        if (item == null || item.getId() == null || item.getId().isBlank()) return;
        MemoryLibrarySupport.ParsedItemId parsed = MemoryLibrarySupport.parseItemId(item.getId());
        Map<String, Object> metadata = MemoryLibrarySupport.mutableMap(item.getMetadata());
        if ("memory".equals(parsed.prefix()) && "FAMILY_EXPERIENCE".equals(item.getSourceType())) {
            MemoryVoteStats stats = memoryEntryVoteRepository.statsByMemoryId(parsed.id(), viewerUserId);
            if (stats == null) stats = new MemoryVoteStats(parsed.id(), 0, 0, 0, 1.0, null);
            metadata.put("voteStats", Map.of(
                    "memoryId", parsed.id(),
                    "upVotes", stats.getUpVotes(),
                    "downVotes", stats.getDownVotes(),
                    "voteScore", stats.getVoteScore(),
                    "consensusWeight", stats.getConsensusWeight(),
                    "myVote", stats.getMyVote() == null ? "" : stats.getMyVote()));
        }
        if ("growth".equals(parsed.prefix()) && "GROWTH_OBSERVATION".equals(item.getSourceType())) {
            GrowthStalenessStats stats = growthGuardStalenessVoteRepository.statsByRecordId(parsed.id(), viewerUserId);
            if (stats == null) stats = new GrowthStalenessStats(parsed.id(), 0, 1.0, false);
            metadata.put("stalenessStats", Map.of(
                    "recordId", parsed.id(),
                    "staleVotes", stats.getStaleVotes(),
                    "stalenessWeight", stats.getStalenessWeight(),
                    "myVoted", stats.isMyVoted()));
        }
        item.setMetadata(metadata);
    }

    private static Object[] sectionArgs(Long familyId, Long viewerUserId, List<String> searchTerms,
            String type, Long memberUserId, String visibility, String tag, LocalDate dateFrom, LocalDate dateTo) {
        String[] terms = searchTerms.toArray(String[]::new);
        return new Object[] { familyId, viewerUserId, viewerUserId, viewerUserId,
                terms, terms, type, type, memberUserId, memberUserId, visibility, visibility,
                tag, tag, dateFrom, dateFrom, dateTo, dateTo };
    }

    private static Object[] growthSectionArgs(Long familyId, Long viewerUserId, List<String> searchTerms,
            String type, Long memberUserId, String visibility, String tag, LocalDate dateFrom, LocalDate dateTo) {
        String[] terms = searchTerms.toArray(String[]::new);
        return new Object[] { familyId, viewerUserId, viewerUserId, viewerUserId, viewerUserId,
                terms, terms, type, type, memberUserId, memberUserId, visibility, visibility,
                tag, tag, dateFrom, dateFrom, dateTo, dateTo };
    }

    static Object[] concat(Object[] args, Object... tail) {
        Object[] result = new Object[args.length + tail.length];
        System.arraycopy(args, 0, result, 0, args.length);
        System.arraycopy(tail, 0, result, args.length, tail.length);
        return result;
    }

    static Object[] concat(Object[]... groups) {
        int length = 0;
        for (Object[] g : groups) length += g.length;
        Object[] result = new Object[length];
        int offset = 0;
        for (Object[] g : groups) { System.arraycopy(g, 0, result, offset, g.length); offset += g.length; }
        return result;
    }

    private static int normalizePage(Integer page) {
        return page == null || page <= 0 ? DEFAULT_PAGE : page;
    }

    private static int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize <= 0) return DEFAULT_PAGE_SIZE;
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    static String normalizeType(String type) {
        String normalized = type == null || type.isBlank() ? "ALL" : type.trim().toUpperCase(Locale.ROOT);
        if (!TYPES.contains(normalized)) throw new BusinessException(ErrorCode.BAD_REQUEST, "记忆库类型不支持");
        return normalized;
    }

    private static LocalDateTime readDateTime(ResultSet rs, String column) throws SQLException {
        Timestamp ts = rs.getTimestamp(column);
        return ts == null ? null : ts.toLocalDateTime();
    }

    private static String[] readStringArray(Array array) throws SQLException {
        if (array == null) return new String[0];
        Object raw = array.getArray();
        if (raw instanceof String[] values) return values;
        if (raw instanceof Object[] values) {
            String[] result = new String[values.length];
            for (int i = 0; i < values.length; i++) result[i] = values[i] == null ? "" : String.valueOf(values[i]);
            return result;
        }
        return new String[0];
    }

    private Map<String, Object> readMap(Object value) {
        if (value == null) return Collections.emptyMap();
        if (value instanceof Map<?, ?> map) return objectMapper.convertValue(map, new TypeReference<>() {});
        String json = value instanceof String text ? text : String.valueOf(value);
        if (json == null || json.isBlank()) return Collections.emptyMap();
        try { return objectMapper.readValue(json, new TypeReference<>() {}); }
        catch (Exception ignored) { return Collections.emptyMap(); }
    }
}
