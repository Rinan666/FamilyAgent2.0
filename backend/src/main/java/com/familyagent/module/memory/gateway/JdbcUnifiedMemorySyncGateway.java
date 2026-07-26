package com.familyagent.module.memory.gateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.familyagent.common.constant.MemoryOriginType;
import com.familyagent.module.memory.facade.UnifiedMemorySyncRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;

@Repository
@RequiredArgsConstructor
public class JdbcUnifiedMemorySyncGateway implements UnifiedMemorySyncGateway {

    private static final String UPSERT_SQL = """
        INSERT INTO memory_entries (
          user_id, family_id, library_kind, title, related_user_id, type, scope,
          content, summary, importance, confidence, status, tags, occurred_at,
          origin_type, origin_id, metadata, created_at, updated_at
        ) VALUES (?, ?, 'FAMILY', ?, ?, ?, ?, ?, ?, 3, 0.8500, ?, ?, ?, ?, ?, ?::jsonb, NOW(), NOW())
        ON CONFLICT (origin_type, origin_id)
          WHERE origin_type IS NOT NULL AND origin_id IS NOT NULL
        DO UPDATE SET
          user_id = EXCLUDED.user_id,
          family_id = EXCLUDED.family_id,
          title = EXCLUDED.title,
          related_user_id = EXCLUDED.related_user_id,
          type = EXCLUDED.type,
          scope = EXCLUDED.scope,
          content = EXCLUDED.content,
          summary = EXCLUDED.summary,
          status = EXCLUDED.status,
          tags = EXCLUDED.tags,
          occurred_at = EXCLUDED.occurred_at,
          metadata = COALESCE(memory_entries.metadata, '{}'::jsonb) || EXCLUDED.metadata,
          updated_at = NOW()
        RETURNING id
        """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public Long upsert(UnifiedMemorySyncRequest request) {
        return jdbcTemplate.execute((PreparedStatementCreator) connection -> {
            PreparedStatement statement = connection.prepareStatement(UPSERT_SQL);
            statement.setLong(1, request.ownerUserId());
            statement.setLong(2, request.familyId());
            statement.setString(3, truncate(request.title(), 120));
            if (request.relatedUserId() == null) {
                statement.setNull(4, java.sql.Types.BIGINT);
            } else {
                statement.setLong(4, request.relatedUserId());
            }
            statement.setString(5, request.type().name());
            statement.setString(6, request.visibility().name());
            statement.setString(7, request.content());
            statement.setString(8, summary(request.title(), request.content()));
            statement.setString(9, request.status().name());
            Array tags = connection.createArrayOf("text", request.tags().toArray(String[]::new));
            statement.setArray(10, tags);
            if (request.occurredAt() == null) {
                statement.setNull(11, java.sql.Types.TIMESTAMP);
            } else {
                statement.setTimestamp(11, Timestamp.valueOf(request.occurredAt()));
            }
            statement.setString(12, request.originType().name());
            statement.setLong(13, request.originId());
            statement.setString(14, serializeMetadata(request));
            return statement;
        }, (PreparedStatementCallback<Long>) JdbcUnifiedMemorySyncGateway::readId);
    }

    @Override
    public Long delete(MemoryOriginType originType, Long originId) {
        return jdbcTemplate.query(
                        "DELETE FROM memory_entries WHERE origin_type = ? AND origin_id = ? RETURNING id",
                        (resultSet, rowNumber) -> resultSet.getLong(1),
                        originType.name(),
                        originId)
                .stream()
                .findFirst()
                .orElse(null);
    }

    private static Long readId(PreparedStatement statement) throws java.sql.SQLException {
        try (ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                throw new java.sql.SQLException("Unified memory upsert returned no id");
            }
            return resultSet.getLong(1);
        }
    }

    private String serializeMetadata(UnifiedMemorySyncRequest request) {
        try {
            return objectMapper.writeValueAsString(request.metadata());
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Unified memory metadata serialization failed", error);
        }
    }

    private static String summary(String title, String content) {
        String preferred = title == null || title.isBlank() ? content.trim() : title.trim();
        return preferred.length() <= 120 ? preferred : preferred.substring(0, 120);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }
}
