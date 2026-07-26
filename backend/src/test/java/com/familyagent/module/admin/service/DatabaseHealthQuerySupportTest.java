package com.familyagent.module.admin.service;

import com.familyagent.module.admin.dto.DatabaseHealthResponse;
import com.familyagent.module.admin.dto.DatabaseTableCount;
import com.familyagent.module.family.service.FamilyService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatabaseHealthQuerySupportTest {

    @Test
    void healthUsesUnifiedMemoryCountAndMarksPhysicalSourceTablesLegacy() {
        PlatformAdminAccessSupport accessSupport = mock(PlatformAdminAccessSupport.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        FamilyService familyService = mock(FamilyService.class);
        when(jdbcTemplate.queryForObject(
                eq("SELECT to_regclass(?) IS NOT NULL"),
                eq(Boolean.class),
                anyString())).thenReturn(true);
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class))).thenReturn(true);
        when(jdbcTemplate.queryForObject("SELECT current_database()", String.class)).thenReturn("test");
        when(jdbcTemplate.queryForObject(anyString(), eq(Number.class))).thenReturn(0L);
        when(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM diary_entries", Number.class)).thenReturn(54L);
        when(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM memory_entries", Number.class)).thenReturn(123L);
        when(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM growth_guard_records", Number.class)).thenReturn(24L);
        when(jdbcTemplate.queryForObject(anyString(), eq(Number.class), anyString())).thenReturn(0L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(List.of());
        DatabaseHealthQuerySupport support = new DatabaseHealthQuerySupport(
                accessSupport,
                jdbcTemplate,
                familyService);

        DatabaseHealthResponse health = support.getHealth();

        assertEquals(123L, health.getTotalCoreRecords());
        assertTrue(table(health, "diary_entries").isLegacy());
        assertFalse(table(health, "memory_entries").isLegacy());
        assertTrue(table(health, "growth_guard_records").isLegacy());
        assertEquals(54L, table(health, "diary_entries").getCount());
        assertEquals(123L, table(health, "memory_entries").getCount());
        assertEquals(24L, table(health, "growth_guard_records").getCount());
    }

    private static DatabaseTableCount table(DatabaseHealthResponse health, String tableName) {
        return health.getTableCounts().stream()
                .filter(table -> tableName.equals(table.getTableName()))
                .findFirst()
                .orElseThrow();
    }
}
