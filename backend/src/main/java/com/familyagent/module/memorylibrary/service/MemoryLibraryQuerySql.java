package com.familyagent.module.memorylibrary.service;

/** SQL fragments for the unified memory-library query. Package-private. */
class MemoryLibraryQuerySql {

    private static final String MEMORY_SOURCE_TYPE_CASE = """
              CASE
                WHEN COALESCE(me.metadata->>'source', '') IN ('FAMILY_WEEKLY_DIGEST')
                  OR COALESCE(me.metadata->>'source', '') LIKE '%DIGEST%'
                  OR COALESCE(me.metadata->>'source', '') LIKE '%SUMMARY%'
                THEN 'AI_SUMMARY'
                ELSE 'FAMILY_EXPERIENCE'
              END
            """;

    static String diarySection(boolean archived) {
        String diaryStatus = archived
                ? "AND de.metadata->>'status' = 'ARCHIVED'"
                : "AND (de.metadata->>'status' IS NULL OR de.metadata->>'status' = 'ACTIVE')";
        return ("""
            SELECT
              CONCAT('diary-', de.id) AS id,
              'LIFE_RECORD' AS source_type,
              COALESCE(de.structured->>'entryType', 'DAILY') AS type,
              COALESCE(NULLIF(de.structured->>'title', ''), NULLIF(de.structured->>'summary', ''), LEFT(de.raw_text, 32), '未命名记录') AS title,
              de.raw_text AS body,
              de.family_id,
              de.user_id AS member_user_id,
              COALESCE(NULLIF(u.nickname, ''), NULLIF(u.username, ''), CONCAT('用户 ', de.user_id)) AS member_name,
              de.visibility,
              de.tags,
              de.metadata,
              de.created_at,
              de.updated_at,
              de.created_at AS sort_time
            FROM diary_entries de
            LEFT JOIN users u ON u.id = de.user_id
            WHERE de.family_id = ?
              {DIARY_STATUS}
              AND (
                de.user_id = ?
                OR de.visibility IN ('FAMILY_VISIBLE', 'FAMILY')
                OR (
                  de.visibility = 'CARE_VISIBLE'
                  AND EXISTS (
                    SELECT 1 FROM family_members fm
                    WHERE fm.family_id = de.family_id
                      AND fm.user_id = ?
                      AND fm.role = 'OWNER'
                  )
                )
                OR (
                  de.visibility = 'CARE_VISIBLE'
                  AND EXISTS (
                    SELECT 1 FROM care_authorizations ca
                    WHERE ca.family_id = de.family_id
                      AND ca.subject_user_id = de.user_id
                      AND ca.caregiver_user_id = ?
                      AND ca.status = 'ACTIVE'
                      AND ca.scope IN ('ALL', 'DIARY')
                      AND (ca.expires_at IS NULL OR ca.expires_at > NOW())
                  )
                )
              )
              AND (
                COALESCE(cardinality(CAST(? AS TEXT[])), 0) = 0
                OR EXISTS (
                  SELECT 1
                  FROM unnest(CAST(? AS TEXT[])) AS term
                  WHERE LOWER(CONCAT_WS(' ', de.raw_text, de.structured->>'title', de.structured->>'summary', de.visibility, array_to_string(de.tags, ' '), COALESCE(u.nickname, ''), COALESCE(u.username, '')))
                    LIKE CONCAT('%', term, '%')
                )
              )
              AND (? = 'ALL' OR ? = 'LIFE_RECORD')
              AND (CAST(? AS BIGINT) IS NULL OR de.user_id = CAST(? AS BIGINT))
              AND (CAST(? AS TEXT) IS NULL OR de.visibility = CAST(? AS TEXT))
              AND (CAST(? AS TEXT) IS NULL OR EXISTS (
                SELECT 1
                FROM unnest(COALESCE(de.tags, ARRAY[]::TEXT[])) AS tag
                WHERE LOWER(tag) = LOWER(CAST(? AS TEXT))
              ))
              AND (CAST(? AS DATE) IS NULL OR de.created_at::date >= CAST(? AS DATE))
              AND (CAST(? AS DATE) IS NULL OR de.created_at::date <= CAST(? AS DATE))
            """).replace("{DIARY_STATUS}", diaryStatus);
    }

