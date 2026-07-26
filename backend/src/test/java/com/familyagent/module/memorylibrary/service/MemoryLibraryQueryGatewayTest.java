package com.familyagent.module.memorylibrary.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemoryLibraryQueryGatewayTest {

    @Mock private JdbcTemplate jdbcTemplate;

    @Test
    void query_shouldBindUnifiedMemoryPermissionsAndPagination() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(2L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of());
        MemoryLibraryQueryGateway gateway = new MemoryLibraryQueryGateway(
                jdbcTemplate,
                new ObjectMapper());
        MemoryLibraryQueryGateway.QueryCriteria criteria =
                new MemoryLibraryQueryGateway.QueryCriteria(
                        10L,
                        101L,
                        List.of("teeth"),
                        "ALL",
                        null,
                        null,
                        null,
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 7, 1),
                        false,
                        3,
                        6);

        MemoryLibraryQueryGateway.QueryResult result = gateway.query(criteria);

        assertEquals(2L, result.total());
        ArgumentCaptor<Object[]> countArgs = ArgumentCaptor.forClass(Object[].class);
        ArgumentCaptor<Object[]> listArgs = ArgumentCaptor.forClass(Object[].class);
        ArgumentCaptor<String> countSql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> listSql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(countSql.capture(), eq(Long.class), countArgs.capture());
        verify(jdbcTemplate).query(listSql.capture(), any(RowMapper.class), listArgs.capture());

        assertTrue(listSql.getValue().contains("FROM memory_entries me"));
        assertTrue(listSql.getValue().contains("me.origin_type = 'DIARY'"));
        assertFalse(listSql.getValue().contains("UNION ALL"));
        assertUnifiedArgs(countArgs.getValue(), false);
        assertUnifiedArgs(listArgs.getValue(), true);
        assertEquals(countQuestionMarks(countSql.getValue()), countArgs.getValue().length);
        assertEquals(countQuestionMarks(listSql.getValue()), listArgs.getValue().length);
    }

    private static void assertUnifiedArgs(Object[] args, boolean includesPagination) {
        int expectedLength = includesPagination ? 20 : 18;
        assertEquals(expectedLength, args.length);
        assertEquals(10L, args[0]);
        assertEquals(101L, args[1]);
        assertEquals(101L, args[2]);
        assertEquals(101L, args[3]);
        if (includesPagination) {
            assertEquals(3, args[18]);
            assertEquals(6, args[19]);
        }
    }

    private static int countQuestionMarks(String sql) {
        int count = 0;
        for (int index = 0; index < sql.length(); index++) {
            if (sql.charAt(index) == '?') {
                count++;
            }
        }
        return count;
    }
}
