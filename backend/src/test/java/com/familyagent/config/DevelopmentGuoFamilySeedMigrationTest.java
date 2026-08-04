package com.familyagent.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DevelopmentGuoFamilySeedMigrationTest {

    private static final String MIGRATION = "/db/dev-migration/R__seed_development_guo_family.sql";
    private static final Pattern BCRYPT_HASH = Pattern.compile("\\$2a\\$10\\$[./A-Za-z0-9]{53}");

    @Test
    void seedCreatesAccountsOwnerRelationshipsAndPersonalMemories() throws IOException {
        String sql = migrationSql();

        assertTrue(sql.contains("('guo001', '郭明远', 'ADMIN'"));
        assertTrue(sql.contains("('guo002', '林雅静', 'USER'"));
        assertTrue(sql.contains("('guo003', '郭子轩', 'USER'"));
        assertTrue(sql.contains("('guo004', '郭雨桐', 'USER'"));
        assertTrue(sql.contains("('guo001', 'OWNER'"));
        assertEquals(12, countRows(relationshipBlock(sql),
                Pattern.compile("\\('guo00[1-4]', 'guo00[1-4]',")));

        String memoryBlock = memoryBlock(sql);
        for (String username : new String[]{"guo001", "guo002", "guo003", "guo004"}) {
            assertEquals(10, countRows(memoryBlock,
                    Pattern.compile("\\('" + username + "', \\d+,")));
        }
        assertTrue(sql.contains("'PRIVATE'"));
        assertTrue(sql.contains("'ALL_FAMILIES_VISIBLE'"));
        assertTrue(sql.contains("'SELECTED_FAMILIES_VISIBLE'"));
        assertTrue(sql.contains("'CARE_VISIBLE'"));
    }

    @Test
    void seedPasswordHashMatchesDocumentedDevelopmentPassword() throws IOException {
        Matcher matcher = BCRYPT_HASH.matcher(migrationSql());

        assertTrue(matcher.find());
        assertTrue(new BCryptPasswordEncoder().matches("123456", matcher.group()));
    }

    private static String relationshipBlock(String sql) {
        int start = sql.indexOf("seed_relationships(");
        int end = sql.indexOf("INSERT INTO family_relationships", start);
        assertTrue(start >= 0 && end > start);
        return sql.substring(start, end);
    }

    private static String memoryBlock(String sql) {
        int start = sql.indexOf("INSERT INTO demo_memory_seed");
        int end = sql.indexOf("CREATE TEMP TABLE demo_prepared_memories", start);
        assertTrue(start >= 0 && end > start);
        return sql.substring(start, end);
    }

    private static int countRows(String block, Pattern pattern) {
        int count = 0;
        Matcher matcher = pattern.matcher(block);
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private static String migrationSql() throws IOException {
        try (var stream = DevelopmentGuoFamilySeedMigrationTest.class.getResourceAsStream(MIGRATION)) {
            assertNotNull(stream, "Development family seed migration is missing");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
