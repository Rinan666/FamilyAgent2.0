package com.familyagent.module.memory.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JdbcMemoryRecallVectorRepositoryTest {

    @Mock private JdbcTemplate jdbcTemplate;

    @Test
    void rankSourceIds_shouldBindVectorQueryParametersAndReturnRankedIds() {
        when(jdbcTemplate.queryForList(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(List.of(55L, 44L));
        JdbcMemoryRecallVectorRepository repository = new JdbcMemoryRecallVectorRepository(jdbcTemplate);

        List<Long> result = repository.rankSourceIds(
                11L,
                "DIARY",
                List.of(44L, 55L),
                List.of(0.1, -0.2),
                0.72,
                2);

        assertEquals(List.of(55L, 44L), result);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> paramsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).queryForList(
                sqlCaptor.capture(),
                eq(Long.class),
                paramsCaptor.capture());
        assertTrue(sqlCaptor.getValue().contains("source_id IN (?,?)"));
        assertTrue(sqlCaptor.getValue().contains("status = 'READY'"));
        assertArrayEquals(
                new Object[]{11L, "DIARY", 44L, 55L, "[0.1,-0.2]", 0.72, "[0.1,-0.2]", 2},
                paramsCaptor.getValue());
    }

    @Test
    void rankSourceIds_shouldSkipDatabaseWhenCandidatesAreEmpty() {
        JdbcMemoryRecallVectorRepository repository = new JdbcMemoryRecallVectorRepository(jdbcTemplate);

        List<Long> result = repository.rankSourceIds(
                11L,
                "MEMORY",
                List.of(),
                List.of(0.1),
                0.72,
                3);

        assertEquals(List.of(), result);
        verifyNoInteractions(jdbcTemplate);
    }
}