    static String growthSection(boolean archived) {
        String rowStatus = archived ? "ARCHIVED" : "ACTIVE";
        return ("""
            SELECT
              CONCAT('growth-', gr.id) AS id,
              'GROWTH_OBSERVATION' AS source_type,
              gr.category AS type,
              CONCAT(CASE gr.category
                WHEN 'POSTURE' THEN '体态'
                WHEN 'DENTAL' THEN '牙齿'
                WHEN 'VISION' THEN '视力'
                WHEN 'SLEEP' THEN '睡眠'
                WHEN 'EXERCISE' THEN '运动'
                WHEN 'SCREEN_TIME' THEN '屏幕时间'
                WHEN 'EMOTION' THEN '情绪'
                WHEN 'COMMUNICATION' THEN '沟通'
                ELSE '其他'
              END, '观察') AS title,
              gr.content AS body,
              gr.family_id,
              COALESCE(gr.target_user_id, gr.created_by) AS member_user_id,
              COALESCE(NULLIF(u.nickname, ''), NULLIF(u.username, ''), CONCAT('用户 ', COALESCE(gr.target_user_id, gr.created_by))) AS member_name,
              gr.visibility,
              ARRAY_REMOVE(ARRAY[
                gr.category,
                gr.metadata->>'followUpStatus'
              ], NULL) AS tags,
              gr.metadata,
              gr.created_at,
              gr.updated_at,
              COALESCE(gr.observed_at::timestamp, gr.created_at) AS sort_time
            FROM growth_guard_records gr
            LEFT JOIN users u ON u.id = COALESCE(gr.target_user_id, gr.created_by)
            WHERE gr.family_id = ?
              AND gr.status = '{ROW_STATUS}'
              AND (
                gr.visibility = 'FAMILY_VISIBLE'
                OR gr.created_by = ?
                OR gr.target_user_id = ?
                OR (
                  gr.visibility = 'CARE_VISIBLE'
                  AND EXISTS (
                    SELECT 1 FROM family_members fm
                    WHERE fm.family_id = gr.family_id
                      AND fm.user_id = ?
                      AND fm.role = 'OWNER'
                  )
                )
                OR (
                  gr.visibility = 'CARE_VISIBLE'
                  AND gr.target_user_id IS NOT NULL
                  AND EXISTS (
                    SELECT 1 FROM care_authorizations ca
                    WHERE ca.family_id = gr.family_id
                      AND ca.subject_user_id = gr.target_user_id
                      AND ca.caregiver_user_id = ?
                      AND ca.status = 'ACTIVE'
                      AND ca.scope IN ('ALL', 'GROWTH_GUARD')
                      AND (ca.expires_at IS NULL OR ca.expires_at > NOW())
                  )
                )
              )
              AND (
                COALESCE(cardinality(CAST(? AS TEXT[])), 0) = 0
                OR EXISTS (
                  SELECT 1
                  FROM unnest(CAST(? AS TEXT[])) AS term
                  WHERE LOWER(CONCAT_WS(' ', gr.content, gr.category, gr.visibility, COALESCE(gr.metadata::text, ''), COALESCE(u.nickname, ''), COALESCE(u.username, '')))
                    LIKE CONCAT('%', term, '%')
                )
              )
              AND (? = 'ALL' OR ? = 'GROWTH_OBSERVATION')
              AND (CAST(? AS BIGINT) IS NULL OR COALESCE(gr.target_user_id, gr.created_by) = CAST(? AS BIGINT))
              AND (CAST(? AS TEXT) IS NULL OR gr.visibility = CAST(? AS TEXT))
              AND (CAST(? AS TEXT) IS NULL OR EXISTS (
                SELECT 1
                FROM unnest(ARRAY_REMOVE(ARRAY[
                  gr.category,
                  gr.metadata->>'followUpStatus'
                ], NULL)
                || COALESCE(ARRAY(
                  SELECT jsonb_array_elements_text(gr.metadata->'tags')
                ), ARRAY[]::TEXT[])) AS tag
                WHERE LOWER(tag) = LOWER(CAST(? AS TEXT))
              ))
              AND (CAST(? AS DATE) IS NULL OR COALESCE(gr.observed_at, gr.created_at::date) >= CAST(? AS DATE))
              AND (CAST(? AS DATE) IS NULL OR COALESCE(gr.observed_at, gr.created_at::date) <= CAST(? AS DATE))
            """).replace("{ROW_STATUS}", rowStatus);
    }

