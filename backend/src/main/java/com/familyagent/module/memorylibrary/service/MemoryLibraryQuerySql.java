package com.familyagent.module.memorylibrary.service;

/** SQL for the unified memory library read model. Package-private. */
class MemoryLibraryQuerySql {

    private static final String SOURCE_TYPE_CASE = """
        CASE
          WHEN me.origin_type = 'DIARY' THEN 'LIFE_RECORD'
          WHEN me.origin_type = 'GROWTH' THEN 'GROWTH_OBSERVATION'
          WHEN COALESCE(me.metadata->>'source', '') IN ('FAMILY_WEEKLY_DIGEST')
            OR COALESCE(me.metadata->>'source', '') LIKE '%DIGEST%'
            OR COALESCE(me.metadata->>'source', '') LIKE '%SUMMARY%'
          THEN 'AI_SUMMARY'
          ELSE 'FAMILY_EXPERIENCE'
        END
        """;

    static String fullQuery(boolean archived) {
        String status = archived ? "ARCHIVED" : "ACTIVE";
        return ("""
            SELECT
              CASE me.origin_type
                WHEN 'DIARY' THEN CONCAT('diary-', me.origin_id)
                WHEN 'GROWTH' THEN CONCAT('growth-', me.origin_id)
                ELSE CONCAT('memory-', me.id)
              END AS id,
              {SOURCE_TYPE_CASE} AS source_type,
              me.type,
              COALESCE(NULLIF(me.title, ''), NULLIF(me.summary, ''), LEFT(me.content, 32), 'Untitled memory') AS title,
              me.content AS body,
              me.family_id,
              me.user_id AS author_user_id,
              COALESCE(me.related_user_id, me.user_id) AS member_user_id,
              COALESCE(NULLIF(member.nickname, ''), NULLIF(member.username, ''), CONCAT('User ', COALESCE(me.related_user_id, me.user_id))) AS member_name,
              me.scope AS visibility,
              COALESCE(me.tags, ARRAY[]::TEXT[]) AS tags,
              me.metadata,
              me.created_at,
              me.updated_at,
              COALESCE(me.occurred_at, me.updated_at, me.created_at) AS sort_time
            FROM memory_entries me
            LEFT JOIN users member ON member.id = COALESCE(me.related_user_id, me.user_id)
            WHERE me.family_id = ?
              AND me.library_kind = 'FAMILY'
              AND me.status = '{STATUS}'
              AND (
                me.user_id = ?
                OR me.scope = 'FAMILY_VISIBLE'
                OR (
                  me.scope = 'CARE_VISIBLE'
                  AND EXISTS (
                    SELECT 1 FROM family_members fm
                    WHERE fm.family_id = me.family_id
                      AND fm.user_id = ?
                      AND fm.role = 'OWNER'
                  )
                )
                OR (
                  me.scope = 'CARE_VISIBLE'
                  AND EXISTS (
                    SELECT 1 FROM care_authorizations ca
                    WHERE ca.family_id = me.family_id
                      AND ca.subject_user_id = COALESCE(me.related_user_id, me.user_id)
                      AND ca.caregiver_user_id = ?
                      AND ca.status = 'ACTIVE'
                      AND ca.scope IN ('ALL', 'MEMORY', 'DIARY', 'GROWTH_GUARD')
                      AND (ca.expires_at IS NULL OR ca.expires_at > NOW())
                  )
                )
              )
              AND (
                COALESCE(cardinality(CAST(? AS TEXT[])), 0) = 0
                OR EXISTS (
                  SELECT 1
                  FROM unnest(CAST(? AS TEXT[])) AS term
                  WHERE LOWER(CONCAT_WS(
                    ' ', me.content, me.title, me.summary, me.type, me.scope,
                    array_to_string(me.tags, ' '), COALESCE(member.nickname, ''), COALESCE(member.username, '')
                  )) LIKE CONCAT('%', term, '%')
                )
              )
              AND (? = 'ALL' OR ? = {SOURCE_TYPE_CASE})
              AND (CAST(? AS BIGINT) IS NULL OR COALESCE(me.related_user_id, me.user_id) = CAST(? AS BIGINT))
              AND (CAST(? AS TEXT) IS NULL OR me.scope = CAST(? AS TEXT))
              AND (CAST(? AS TEXT) IS NULL OR EXISTS (
                SELECT 1
                FROM unnest(COALESCE(me.tags, ARRAY[]::TEXT[])) AS tag
                WHERE LOWER(tag) = LOWER(CAST(? AS TEXT))
              ))
              AND (CAST(? AS DATE) IS NULL OR COALESCE(me.occurred_at::date, me.created_at::date) >= CAST(? AS DATE))
              AND (CAST(? AS DATE) IS NULL OR COALESCE(me.occurred_at::date, me.created_at::date) <= CAST(? AS DATE))
            """)
                .replace("{SOURCE_TYPE_CASE}", SOURCE_TYPE_CASE)
                .replace("{STATUS}", status);
    }
}
