package com.familyagent.module.memorylibrary.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.familyagent.module.memorylibrary.dto.MemoryLibraryItem;
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
import java.util.Map;

@Component
@RequiredArgsConstructor
public class MemoryLibraryQueryGateway {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public QueryResult query(QueryCriteria criteria) {
        Object[] args = concat(
                sectionArgs(criteria),
                sectionArgs(criteria),
                growthSectionArgs(criteria));
        String sql = MemoryLibraryQuerySql.fullQuery(criteria.archived());
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM (" + sql + ") items",
                Long.class,
                args);
        List<MemoryLibraryItem> items = jdbcTemplate.query(
                "SELECT * FROM (" + sql + ") items ORDER BY sort_time DESC, id DESC LIMIT ? OFFSET ?",
                (resultSet, rowNumber) -> mapItem(resultSet),
                concat(args, criteria.limit(), criteria.offset()));
        return new QueryResult(items, total == null ? 0L : total);
    }

    private MemoryLibraryItem mapItem(ResultSet resultSet) throws SQLException {
        return MemoryLibraryItem.builder()
                .id(resultSet.getString("id"))
                .sourceType(resultSet.getString("source_type"))
                .type(resultSet.getString("type"))
                .title(resultSet.getString("title"))
                .body(resultSet.getString("body"))
                .familyId(resultSet.getLong("family_id"))
                .authorUserId(resultSet.getLong("author_user_id"))
                .memberUserId(resultSet.getLong("member_user_id"))
                .memberName(resultSet.getString("member_name"))
                .visibility(resultSet.getString("visibility"))
                .tags(readStringArray(resultSet.getArray("tags")))
                .metadata(readMap(resultSet.getObject("metadata")))
                .createdAt(readDateTime(resultSet, "created_at"))
                .updatedAt(readDateTime(resultSet, "updated_at"))
                .build();
    }

    private static Object[] sectionArgs(QueryCriteria criteria) {
        String[] terms = criteria.searchTerms().toArray(String[]::new);
        return new Object[] {
                criteria.familyId(), criteria.viewerUserId(), criteria.viewerUserId(), criteria.viewerUserId(),
                terms, terms, criteria.type(), criteria.type(), criteria.memberUserId(), criteria.memberUserId(),
                criteria.visibility(), criteria.visibility(), criteria.tag(), criteria.tag(),
                criteria.dateFrom(), criteria.dateFrom(), criteria.dateTo(), criteria.dateTo()
        };
    }

    private static Object[] growthSectionArgs(QueryCriteria criteria) {
        String[] terms = criteria.searchTerms().toArray(String[]::new);
        return new Object[] {
                criteria.familyId(), criteria.viewerUserId(), criteria.viewerUserId(), criteria.viewerUserId(),
                criteria.viewerUserId(), terms, terms, criteria.type(), criteria.type(),
                criteria.memberUserId(), criteria.memberUserId(), criteria.visibility(), criteria.visibility(),
                criteria.tag(), criteria.tag(), criteria.dateFrom(), criteria.dateFrom(),
                criteria.dateTo(), criteria.dateTo()
        };
    }

    static Object[] concat(Object[] args, Object... tail) {
        Object[] result = new Object[args.length + tail.length];
        System.arraycopy(args, 0, result, 0, args.length);
        System.arraycopy(tail, 0, result, args.length, tail.length);
        return result;
    }

    static Object[] concat(Object[]... groups) {
        int length = 0;
        for (Object[] group : groups) {
            length += group.length;
        }
        Object[] result = new Object[length];
        int offset = 0;
        for (Object[] group : groups) {
            System.arraycopy(group, 0, result, offset, group.length);
            offset += group.length;
        }
        return result;
    }

    private static LocalDateTime readDateTime(ResultSet resultSet, String column) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private static String[] readStringArray(Array array) throws SQLException {
        if (array == null) {
            return new String[0];
        }
        Object raw = array.getArray();
        if (raw instanceof String[] values) {
            return values;
        }
        if (raw instanceof Object[] values) {
            String[] result = new String[values.length];
            for (int index = 0; index < values.length; index++) {
                result[index] = values[index] == null ? "" : String.valueOf(values[index]);
            }
            return result;
        }
        return new String[0];
    }

    private Map<String, Object> readMap(Object value) {
        if (value == null) {
            return Collections.emptyMap();
        }
        if (value instanceof Map<?, ?> map) {
            return objectMapper.convertValue(map, new TypeReference<>() { });
        }
        String json = value instanceof String text ? text : String.valueOf(value);
        if (json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (Exception ignored) {
            return Collections.emptyMap();
        }
    }

    public record QueryCriteria(
            Long familyId,
            Long viewerUserId,
            List<String> searchTerms,
            String type,
            Long memberUserId,
            String visibility,
            String tag,
            LocalDate dateFrom,
            LocalDate dateTo,
            boolean archived,
            int limit,
            int offset) {

        public QueryCriteria {
            searchTerms = searchTerms == null ? List.of() : List.copyOf(searchTerms);
        }
    }

    public record QueryResult(List<MemoryLibraryItem> items, long total) {

        public QueryResult {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }
}
