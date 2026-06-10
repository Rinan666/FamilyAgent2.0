package com.familyagent.module.memorylibrary.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.common.response.PageResult;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.diary.repository.DiaryEntryRepository;
import com.familyagent.module.family.service.FamilyService;
import com.familyagent.module.growth.dto.GrowthStalenessStats;
import com.familyagent.module.growth.entity.GrowthGuardRecord;
import com.familyagent.module.growth.entity.GrowthGuardReport;
import com.familyagent.module.growth.repository.GrowthGuardRecordRepository;
import com.familyagent.module.growth.repository.GrowthGuardReportRepository;
import com.familyagent.module.growth.repository.GrowthGuardStalenessVoteRepository;
import com.familyagent.module.growth.service.GrowthGuardService;
import com.familyagent.module.memory.dto.MemoryVoteStats;
import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.repository.MemoryEntryVoteRepository;
import com.familyagent.module.memory.service.MemoryService;
import com.familyagent.module.memory.service.MemoryEmbeddingService;
import com.familyagent.module.memory.service.MemoryIndexMetadataBuilder;
import com.familyagent.module.memory.repository.MemoryEntryRepository;
import com.familyagent.module.memory.dto.CreateFamilyMemoryRequest;
import com.familyagent.module.memorylibrary.dto.MemoryLibraryItem;
import com.familyagent.module.memorylibrary.dto.MemoryLibraryMaintenanceSuggestion;
import com.familyagent.module.memorylibrary.dto.MemoryLibrarySearchRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MemoryLibraryService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 12;
    private static final int MAX_PAGE_SIZE = 48;
    private static final Set<String> TYPES = Set.of(
            "ALL", "LIFE_RECORD", "FAMILY_EXPERIENCE", "GROWTH_OBSERVATION", "AI_SUMMARY");

    private final JdbcTemplate jdbcTemplate;
    private final FamilyService familyService;
    private final ObjectMapper objectMapper;
    private final DiaryEntryRepository diaryEntryRepository;
    private final MemoryEntryRepository memoryEntryRepository;
    private final MemoryEntryVoteRepository memoryEntryVoteRepository;
    private final MemoryService memoryService;
    private final MemoryEmbeddingService memoryEmbeddingService;
    private final GrowthGuardService growthGuardService;
    private final GrowthGuardRecordRepository growthRecordRepository;
    private final GrowthGuardReportRepository growthReportRepository;
    private final GrowthGuardStalenessVoteRepository growthGuardStalenessVoteRepository;

    public PageResult<MemoryLibraryItem> search(MemoryLibrarySearchRequest request) {
        return search(request, false);
    }

    public PageResult<MemoryLibraryItem> archived(MemoryLibrarySearchRequest request) {
        return search(request, true);
    }

    private PageResult<MemoryLibraryItem> search(MemoryLibrarySearchRequest request, boolean archived) {
        if (request.getFamilyId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "familyId 不能为空");
        }
        familyService.checkMembership(request.getFamilyId());

        Long viewerUserId = CurrentUserGuard.currentUserId();
        String type = normalizeType(request.getType());
        String keyword = blankToNull(request.getKeyword());
        String keywordLike = keyword == null ? null : "%" + keyword.toLowerCase(Locale.ROOT) + "%";
        Long memberUserId = request.getMemberUserId();
        String visibility = blankToNull(request.getVisibility());
        int page = normalizePage(request.getPage());
        int pageSize = normalizePageSize(request.getPageSize());
        int offset = (page - 1) * pageSize;

        Object[] args = concat(
                sectionArgs(request.getFamilyId(), viewerUserId, keywordLike, type, memberUserId, visibility),
                sectionArgs(request.getFamilyId(), viewerUserId, keywordLike, type, memberUserId, visibility),
                growthSectionArgs(request.getFamilyId(), viewerUserId, keywordLike, type, memberUserId, visibility),
                growthSectionArgs(request.getFamilyId(), viewerUserId, keywordLike, type, memberUserId, visibility));

        String query = baseQuery(archived);
        long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM (" + query + ") items", Long.class, args);
        List<MemoryLibraryItem> items = jdbcTemplate.query(
                "SELECT * FROM (" + query + ") items ORDER BY sort_time DESC, id DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> mapItem(rs),
                concat(args, pageSize, offset));
        items.forEach(item -> attachDynamicSignals(item, viewerUserId));
        return PageResult.of(items, page, pageSize, total);
    }

    public List<MemoryLibraryMaintenanceSuggestion> maintenanceSuggestions(MemoryLibrarySearchRequest request) {
        if (request.getFamilyId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "familyId 不能为空");
        }
        MemoryLibrarySearchRequest scan = new MemoryLibrarySearchRequest();
        scan.setFamilyId(request.getFamilyId());
        scan.setPage(1);
        scan.setPageSize(48);
        scan.setType("ALL");
        PageResult<MemoryLibraryItem> page = search(scan);
        List<MemoryLibraryItem> items = page.getItems() == null ? List.of() : page.getItems();

        List<MemoryLibraryMaintenanceSuggestion> suggestions = new ArrayList<>();
        suggestions.addAll(mergeSuggestions(items));
        for (MemoryLibraryItem item : items) {
            MaintenanceScore score = maintenanceScore(item);
            if (score.score() >= 70) {
                suggestions.add(MemoryLibraryMaintenanceSuggestion.builder()
                        .action("DELETE_REVIEW")
                        .score(score.score())
                        .title("疑似误保存，建议进入待清理箱")
                        .reason(String.join("；", score.reasons()))
                        .reasons(score.reasons())
                        .items(List.of(item))
                        .build());
            } else if (score.score() >= 45) {
                suggestions.add(MemoryLibraryMaintenanceSuggestion.builder()
                        .action("ARCHIVE_REVIEW")
                        .score(score.score())
                        .title("建议归档，降低默认展示和召回")
                        .reason(String.join("；", score.reasons()))
                        .reasons(score.reasons())
                        .items(List.of(item))
                        .build());
            }
        }
        return suggestions.stream()
                .sorted(Comparator.comparingInt(MemoryLibraryMaintenanceSuggestion::getScore).reversed())
                .limit(12)
                .toList();
    }

    @Transactional
    public void classicalizeLibraryItem(
            Long familyId,
            String itemId,
            String classicalText,
            String plainSummary,
            String styleNote) {
        if (familyId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "familyId 不能为空");
        }
        familyService.checkMembership(familyId);
        ParsedItemId parsed = parseItemId(itemId);
        if (!"memory".equals(parsed.prefix())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "当前仅支持对家族经验进行古文提炼");
        }

        String normalizedClassicalText = blankToNull(classicalText);
        if (normalizedClassicalText == null || normalizedClassicalText.length() < 8) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "古文稿过短，无法回写");
        }
        String normalizedPlainSummary = blankToNull(plainSummary);
        if (normalizedPlainSummary == null) {
            normalizedPlainSummary = previewText(normalizedClassicalText, 120);
        }
        String normalizedStyleNote = blankToNull(styleNote);
        if (normalizedStyleNote == null) {
            normalizedStyleNote = "MEMORY_LIBRARY_CLASSICALIZE";
        }

        MemoryEntry entry = requireActiveFamilyExperienceMemory(familyId, parsed.id());
        ensureCreatorOrFamilyOwner(familyId, entry.getUserId(), "只能改写自己创建的经验，或由家族创建者改写");

        Map<String, Object> metadata = mutableMap(entry.getMetadata());
        Map<String, Object> classicalization = mutableMap(metadata.get("classicalization"));
        classicalization.putIfAbsent("originalContent", entry.getContent());
        if (blankToNull(entry.getSummary()) != null) {
            classicalization.putIfAbsent("originalSummary", entry.getSummary());
        }
        classicalization.put("plainSummary", normalizedPlainSummary);
        classicalization.put("styleNote", normalizedStyleNote);
        classicalization.put("classicalizedAt", LocalDateTime.now().toString());
        classicalization.put("classicalizedBy", CurrentUserGuard.currentUserId());
        classicalization.put("source", "MEMORY_LIBRARY_CLASSICALIZE");
        metadata.put("classicalization", classicalization);

        entry.setContent(normalizedClassicalText.trim());
        entry.setSummary(truncateText(normalizedPlainSummary, 200));
        entry.setMetadata(MemoryIndexMetadataBuilder.enrichFamilyMemory(
                metadata,
                entry.getContent(),
                entry.getSummary(),
                entry.getType(),
                entry.getImportance() == null ? 3 : entry.getImportance()));
        memoryEntryRepository.updateById(entry);
        memoryEmbeddingService.indexMemoryAfterCommit(entry);
    }

    @Transactional
    public void mergeLibraryItems(Long familyId, String primaryItemId, String secondaryItemId) {
        if (familyId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "familyId 不能为空");
        }
        if (primaryItemId == null || secondaryItemId == null || primaryItemId.equals(secondaryItemId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择两条不同的经验记录再合并");
        }
        familyService.checkMembership(familyId);

        ParsedItemId primaryParsed = parseItemId(primaryItemId);
        ParsedItemId secondaryParsed = parseItemId(secondaryItemId);
        if (!"memory".equals(primaryParsed.prefix()) || !"memory".equals(secondaryParsed.prefix())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "当前仅支持合并家族经验类记忆");
        }

        MemoryEntry primary = requireActiveFamilyMemory(familyId, primaryParsed.id());
        MemoryEntry secondary = requireActiveFamilyMemory(familyId, secondaryParsed.id());
        ensureCreatorOrFamilyOwner(familyId, primary.getUserId(), "只能合并自己创建的经验，或由家族创建者合并");
        ensureCreatorOrFamilyOwner(familyId, secondary.getUserId(), "只能合并自己创建的经验，或由家族创建者合并");

        if (!primary.getType().equals(secondary.getType()) || !primary.getScope().equals(secondary.getScope())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅支持合并同类型、同可见范围的经验记录");
        }

        CreateFamilyMemoryRequest request = new CreateFamilyMemoryRequest();
        request.setFamilyId(familyId);
        request.setContent(secondary.getContent());
        request.setType(secondary.getType());
        request.setScope(secondary.getScope());
        request.setSummary(secondary.getSummary());
        request.setImportance(secondary.getImportance());
        request.setMemoryCard(memoryCardFromMetadata(secondary.getMetadata()));
        request.setMetadata(Map.of(
                "source", "MEMORY_LIBRARY_MERGE",
                "scenario", String.valueOf(mutableMap(secondary.getMetadata()).getOrDefault("scenario", ""))));

        Map<String, Object> incomingMetadata = mutableMap(secondary.getMetadata());
        incomingMetadata.put("source", "MEMORY_LIBRARY_MERGE");
        incomingMetadata.put("mergedItemId", "memory-" + secondary.getId());
        incomingMetadata.put("mergedItemPreview", previewText(secondary.getContent(), 80));

        memoryService.mergeFamilyMemory(primary, request, incomingMetadata, CurrentUserGuard.currentUserId());

        Map<String, Object> secondaryMetadata = mutableMap(secondary.getMetadata());
        secondaryMetadata.put("archivedBy", CurrentUserGuard.currentUserId());
        secondaryMetadata.put("archivedAt", LocalDateTime.now().toString());
        secondaryMetadata.put("archiveSource", "MEMORY_LIBRARY_MERGE");
        secondaryMetadata.put("mergedIntoItemId", "memory-" + primary.getId());
        secondaryMetadata.put("mergedIntoSummary", previewText(primary.getSummary(), 120));
        secondary.setMetadata(secondaryMetadata);
        secondary.setStatus("ARCHIVED");
        memoryEntryRepository.updateById(secondary);
    }

    public void archiveLibraryItem(Long familyId, String itemId) {
        if (familyId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "familyId 不能为空");
        }
        familyService.checkMembership(familyId);
        ParsedItemId parsed = parseItemId(itemId);
        switch (parsed.prefix()) {
            case "diary" -> archiveDiary(familyId, parsed.id());
            case "memory" -> archiveMemory(familyId, parsed.id());
            case "growth" -> archiveGrowthRecord(familyId, parsed.id());
            case "report" -> archiveGrowthReport(familyId, parsed.id());
            default -> throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的记忆类型");
        }
    }

    public void restoreLibraryItem(Long familyId, String itemId) {
        if (familyId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "familyId 不能为空");
        }
        familyService.checkMembership(familyId);
        ParsedItemId parsed = parseItemId(itemId);
        switch (parsed.prefix()) {
            case "diary" -> restoreDiary(familyId, parsed.id());
            case "memory" -> restoreMemory(familyId, parsed.id());
            case "growth" -> restoreGrowthRecord(familyId, parsed.id());
            case "report" -> restoreGrowthReport(familyId, parsed.id());
            default -> throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的记忆类型");
        }
    }

    public void deleteArchivedLibraryItem(Long familyId, String itemId) {
        if (familyId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "familyId 不能为空");
        }
        familyService.checkMembership(familyId);
        ParsedItemId parsed = parseItemId(itemId);
        switch (parsed.prefix()) {
            case "diary" -> deleteArchivedDiary(familyId, parsed.id());
            case "memory" -> deleteArchivedMemory(familyId, parsed.id());
            case "growth" -> deleteArchivedGrowthRecord(familyId, parsed.id());
            case "report" -> deleteArchivedGrowthReport(familyId, parsed.id());
            default -> throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的记忆类型");
        }
    }

    private static String baseQuery(boolean archived) {
        String diaryStatusCondition = archived
                ? "AND de.metadata->>'status' = 'ARCHIVED'"
                : "AND (de.metadata->>'status' IS NULL OR de.metadata->>'status' = 'ACTIVE')";
        String rowStatus = archived ? "ARCHIVED" : "ACTIVE";
        String template = """
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
              __DIARY_STATUS_CONDITION__
              AND (
                de.user_id = ?
                OR de.visibility IN ('FAMILY_VISIBLE', 'FAMILY')
                OR (
                  de.visibility IN ('CARE_VISIBLE', 'PARENT_VISIBLE')
                  AND EXISTS (
                    SELECT 1 FROM family_members fm
                    WHERE fm.family_id = de.family_id
                      AND fm.user_id = ?
                      AND fm.role = 'OWNER'
                  )
                )
                OR (
                  de.visibility IN ('CARE_VISIBLE', 'PARENT_VISIBLE')
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
              AND (CAST(? AS TEXT) IS NULL OR LOWER(CONCAT_WS(' ', de.raw_text, de.structured->>'title', de.structured->>'summary', de.visibility, array_to_string(de.tags, ' '), COALESCE(u.nickname, ''), COALESCE(u.username, ''))) LIKE CAST(? AS TEXT))
              AND (? = 'ALL' OR ? = 'LIFE_RECORD')
              AND (CAST(? AS BIGINT) IS NULL OR de.user_id = CAST(? AS BIGINT))
              AND (CAST(? AS TEXT) IS NULL OR de.visibility = CAST(? AS TEXT))

            UNION ALL

            SELECT
              CONCAT('memory-', me.id) AS id,
              CASE
                WHEN COALESCE(me.metadata->>'source', '') IN ('FAMILY_WEEKLY_DIGEST')
                  OR COALESCE(me.metadata->>'source', '') LIKE '%DIGEST%'
                  OR COALESCE(me.metadata->>'source', '') LIKE '%SUMMARY%'
                THEN 'AI_SUMMARY'
                ELSE 'FAMILY_EXPERIENCE'
              END AS source_type,
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
              AND me.status = '__ROW_STATUS__'
              AND me.type IN ('FAMILY_STORY', 'ELDER_ADVICE', 'HEALTH_REMINDER', 'GROWTH_RISK', 'VALUE', 'PLAN')
              AND (
                me.scope = 'FAMILY_VISIBLE'
                OR me.user_id = ?
                OR (
                  me.scope IN ('PARENT_VISIBLE', 'CARE_VISIBLE')
                  AND EXISTS (
                    SELECT 1 FROM family_members fm
                    WHERE fm.family_id = me.family_id
                      AND fm.user_id = ?
                      AND fm.role = 'OWNER'
                  )
                )
                OR (
                  me.scope IN ('PARENT_VISIBLE', 'CARE_VISIBLE')
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
              AND (CAST(? AS TEXT) IS NULL OR LOWER(CONCAT_WS(' ', me.content, me.summary, me.type, me.scope, COALESCE(me.metadata::text, ''), COALESCE(u.nickname, ''), COALESCE(u.username, ''))) LIKE CAST(? AS TEXT))
              AND (? = 'ALL' OR ? = CASE
                WHEN COALESCE(me.metadata->>'source', '') IN ('FAMILY_WEEKLY_DIGEST')
                  OR COALESCE(me.metadata->>'source', '') LIKE '%DIGEST%'
                  OR COALESCE(me.metadata->>'source', '') LIKE '%SUMMARY%'
                THEN 'AI_SUMMARY'
                ELSE 'FAMILY_EXPERIENCE'
              END)
              AND (CAST(? AS BIGINT) IS NULL OR me.user_id = CAST(? AS BIGINT))
              AND (CAST(? AS TEXT) IS NULL OR me.scope = CAST(? AS TEXT))

            UNION ALL

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
              AND gr.status = '__ROW_STATUS__'
              AND (
                gr.visibility = 'FAMILY_VISIBLE'
                OR gr.created_by = ?
                OR gr.target_user_id = ?
                OR (
                  gr.visibility IN ('PARENT_VISIBLE', 'CARE_VISIBLE')
                  AND EXISTS (
                    SELECT 1 FROM family_members fm
                    WHERE fm.family_id = gr.family_id
                      AND fm.user_id = ?
                      AND fm.role = 'OWNER'
                  )
                )
                OR (
                  gr.visibility IN ('PARENT_VISIBLE', 'CARE_VISIBLE')
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
              AND (CAST(? AS TEXT) IS NULL OR LOWER(CONCAT_WS(' ', gr.content, gr.category, gr.visibility, COALESCE(gr.metadata::text, ''), COALESCE(u.nickname, ''), COALESCE(u.username, ''))) LIKE CAST(? AS TEXT))
              AND (? = 'ALL' OR ? = 'GROWTH_OBSERVATION')
              AND (CAST(? AS BIGINT) IS NULL OR COALESCE(gr.target_user_id, gr.created_by) = CAST(? AS BIGINT))
              AND (CAST(? AS TEXT) IS NULL OR gr.visibility = CAST(? AS TEXT))

            UNION ALL

            SELECT
              CONCAT('report-', rp.id) AS id,
              'AI_SUMMARY' AS source_type,
              'GROWTH_GUARD_REPORT' AS type,
              COALESCE(NULLIF(rp.title, ''), '成长观察摘要') AS title,
              COALESCE(NULLIF(rp.summary, ''), rp.report->>'summary', '') AS body,
              rp.family_id,
              COALESCE(rp.target_user_id, rp.created_by) AS member_user_id,
              COALESCE(NULLIF(u.nickname, ''), NULLIF(u.username, ''), CONCAT('用户 ', COALESCE(rp.target_user_id, rp.created_by))) AS member_name,
              rp.visibility,
              ARRAY['成长观察摘要'] AS tags,
              rp.metadata,
              rp.created_at,
              rp.updated_at,
              COALESCE(rp.week_end::timestamp, rp.created_at) AS sort_time
            FROM growth_guard_reports rp
            LEFT JOIN users u ON u.id = COALESCE(rp.target_user_id, rp.created_by)
            WHERE rp.family_id = ?
              AND rp.status = '__ROW_STATUS__'
              AND (
                rp.visibility = 'FAMILY_VISIBLE'
                OR rp.created_by = ?
                OR rp.target_user_id = ?
                OR (
                  rp.visibility IN ('PARENT_VISIBLE', 'CARE_VISIBLE')
                  AND EXISTS (
                    SELECT 1 FROM family_members fm
                    WHERE fm.family_id = rp.family_id
                      AND fm.user_id = ?
                      AND fm.role = 'OWNER'
                  )
                )
                OR (
                  rp.visibility IN ('PARENT_VISIBLE', 'CARE_VISIBLE')
                  AND rp.target_user_id IS NOT NULL
                  AND EXISTS (
                    SELECT 1 FROM care_authorizations ca
                    WHERE ca.family_id = rp.family_id
                      AND ca.subject_user_id = rp.target_user_id
                      AND ca.caregiver_user_id = ?
                      AND ca.status = 'ACTIVE'
                      AND ca.scope IN ('ALL', 'GROWTH_GUARD')
                      AND (ca.expires_at IS NULL OR ca.expires_at > NOW())
                  )
                )
              )
              AND (CAST(? AS TEXT) IS NULL OR LOWER(CONCAT_WS(' ', rp.title, rp.summary, rp.visibility, COALESCE(rp.report::text, ''), COALESCE(rp.metadata::text, ''), COALESCE(u.nickname, ''), COALESCE(u.username, ''))) LIKE CAST(? AS TEXT))
              AND (? = 'ALL' OR ? = 'AI_SUMMARY')
              AND (CAST(? AS BIGINT) IS NULL OR COALESCE(rp.target_user_id, rp.created_by) = CAST(? AS BIGINT))
              AND (CAST(? AS TEXT) IS NULL OR rp.visibility = CAST(? AS TEXT))
            """;
        return template
                .replace("__DIARY_STATUS_CONDITION__", diaryStatusCondition)
                .replace("__ROW_STATUS__", rowStatus);
    }

    private MemoryLibraryItem mapItem(ResultSet rs) throws SQLException {
        return MemoryLibraryItem.builder()
                .id(rs.getString("id"))
                .sourceType(rs.getString("source_type"))
                .type(rs.getString("type"))
                .title(rs.getString("title"))
                .body(rs.getString("body"))
                .familyId(rs.getLong("family_id"))
                .memberUserId(rs.getLong("member_user_id"))
                .memberName(rs.getString("member_name"))
                .visibility(rs.getString("visibility"))
                .tags(readStringArray(rs.getArray("tags")))
                .metadata(readMap(rs.getObject("metadata")))
                .createdAt(readDateTime(rs, "created_at"))
                .updatedAt(readDateTime(rs, "updated_at"))
                .build();
    }

    private void attachDynamicSignals(MemoryLibraryItem item, Long viewerUserId) {
        if (item == null || item.getId() == null || item.getId().isBlank()) {
            return;
        }
        ParsedItemId parsed = parseItemId(item.getId());
        Map<String, Object> metadata = mutableMap(item.getMetadata());
        if ("memory".equals(parsed.prefix()) && "FAMILY_EXPERIENCE".equals(item.getSourceType())) {
            MemoryVoteStats stats = memoryEntryVoteRepository.statsByMemoryId(parsed.id(), viewerUserId);
            if (stats == null) {
                stats = new MemoryVoteStats(parsed.id(), 0, 0, 0, 1.0, null);
            }
            metadata.put("voteStats", Map.of(
                    "memoryId", parsed.id(),
                    "upVotes", stats.getUpVotes(),
                    "downVotes", stats.getDownVotes(),
                    "voteScore", stats.getVoteScore(),
                    "consensusWeight", stats.getConsensusWeight(),
                    "myVote", stats.getMyVote() == null ? "" : stats.getMyVote()));
        }
        if ("growth".equals(parsed.prefix()) && "GROWTH_OBSERVATION".equals(item.getSourceType())) {
            GrowthStalenessStats stats = growthGuardStalenessVoteRepository.statsByRecordId(parsed.id(), viewerUserId);
            if (stats == null) {
                stats = new GrowthStalenessStats(parsed.id(), 0, 1.0, false);
            }
            metadata.put("stalenessStats", Map.of(
                    "recordId", parsed.id(),
                    "staleVotes", stats.getStaleVotes(),
                    "stalenessWeight", stats.getStalenessWeight(),
                    "myVoted", stats.isMyVoted()));
        }
        item.setMetadata(metadata);
    }

    private List<MemoryLibraryMaintenanceSuggestion> mergeSuggestions(List<MemoryLibraryItem> items) {
        List<MemoryLibraryMaintenanceSuggestion> suggestions = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            MemoryLibraryItem left = items.get(i);
            for (int j = i + 1; j < items.size(); j++) {
                MemoryLibraryItem right = items.get(j);
                if (!isMergeCandidate(left) || !isMergeCandidate(right)) {
                    continue;
                }
                if (!left.getSourceType().equals(right.getSourceType())) {
                    continue;
                }
                if (!safeEquals(left.getType(), right.getType()) || !safeEquals(left.getVisibility(), right.getVisibility())) {
                    continue;
                }
                int score = similarityScore(left, right);
                if (score >= 8) {
                    suggestions.add(MemoryLibraryMaintenanceSuggestion.builder()
                            .action("MERGE_REVIEW")
                            .score(Math.min(95, 50 + score * 5))
                            .title("内容相近，建议合并为一条更凝练的记忆")
                            .reason("标题、标签、正文关键词高度重合，可能在记录同一类问题。")
                            .reasons(List.of("同类型内容", "关键词重合", "可减少重复记忆"))
                            .items(List.of(left, right))
                            .build());
                    break;
                }
            }
        }
        return suggestions;
    }

    private MemoryEntry requireActiveFamilyMemory(Long familyId, Long memoryId) {
        MemoryEntry entry = memoryEntryRepository.selectById(memoryId);
        if (entry == null || !familyId.equals(entry.getFamilyId()) || !"ACTIVE".equals(entry.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (!Set.of("FAMILY_STORY", "ELDER_ADVICE", "HEALTH_REMINDER", "GROWTH_RISK", "VALUE", "PLAN").contains(entry.getType())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "当前仅支持合并经验沉淀类记忆");
        }
        return entry;
    }

    private MemoryEntry requireActiveFamilyExperienceMemory(Long familyId, Long memoryId) {
        MemoryEntry entry = requireActiveFamilyMemory(familyId, memoryId);
        if (isAiSummaryMemory(entry)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "AI 摘要不能作为家族经验进行改写");
        }
        return entry;
    }

    private static boolean isMergeCandidate(MemoryLibraryItem item) {
        return item != null
                && item.getId() != null
                && item.getId().startsWith("memory-")
                && ("FAMILY_EXPERIENCE".equals(item.getSourceType()) || "AI_SUMMARY".equals(item.getSourceType()));
    }

    private static boolean isAiSummaryMemory(MemoryEntry entry) {
        Map<String, Object> metadata = mutableMap(entry.getMetadata());
        String source = asText(metadata.get("source")).toUpperCase(Locale.ROOT);
        return source.equals("FAMILY_WEEKLY_DIGEST")
                || source.contains("DIGEST")
                || source.contains("SUMMARY");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> memoryCardFromMetadata(Object metadata) {
        if (metadata instanceof Map<?, ?> map) {
            Object card = map.get("memoryCard");
            if (card instanceof Map<?, ?> cardMap) {
                return objectMap((Map<String, Object>) cardMap);
            }
        }
        return Map.of();
    }

    private static String previewText(String value, int maxLength) {
        String text = asText(value);
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, Math.max(0, maxLength - 1)).strip() + "…";
    }

    private static String truncateText(String value, int maxLength) {
        String text = asText(value);
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    private static MaintenanceScore maintenanceScore(MemoryLibraryItem item) {
        int score = 0;
        List<String> reasons = new ArrayList<>();
        Map<String, Object> metadata = item.getMetadata() == null ? Map.of() : item.getMetadata();
        String body = item.getBody() == null ? "" : item.getBody().trim();
        String sourceType = item.getSourceType();

        if (Boolean.TRUE.equals(metadata.get("coreMemory")) || "true".equalsIgnoreCase(String.valueOf(metadata.get("coreMemory")))) {
            return new MaintenanceScore(0, List.of("核心记忆不自动建议清理"));
        }
        if (body.length() < 12) {
            score += 35;
            reasons.add("正文过短，可能缺少长期价值");
        } else if (body.length() < 30) {
            score += 18;
            reasons.add("内容较短，建议补充背景或归档");
        }
        String source = asText(metadata.get("source"));
        if (source.contains("TOOL") && body.length() < 60) {
            score += 18;
            reasons.add("AI 自动保存且内容较短");
        }
        long ageDays = ageDays(item);
        if ("LIFE_RECORD".equals(sourceType) && ageDays > 180) {
            score += 22;
            reasons.add("较早的普通日记，默认可淡出展示");
        } else if ("LIFE_RECORD".equals(sourceType) && ageDays > 60) {
            score += 12;
            reasons.add("日记已过近期参考期");
        }
        if ("GROWTH_OBSERVATION".equals(sourceType)) {
            int staleVotes = nestedInt(metadata, "stalenessStats", "staleVotes");
            if (staleVotes > 0) {
                score += Math.min(35, 12 + staleVotes * 8);
                reasons.add(staleVotes + " 人认为这条观察可能过时");
            }
            String followUpStatus = asText(metadata.get("followUpStatus"));
            if ("IMPROVED".equalsIgnoreCase(followUpStatus) || "ARCHIVED".equalsIgnoreCase(followUpStatus)) {
                score += 18;
                reasons.add("跟进状态已结束");
            }
            if (ageDays > 120) {
                score += 16;
                reasons.add("观察记录时间较久，需要新证据复核");
            }
        }
        if ("FAMILY_EXPERIENCE".equals(sourceType)) {
            int voteScore = nestedInt(metadata, "voteStats", "voteScore");
            int downVotes = nestedInt(metadata, "voteStats", "downVotes");
            if (voteScore < 0 || downVotes >= 2) {
                score += Math.min(30, 10 + downVotes * 8);
                reasons.add("家族反馈偏谨慎，建议复核或归档");
            }
            if (metadata.get("memoryCard") == null && body.length() < 80) {
                score += 15;
                reasons.add("经验卡信息不完整");
            }
        }
        return new MaintenanceScore(Math.min(100, score), reasons.isEmpty() ? List.of("暂无明显整理风险") : reasons);
    }

    private static int similarityScore(MemoryLibraryItem left, MemoryLibraryItem right) {
        int score = 0;
        if (safeEquals(left.getType(), right.getType())) score += 2;
        if (safeEquals(left.getVisibility(), right.getVisibility())) score += 1;
        Set<String> leftSignals = signals(left);
        Set<String> rightSignals = signals(right);
        leftSignals.retainAll(rightSignals);
        score += Math.min(8, leftSignals.size());
        return score;
    }

    private static Set<String> signals(MemoryLibraryItem item) {
        Set<String> signals = new LinkedHashSet<>();
        addSignal(signals, item.getType());
        addSignal(signals, item.getTitle());
        if (item.getTags() != null) {
            for (String tag : item.getTags()) addSignal(signals, tag);
        }
        String text = normalizeText((item.getTitle() == null ? "" : item.getTitle()) + " " + (item.getBody() == null ? "" : item.getBody()));
        for (String keyword : List.of("牙", "视力", "体态", "睡眠", "运动", "屏幕", "情绪", "沟通", "选择", "工作", "规矩", "家风", "教训")) {
            if (text.contains(keyword)) signals.add(keyword);
        }
        for (String token : text.split("[^\\p{IsHan}\\p{Alnum}]+")) {
            if (token.length() >= 2 && token.length() <= 10) {
                signals.add(token);
            }
        }
        return signals;
    }

    private static void addSignal(Set<String> signals, String value) {
        String text = normalizeText(value);
        if (!text.isBlank()) signals.add(text);
    }

    private void archiveDiary(Long familyId, Long diaryId) {
        DiaryEntry entry = diaryEntryRepository.selectById(diaryId);
        if (entry == null || !familyId.equals(entry.getFamilyId()) || isArchivedMetadata(entry.getMetadata())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        ensureCreatorOrFamilyOwner(familyId, entry.getUserId(), "只能归档自己的日记，或由家族创建者归档");
        Map<String, Object> metadata = mutableMap(entry.getMetadata());
        metadata.put("status", "ARCHIVED");
        metadata.put("archivedBy", CurrentUserGuard.currentUserId());
        metadata.put("archivedAt", LocalDateTime.now().toString());
        metadata.put("archiveSource", "MEMORY_LIBRARY_MAINTENANCE");
        entry.setMetadata(metadata);
        diaryEntryRepository.updateById(entry);
    }

    private void restoreDiary(Long familyId, Long diaryId) {
        DiaryEntry entry = diaryEntryRepository.selectById(diaryId);
        if (entry == null || !familyId.equals(entry.getFamilyId()) || !isArchivedMetadata(entry.getMetadata())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        ensureCreatorOrFamilyOwner(familyId, entry.getUserId(), "只能恢复自己的日记，或由家族创建者恢复");
        Map<String, Object> metadata = mutableMap(entry.getMetadata());
        metadata.put("status", "ACTIVE");
        metadata.put("restoredBy", CurrentUserGuard.currentUserId());
        metadata.put("restoredAt", LocalDateTime.now().toString());
        metadata.put("restoreSource", "MEMORY_LIBRARY_ARCHIVE_BOX");
        entry.setMetadata(metadata);
        diaryEntryRepository.updateById(entry);
    }

    private void deleteArchivedDiary(Long familyId, Long diaryId) {
        DiaryEntry entry = diaryEntryRepository.selectById(diaryId);
        if (entry == null || !familyId.equals(entry.getFamilyId()) || !isArchivedMetadata(entry.getMetadata())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        ensureCreatorOrFamilyOwner(familyId, entry.getUserId(), "只能删除自己归档的日记，或由家族创建者删除");
        deleteEmbeddings("DIARY", diaryId);
        diaryEntryRepository.deleteById(diaryId);
    }

    private void archiveMemory(Long familyId, Long memoryId) {
        MemoryEntry entry = memoryEntryRepository.selectById(memoryId);
        if (entry == null || !familyId.equals(entry.getFamilyId()) || !"ACTIVE".equals(entry.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        ensureCreatorOrFamilyOwner(familyId, entry.getUserId(), "只能归档自己的经验，或由家族创建者归档");
        Map<String, Object> metadata = mutableMap(entry.getMetadata());
        metadata.put("archivedBy", CurrentUserGuard.currentUserId());
        metadata.put("archivedAt", LocalDateTime.now().toString());
        metadata.put("archiveSource", "MEMORY_LIBRARY_MAINTENANCE");
        entry.setMetadata(metadata);
        entry.setStatus("ARCHIVED");
        memoryEntryRepository.updateById(entry);
    }

    private void restoreMemory(Long familyId, Long memoryId) {
        MemoryEntry entry = memoryEntryRepository.selectById(memoryId);
        if (entry == null || !familyId.equals(entry.getFamilyId()) || !"ARCHIVED".equals(entry.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        ensureCreatorOrFamilyOwner(familyId, entry.getUserId(), "只能恢复自己的经验，或由家族创建者恢复");
        Map<String, Object> metadata = mutableMap(entry.getMetadata());
        metadata.put("restoredBy", CurrentUserGuard.currentUserId());
        metadata.put("restoredAt", LocalDateTime.now().toString());
        metadata.put("restoreSource", "MEMORY_LIBRARY_ARCHIVE_BOX");
        entry.setMetadata(metadata);
        entry.setStatus("ACTIVE");
        memoryEntryRepository.updateById(entry);
    }

    private void deleteArchivedMemory(Long familyId, Long memoryId) {
        MemoryEntry entry = memoryEntryRepository.selectById(memoryId);
        if (entry == null || !familyId.equals(entry.getFamilyId()) || !"ARCHIVED".equals(entry.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        ensureCreatorOrFamilyOwner(familyId, entry.getUserId(), "只能删除自己归档的经验，或由家族创建者删除");
        deleteEmbeddings("MEMORY", memoryId);
        memoryEntryRepository.deleteById(memoryId);
    }

    private void archiveGrowthRecord(Long familyId, Long recordId) {
        GrowthGuardRecord record = growthRecordRepository.selectById(recordId);
        if (record == null || !familyId.equals(record.getFamilyId()) || !"ACTIVE".equals(record.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        growthGuardService.archiveRecord(recordId);
    }

    private void restoreGrowthRecord(Long familyId, Long recordId) {
        GrowthGuardRecord record = growthRecordRepository.selectById(recordId);
        if (record == null || !familyId.equals(record.getFamilyId()) || !"ARCHIVED".equals(record.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        ensureCreatorOrFamilyOwner(familyId, record.getCreatedBy(), "只能恢复自己创建的观察，或由家族创建者恢复");
        record.setStatus("ACTIVE");
        growthRecordRepository.updateById(record);
    }

    private void deleteArchivedGrowthRecord(Long familyId, Long recordId) {
        GrowthGuardRecord record = growthRecordRepository.selectById(recordId);
        if (record == null || !familyId.equals(record.getFamilyId()) || !"ARCHIVED".equals(record.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        ensureCreatorOrFamilyOwner(familyId, record.getCreatedBy(), "只能删除自己归档的观察，或由家族创建者删除");
        deleteEmbeddings("GROWTH_OBSERVATION", recordId);
        growthRecordRepository.deleteById(recordId);
    }

    private void archiveGrowthReport(Long familyId, Long reportId) {
        GrowthGuardReport report = growthReportRepository.selectById(reportId);
        if (report == null || !"ACTIVE".equals(report.getStatus()) || !familyId.equals(report.getFamilyId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        Long viewerUserId = CurrentUserGuard.currentUserId();
        boolean selfCreated = viewerUserId.equals(report.getCreatedBy());
        boolean familyOwner = false;
        try {
            familyService.checkOwner(familyId);
            familyOwner = true;
        } catch (BusinessException ignored) {
            // Fall through to forbidden.
        }
        if (!selfCreated && !familyOwner) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能归档自己创建的摘要，或由家族创建者归档");
        }
        report.setStatus("ARCHIVED");
        growthReportRepository.updateById(report);
    }

    private void restoreGrowthReport(Long familyId, Long reportId) {
        GrowthGuardReport report = growthReportRepository.selectById(reportId);
        if (report == null || !"ARCHIVED".equals(report.getStatus()) || !familyId.equals(report.getFamilyId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        ensureCreatorOrFamilyOwner(familyId, report.getCreatedBy(), "只能恢复自己创建的摘要，或由家族创建者恢复");
        report.setStatus("ACTIVE");
        growthReportRepository.updateById(report);
    }

    private void deleteArchivedGrowthReport(Long familyId, Long reportId) {
        GrowthGuardReport report = growthReportRepository.selectById(reportId);
        if (report == null || !"ARCHIVED".equals(report.getStatus()) || !familyId.equals(report.getFamilyId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        ensureCreatorOrFamilyOwner(familyId, report.getCreatedBy(), "只能删除自己归档的摘要，或由家族创建者删除");
        growthReportRepository.deleteById(reportId);
    }

    private void deleteEmbeddings(String sourceType, Long sourceId) {
        jdbcTemplate.update(
                "DELETE FROM memory_embeddings WHERE source_type = ? AND source_id = ?",
                sourceType,
                sourceId);
    }

    private void ensureCreatorOrFamilyOwner(Long familyId, Long creatorUserId, String message) {
        Long viewerUserId = CurrentUserGuard.currentUserId();
        if (viewerUserId.equals(creatorUserId)) {
            return;
        }
        try {
            familyService.checkOwner(familyId);
            return;
        } catch (BusinessException ignored) {
            // Fall through to forbidden.
        }
        throw new BusinessException(ErrorCode.FORBIDDEN, message);
    }

    private static boolean isArchivedMetadata(Object metadata) {
        if (metadata instanceof Map<?, ?> map) {
            return "ARCHIVED".equalsIgnoreCase(String.valueOf(map.get("status")));
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mutableMap(Object metadata) {
        if (metadata instanceof Map<?, ?> map) {
            return objectMap((Map<String, Object>) map);
        }
        return new java.util.HashMap<>();
    }

    private static Map<String, Object> objectMap(Map<String, Object> map) {
        return new java.util.HashMap<>(map);
    }

    private static ParsedItemId parseItemId(String itemId) {
        if (itemId == null || itemId.isBlank() || !itemId.contains("-")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "记忆 ID 不合法");
        }
        String[] parts = itemId.split("-", 2);
        try {
            return new ParsedItemId(parts[0], Long.parseLong(parts[1]));
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "记忆 ID 不合法");
        }
    }

    private static long ageDays(MemoryLibraryItem item) {
        LocalDateTime reference = item.getUpdatedAt() == null ? item.getCreatedAt() : item.getUpdatedAt();
        if (reference == null) {
            return 0;
        }
        return Math.max(0, Duration.between(reference, LocalDateTime.now()).toDays());
    }

    private static int nestedInt(Map<String, Object> metadata, String objectKey, String valueKey) {
        Object nested = metadata.get(objectKey);
        if (!(nested instanceof Map<?, ?> map)) {
            return 0;
        }
        Object value = map.get(valueKey);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static String asText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static boolean safeEquals(String left, String right) {
        String a = normalizeText(left);
        String b = normalizeText(right);
        return !a.isBlank() && a.equals(b);
    }

    private static String normalizeText(String value) {
        return value == null
                ? ""
                : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "").trim();
    }

    private record MaintenanceScore(int score, List<String> reasons) {
    }

    private record ParsedItemId(String prefix, Long id) {
    }

    private static Object[] concat(Object[] args, Object... tail) {
        Object[] result = new Object[args.length + tail.length];
        System.arraycopy(args, 0, result, 0, args.length);
        System.arraycopy(tail, 0, result, args.length, tail.length);
        return result;
    }

    private static Object[] concat(Object[]... groups) {
        int length = 0;
        for (Object[] group : groups) {
            length += group.length;
        }
        Object[] result = new Object[length];
        int offset = 0;
        for (Object[] group : groups) {
            System.arraycopy(group, 0, result, offset, group.length);
            offset += group.length;
        }
        return result;
    }

    private static Object[] sectionArgs(
            Long familyId,
            Long viewerUserId,
            String keywordLike,
            String type,
            Long memberUserId,
            String visibility) {
        return new Object[] {
                familyId,
                viewerUserId,
                viewerUserId,
                viewerUserId,
                keywordLike,
                keywordLike,
                type,
                type,
                memberUserId,
                memberUserId,
                visibility,
                visibility
        };
    }

    private static Object[] growthSectionArgs(
            Long familyId,
            Long viewerUserId,
            String keywordLike,
            String type,
            Long memberUserId,
            String visibility) {
        return new Object[] {
                familyId,
                viewerUserId,
                viewerUserId,
                viewerUserId,
                viewerUserId,
                keywordLike,
                keywordLike,
                type,
                type,
                memberUserId,
                memberUserId,
                visibility,
                visibility
        };
    }

    private static int normalizePage(Integer page) {
        return page == null || page <= 0 ? DEFAULT_PAGE : page;
    }

    private static int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize <= 0) return DEFAULT_PAGE_SIZE;
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private static String normalizeType(String type) {
        String normalized = type == null || type.isBlank() ? "ALL" : type.trim().toUpperCase(Locale.ROOT);
        if (!TYPES.contains(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "记忆库类型不支持");
        }
        return normalized;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static LocalDateTime readDateTime(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private static String[] readStringArray(Array array) throws SQLException {
        if (array == null) return new String[0];
        Object raw = array.getArray();
        if (raw instanceof String[] values) return values;
        if (raw instanceof Object[] values) {
            String[] result = new String[values.length];
            for (int i = 0; i < values.length; i++) {
                result[i] = values[i] == null ? "" : String.valueOf(values[i]);
            }
            return result;
        }
        return new String[0];
    }

    private Map<String, Object> readMap(Object value) {
        if (value == null) return Collections.emptyMap();
        if (value instanceof Map<?, ?> map) {
            return objectMapper.convertValue(map, new TypeReference<>() {});
        }
        String json = value instanceof String text ? text : String.valueOf(value);
        if (json == null || json.isBlank()) return Collections.emptyMap();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ignored) {
            return Collections.emptyMap();
        }
    }
}
