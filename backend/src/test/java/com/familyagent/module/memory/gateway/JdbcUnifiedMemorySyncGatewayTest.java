package com.familyagent.module.memory.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.familyagent.common.constant.EntityStatus;
import com.familyagent.common.constant.MemoryContentType;
import com.familyagent.common.constant.MemoryOriginType;
import com.familyagent.common.constant.MemoryScope;
import com.familyagent.module.memory.facade.UnifiedMemoryCreateResult;
import com.familyagent.module.memory.facade.UnifiedMemorySyncMetadata;
import com.familyagent.module.memory.facade.UnifiedMemorySyncRequest;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.jdbc.core.PreparedStatementCreator;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcUnifiedMemorySyncGatewayTest {

    @Test
    void insertBindsOriginTypeSequenceAndFlattenedMetadataToExpectedSlots() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        Array tags = mock(Array.class);
        LocalDateTime timestamp = LocalDateTime.of(2026, 7, 27, 12, 0);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(connection.createArrayOf(eq("text"), any(Object[].class))).thenReturn(tags);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getLong("id")).thenReturn(151L);
        when(resultSet.getLong("origin_id")).thenReturn(51L);
        when(resultSet.getTimestamp("created_at")).thenReturn(Timestamp.valueOf(timestamp));
        when(resultSet.getTimestamp("updated_at")).thenReturn(Timestamp.valueOf(timestamp));
        when(jdbcTemplate.execute(
                any(PreparedStatementCreator.class),
                any(PreparedStatementCallback.class)))
                .thenAnswer(invocation -> {
                    PreparedStatementCreator creator = invocation.getArgument(0);
                    PreparedStatementCallback<?> callback = invocation.getArgument(1);
                    return callback.doInPreparedStatement(creator.createPreparedStatement(connection));
                });
        JdbcUnifiedMemorySyncGateway gateway = new JdbcUnifiedMemorySyncGateway(
                jdbcTemplate,
                new ObjectMapper());
        UnifiedMemorySyncRequest request = new UnifiedMemorySyncRequest(
                10L,
                1L,
                22L,
                MemoryContentType.NOTE,
                MemoryScope.PRIVATE,
                "Today",
                "Keep this",
                List.of("family"),
                timestamp,
                MemoryOriginType.DIARY,
                null,
                UnifiedMemorySyncMetadata.diary(
                        "DAILY",
                        null,
                        "DIARY_MANUAL",
                        null,
                        Map.of("diaryDate", "2026-07-27")),
                EntityStatus.ACTIVE);

        UnifiedMemoryCreateResult result = gateway.insert(request);

        assertEquals(151L, result.memoryEntryId());
        assertEquals(51L, result.originId());
        verify(statement).setString(12, "DIARY");
        verify(statement).setString(13, "unified_diary_record_id_seq");
        verify(statement).setString(eq(14), contains("\"diaryDate\":\"2026-07-27\""));
    }
}
