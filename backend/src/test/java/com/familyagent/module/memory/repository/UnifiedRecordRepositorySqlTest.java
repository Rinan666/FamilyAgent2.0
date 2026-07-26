package com.familyagent.module.memory.repository;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnifiedRecordRepositorySqlTest {

    @Test
    void diaryCompatibilityQueriesReadOnlyUnifiedMemoryEntries() {
        for (Method method : UnifiedDiaryRecordRepository.class.getDeclaredMethods()) {
            Select select = method.getAnnotation(Select.class);
            assertTrue(select != null, method.getName() + " must define its unified query");
            String sql = normalized(select);
            assertTrue(sql.contains("memory_entries"), method.getName());
            assertTrue(sql.contains("origin_type='DIARY'"), method.getName());
            assertFalse(sql.contains("diary_entries"), method.getName());
        }
        String visibleSql = sql(UnifiedDiaryRecordRepository.class, "findVisibleByFamily");
        assertTrue(visibleSql.contains("COALESCE(memory_entries.related_user_id,memory_entries.user_id)"));
    }

    @Test
    void growthCompatibilityQueriesReadOnlyUnifiedMemoryEntries() {
        for (Method method : UnifiedGrowthRecordRepository.class.getDeclaredMethods()) {
            Select select = method.getAnnotation(Select.class);
            assertTrue(select != null, method.getName() + " must define its unified query");
            String sql = normalized(select);
            assertTrue(sql.contains("memory_entries"), method.getName());
            assertTrue(sql.contains("origin_type='GROWTH'"), method.getName());
            assertFalse(sql.contains("growth_guard_records"), method.getName());
        }
    }

    private static String sql(Class<?> repositoryType, String methodName) {
        Method method = Arrays.stream(repositoryType.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        return normalized(method.getAnnotation(Select.class));
    }

    private static String normalized(Select select) {
        return String.join("\n", select.value()).replaceAll("\\s+", "");
    }
}
