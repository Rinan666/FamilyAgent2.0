package com.familyagent.module.memory.repository;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthorizedMemoryRecallSqlTest {

    @Test
    void recallQueriesUseOnlyUnifiedMemoryEntriesAndServerSideAuthorization() {
        for (String sql : new String[] {
                AuthorizedMemoryRecallSql.visibleFamilyEntriesByOrigin(),
                AuthorizedMemoryRecallSql.visibleCanonicalMemories(),
                AuthorizedMemoryRecallSql.visibleMirrorSelfDiaries(),
                AuthorizedMemoryRecallSql.visibleMirrorRelatedDiaries()
        }) {
            String normalized = sql.replaceAll("\\s+", " ").toLowerCase();
            assertTrue(normalized.contains("from memory_entries me"));
            assertFalse(normalized.contains("from diary_entries"));
            assertFalse(normalized.contains("from growth_guard_records"));
            assertTrue(normalized.contains("care_authorizations"));
            assertTrue(normalized.contains("#{vieweruserid}"));
            assertFalse(normalized.contains("andme."));
        }
    }

    @Test
    void growthRecallAllowsTheObservedMemberToSeeTheirRecord() {
        String sql = AuthorizedMemoryRecallSql.visibleFamilyEntriesByOrigin()
                .replaceAll("\\s+", " ");
        assertTrue(sql.contains("me.origin_type = 'GROWTH' AND me.related_user_id = #{viewerUserId}"));
    }

    @Test
    void mirrorPredicatesRemainSeparatedFromTheAuthorizationClause() {
        String self = AuthorizedMemoryRecallSql.visibleMirrorSelfDiaries().replaceAll("\\s+", " ");
        String related = AuthorizedMemoryRecallSql.visibleMirrorRelatedDiaries().replaceAll("\\s+", " ");
        assertTrue(self.contains("AND me.user_id = #{targetUserId} AND ("));
        assertTrue(related.contains(
                "AND me.related_user_id = #{targetUserId} AND me.user_id <> #{targetUserId} AND ("));
    }
}
