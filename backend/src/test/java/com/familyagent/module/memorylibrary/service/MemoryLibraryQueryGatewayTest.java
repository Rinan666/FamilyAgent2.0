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
    void query_shouldBindPermissionSectionsAndPagination() {
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

        assertTrue(listSql.getValue().contains("jsonb_typeof(me.metadata->'tags')"));
        assertTrue(listSql.getValue().contains("jsonb_typeof(gr.metadata->'tags')"));
        assertPermissionSectionArgs(countArgs.getValue(), false);
        assertPermissionSectionArgs(listArgs.getValue(), true);
        assertEquals(countQuestionMarks(countSql.getValue()), countArgs.getValue().length);
        assertEquals(countQuestionMarks(listSql.getValue()), listArgs.getValue().length);
    }

    private static void assertPermissionSectionArgs(Object[] args, boolean includesPagination) {
        int expectedLength = includesPagination ? 57 : 55;
        assertEquals(expectedLength, args.length);
        assertSection(args, 0, 18);
        assertSection(args, 18, 18);
        assertSection(args, 36, 19);
        if (includesPagination) {
            assertEquals(3, args[55]);
            assertEquals(6, args[56]);
        }
    }

    private static void assertSection(Object[] args, int offset, int length) {
        assertEquals(10L, args[offset]);
        assertEquals(101L, args[offset + 1]);
        assertEquals(101L, args[offset + 2]);
        assertEquals(101L, args[offset + 3]);
        if (length == 19) {
            assertEquals(101L, args[offset + 4]);
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
