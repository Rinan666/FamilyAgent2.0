package com.familyagent.module.memory.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class UnifiedMemoryCutoverMigrationTest {

    @Test
    void migrationMovesDependentForeignKeysBeforeDroppingLegacyTables() throws IOException {
        String sql = migrationSql();

        assertTrue(sql.contains("RENAME COLUMN record_id TO memory_entry_id"));
        assertTrue(sql.contains("REFERENCES memory_entries(id) ON DELETE CASCADE"));
        assertTrue(sql.contains("ALTER COLUMN memory_entry_id SET NOT NULL"));
        assertTrue(sql.indexOf("UPDATE agent_record_provenance") < sql.indexOf("DROP TABLE diary_entries"));
        assertTrue(sql.indexOf("UPDATE growth_guard_staleness_votes") < sql.indexOf("DROP TABLE growth_guard_records"));
        assertTrue(sql.contains("CREATE SEQUENCE unified_diary_record_id_seq"));
        assertTrue(sql.contains("CREATE SEQUENCE unified_growth_record_id_seq"));
    }

    private static String migrationSql() throws IOException {
        try (InputStream stream = UnifiedMemoryCutoverMigrationTest.class.getResourceAsStream(
                "/db/migration/V22__complete_unified_memory_cutover.sql")) {
            if (stream == null) {
                throw new IOException("V22 migration was not found");
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
