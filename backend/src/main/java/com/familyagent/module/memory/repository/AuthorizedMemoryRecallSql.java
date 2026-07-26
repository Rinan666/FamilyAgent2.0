package com.familyagent.module.memory.repository;

public final class AuthorizedMemoryRecallSql {

    private static final String FAMILY_AUTHORIZATION = """
          AND (
            me.user_id = #{viewerUserId}
            OR (me.origin_type = 'GROWTH' AND me.related_user_id = #{viewerUserId})
            OR me.scope = 'FAMILY_VISIBLE'
            OR (
              me.scope = 'CARE_VISIBLE'
              AND EXISTS (
                SELECT 1 FROM family_members fm
                WHERE fm.family_id = me.family_id
                  AND fm.user_id = #{viewerUserId}
                  AND fm.role = 'OWNER'
              )
            )
            OR (
              me.scope = 'CARE_VISIBLE'
              AND EXISTS (
                SELECT 1 FROM care_authorizations ca
                WHERE ca.family_id = me.family_id
                  AND ca.subject_user_id = COALESCE(me.related_user_id, me.user_id)
                  AND ca.caregiver_user_id = #{viewerUserId}
                  AND ca.status = 'ACTIVE'
                  AND ca.scope IN ('ALL', 'MEMORY', 'DIARY', 'GROWTH_GUARD')
                  AND (ca.expires_at IS NULL OR ca.expires_at > NOW())
              )
            )
          )
        """;

    private AuthorizedMemoryRecallSql() {
    }

    public static String visibleFamilyEntriesByOrigin() {
        return """
            SELECT me.*
            FROM memory_entries me
            WHERE me.family_id = #{familyId}
              AND me.library_kind = 'FAMILY'
              AND me.status = 'ACTIVE'
              AND me.origin_type = #{originType}
            """ + FAMILY_AUTHORIZATION + """
            ORDER BY me.importance DESC, me.occurred_at DESC, me.updated_at DESC
            LIMIT #{limit}
            """;
    }

    public static String visibleCanonicalMemories() {
        return """
            SELECT me.*
            FROM memory_entries me
            WHERE me.status = 'ACTIVE'
              AND (
                (
                  me.library_kind = 'FAMILY'
                  AND me.family_id = #{familyId}
                  AND me.origin_type IS NULL
            """ + FAMILY_AUTHORIZATION + """
                )
                OR (
                  me.library_kind = 'PERSONAL'
                  AND (
                    me.user_id = #{viewerUserId}
                    OR (
                      me.scope IN ('ALL_FAMILIES_VISIBLE', 'SELECTED_FAMILIES_VISIBLE')
                      AND EXISTS (
                        SELECT 1 FROM family_members owner_membership
                        WHERE owner_membership.family_id = #{familyId}
                          AND owner_membership.user_id = me.user_id
                      )
                      AND EXISTS (
                        SELECT 1 FROM personal_memory_family_grants pmfg
                        WHERE pmfg.memory_id = me.id
                          AND pmfg.family_id = #{familyId}
                      )
                    )
                    OR (
                      me.scope = 'CARE_VISIBLE'
                      AND EXISTS (
                        SELECT 1 FROM family_members owner_membership
                        WHERE owner_membership.family_id = #{familyId}
                          AND owner_membership.user_id = me.user_id
                      )
                      AND EXISTS (
                        SELECT 1 FROM care_authorizations ca
                        WHERE ca.family_id = #{familyId}
                          AND ca.subject_user_id = me.user_id
                          AND ca.caregiver_user_id = #{viewerUserId}
                          AND ca.status = 'ACTIVE'
                          AND ca.scope IN ('ALL', 'MEMORY')
                          AND (ca.expires_at IS NULL OR ca.expires_at > NOW())
                      )
                    )
                  )
                )
              )
            ORDER BY me.importance DESC, me.occurred_at DESC, me.updated_at DESC
            LIMIT #{limit}
            """;
    }

    public static String visibleMirrorSelfDiaries() {
        return mirrorDiaryQuery("me.user_id = #{targetUserId}");
    }

    public static String visibleMirrorRelatedDiaries() {
        return mirrorDiaryQuery("me.related_user_id = #{targetUserId} AND me.user_id <> #{targetUserId}");
    }

    private static String mirrorDiaryQuery(String targetPredicate) {
        return """
            SELECT me.*
            FROM memory_entries me
            WHERE me.family_id = #{familyId}
              AND me.library_kind = 'FAMILY'
              AND me.status = 'ACTIVE'
              AND me.origin_type = 'DIARY'
            """ + "  AND " + targetPredicate + "\n" + FAMILY_AUTHORIZATION + """
            ORDER BY me.occurred_at DESC, me.updated_at DESC
            LIMIT #{limit}
            """;
    }
}
