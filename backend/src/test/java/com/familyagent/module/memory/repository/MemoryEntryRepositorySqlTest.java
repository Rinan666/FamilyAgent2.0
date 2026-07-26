package com.familyagent.module.memory.repository;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryEntryRepositorySqlTest {

    @Test
    void familyQueriesAuthorizeAndFilterByRelatedMemberFirst() {
        for (String methodName : new String[] {
                "findActiveFamilyMemories",
                "countActiveFamilyMemoriesSearch",
                "searchActiveFamilyMemories",
                "findVisibleFamilyMemoryById"
        }) {
            String sql = selectSql(methodName).replaceAll("\\s+", "");
            assertTrue(sql.contains("COALESCE(related_user_id,user_id)")
                            || sql.contains("COALESCE(memory_entries.related_user_id,memory_entries.user_id)"),
                    methodName + " must resolve the related member before the author");
            assertTrue(sql.contains("'DIARY','GROWTH_GUARD'"),
                    methodName + " must retain legacy care-authorization compatibility");
        }
    }

    @Test
    void styleQueriesUseDisjointUnifiedMemorySources() {
        String diarySql = normalizedSql("findActiveDiaryEntriesByAuthorForStyle");
        String memorySql = normalizedSql("findActiveCanonicalEntriesByAuthorForStyle");
        String growthSql = normalizedSql("findActiveGrowthEntriesBySubjectForStyle");

        assertTrue(diarySql.contains("FROMmemory_entries"));
        assertTrue(diarySql.contains("origin_type='DIARY'"));
        assertTrue(diarySql.contains("user_id=#{userId}"));
        assertTrue(memorySql.contains("FROMmemory_entries"));
        assertTrue(memorySql.contains("origin_typeISNULL"));
        assertTrue(memorySql.contains("user_id=#{userId}"));
        assertTrue(growthSql.contains("FROMmemory_entries"));
        assertTrue(growthSql.contains("origin_type='GROWTH'"));
        assertTrue(growthSql.contains("COALESCE(related_user_id,user_id)=#{userId}"));
    }

    private static String normalizedSql(String methodName) {
        return selectSql(methodName).replaceAll("\\s+", "");
    }

    private static String selectSql(String methodName) {
        Method method = Arrays.stream(MemoryEntryRepository.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        Select select = method.getAnnotation(Select.class);
        return String.join("\n", select.value());
    }
}