    static String fullQuery(boolean archived) {
        return diarySection(archived)
                + "\nUNION ALL\n"
                + memorySection(archived)
                + "\nUNION ALL\n"
                + growthSection(archived);
    }

    static String memorySection(boolean archived) {
        String rowStatus = archived ? "ARCHIVED" : "ACTIVE";
        return ("""
            SELECT
              CONCAT('memory-', me.id) AS id,
              {MEMORY_SOURCE_TYPE_CASE} AS source_type,
              me.type,
              COALESCE(NULLIF(me.summary, ''), LEFT(me.content, 32), '未命名经验') AS title,
              me.content AS body,
              me.family_id,
              me.user_id AS member_user_id,
              COALESCE(NULLIF(u.nickname, ''), NULLIF(u.username, ''), CONCAT('用户 ', me.user_id)) AS member_name,
              me.scope AS visibility,
              ARRAY_REMOVE(ARRAY[
                CASE WHEN COALESCE(me.metadata->>'coreMemory', '') = 'true' THEN '核心记忆' ELSE NULL END,
                me.type
              ], NULL) AS tags,
              me.metadata,
              me.created_at,
              me.updated_at,
              COALESCE(me.updated_at, me.created_at) AS sort_time
            FROM memory_entries me
            LEFT JOIN users u ON u.id = me.user_id
            WHERE me.family_id = ?
              AND me.status = '{ROW_STATUS}'
              AND me.type IN ('FAMILY_STORY', 'ELDER_ADVICE', 'HEALTH_REMINDER', 'GROWTH_RISK', 'VALUE', 'PLAN')
              AND (
                me.scope = 'FAMILY_VISIBLE'
                OR me.user_id = ?
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
                      AND ca.subject_user_id = me.user_id
                      AND ca.caregiver_user_id = ?
                      AND ca.status = 'ACTIVE'
                      AND ca.scope IN ('ALL', 'DIARY', 'GROWTH_GUARD')
                      AND (ca.expires_at IS NULL OR ca.expires_at > NOW())
                  )
                )
              )
              AND (
                COALESCE(cardinality(CAST(? AS TEXT[])), 0) = 0
                OR EXISTS (
                  SELECT 1
                  FROM unnest(CAST(? AS TEXT[])) AS term
                  WHERE LOWER(CONCAT_WS(' ', me.content, me.summary, me.type, me.scope, COALESCE(me.metadata::text, ''), COALESCE(u.nickname, ''), COALESCE(u.username, '')))
                    LIKE CONCAT('%', term, '%')
                )
              )
              AND (? = 'ALL' OR ? = {MEMORY_SOURCE_TYPE_CASE})
              AND (CAST(? AS BIGINT) IS NULL OR me.user_id = CAST(? AS BIGINT))
              AND (CAST(? AS TEXT) IS NULL OR me.scope = CAST(? AS TEXT))
              AND (CAST(? AS TEXT) IS NULL OR EXISTS (
                SELECT 1
                FROM unnest(ARRAY_REMOVE(ARRAY[
                  CASE WHEN COALESCE(me.metadata->>'coreMemory', '') = 'true' THEN '核心记忆' ELSE NULL END,
                  me.type,
                  me.metadata->>'scenario'
                ], NULL)
                || COALESCE(ARRAY(
                  SELECT jsonb_array_elements_text(me.metadata->'tags')
                ), ARRAY[]::TEXT[])) AS tag
                WHERE LOWER(tag) = LOWER(CAST(? AS TEXT))
              ))
              AND (CAST(? AS DATE) IS NULL OR COALESCE(me.updated_at::date, me.created_at::date) >= CAST(? AS DATE))
              AND (CAST(? AS DATE) IS NULL OR COALESCE(me.updated_at::date, me.created_at::date) <= CAST(? AS DATE))
            """)
                .replace("{MEMORY_SOURCE_TYPE_CASE}", MEMORY_SOURCE_TYPE_CASE)
                .replace("{ROW_STATUS}", rowStatus);
    }
}
