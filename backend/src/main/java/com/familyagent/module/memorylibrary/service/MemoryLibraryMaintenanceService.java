package com.familyagent.module.memorylibrary.service;

import com.familyagent.common.constant.EntityStatus;
import com.familyagent.common.constant.FollowUpStatus;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.diary.repository.DiaryEntryRepository;
import com.familyagent.module.family.service.FamilyService;
import com.familyagent.module.growth.entity.GrowthGuardRecord;
import com.familyagent.module.growth.repository.GrowthGuardRecordRepository;
import com.familyagent.module.growth.service.GrowthGuardService;
import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.repository.MemoryEntryRepository;
import com.familyagent.module.memorylibrary.dto.MemoryLibraryItem;
import com.familyagent.module.memorylibrary.dto.MemoryLibraryMaintenanceSuggestion;
import com.familyagent.module.memorylibrary.dto.MemoryLibrarySearchRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 记忆库维护建议、归档、恢复、删除，从 MemoryLibraryService 拆出。
 */
@Component
@RequiredArgsConstructor
public class MemoryLibraryMaintenanceService {

    private final FamilyService familyService;
    private final DiaryEntryRepository diaryEntryRepository;
    private final MemoryEntryRepository memoryEntryRepository;
    private final GrowthGuardRecordRepository growthRecordRepository;
    private final GrowthGuardService growthGuardService;
    private final MemoryLibraryQueryService queryService;
    private final JdbcTemplate jdbcTemplate;

    public List<MemoryLibraryMaintenanceSuggestion> maintenanceSuggestions(MemoryLibrarySearchRequest request) {
        if (request.getFamilyId() == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "familyId 不能为空");
        MemoryLibrarySearchRequest scan = new MemoryLibrarySearchRequest();
        scan.setFamilyId(request.getFamilyId());
        scan.setPage(1);
        scan.setPageSize(48);
        scan.setType("ALL");
        List<MemoryLibraryItem> items = queryService.search(scan).getItems();
        if (items == null) items = List.of();

        List<MemoryLibraryMaintenanceSuggestion> suggestions = new ArrayList<>(mergeSuggestions(items));
        for (MemoryLibraryItem item : items) {
            MaintenanceScore score = maintenanceScore(item);
            if (score.score() >= 70) {
                suggestions.add(MemoryLibraryMaintenanceSuggestion.builder()
                        .action("DELETE_REVIEW").score(score.score())
                        .title("疑似误保存，建议进入待清理箱")
                        .reason(String.join("；", score.reasons())).reasons(score.reasons())
                        .items(List.of(item)).build());
            } else if (score.score() >= 45) {
                suggestions.add(MemoryLibraryMaintenanceSuggestion.builder()
                        .action("ARCHIVE_REVIEW").score(score.score())
                        .title("建议归档，降低默认展示和召回")
                        .reason(String.join("；", score.reasons())).reasons(score.reasons())
                        .items(List.of(item)).build());
            }
        }
        return suggestions.stream()
                .sorted(Comparator.comparingInt(MemoryLibraryMaintenanceSuggestion::getScore).reversed())
                .limit(12).toList();
    }

    public void archiveItem(Long familyId, String itemId) {
        if (familyId == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "familyId 不能为空");
        familyService.checkMembership(familyId);
        MemoryLibrarySupport.ParsedItemId parsed = MemoryLibrarySupport.parseItemId(itemId);
        switch (parsed.prefix()) {
            case "diary"   -> archiveDiary(familyId, parsed.id());
            case "memory"  -> archiveMemory(familyId, parsed.id());
            case "growth"  -> archiveGrowthRecord(familyId, parsed.id());
            default        -> throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的记忆类型");
        }
    }

    public void restoreItem(Long familyId, String itemId) {
        if (familyId == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "familyId 不能为空");
        familyService.checkMembership(familyId);
        MemoryLibrarySupport.ParsedItemId parsed = MemoryLibrarySupport.parseItemId(itemId);
        switch (parsed.prefix()) {
            case "diary"   -> restoreDiary(familyId, parsed.id());
            case "memory"  -> restoreMemory(familyId, parsed.id());
            case "growth"  -> restoreGrowthRecord(familyId, parsed.id());
            default        -> throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的记忆类型");
        }
    }

    public void deleteArchivedItem(Long familyId, String itemId) {
        if (familyId == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "familyId 不能为空");
        familyService.checkMembership(familyId);
        MemoryLibrarySupport.ParsedItemId parsed = MemoryLibrarySupport.parseItemId(itemId);
        switch (parsed.prefix()) {
            case "diary"   -> deleteArchivedDiary(familyId, parsed.id());
            case "memory"  -> deleteArchivedMemory(familyId, parsed.id());
            case "growth"  -> deleteArchivedGrowthRecord(familyId, parsed.id());
            default        -> throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的记忆类型");
        }
    }

    private void archiveDiary(Long familyId, Long diaryId) {
        DiaryEntry entry = diaryEntryRepository.selectById(diaryId);
        if (entry == null || !familyId.equals(entry.getFamilyId()) || isArchivedMetadata(entry.getMetadata())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        MemoryLibrarySupport.ensureCreatorOrFamilyOwner(familyService, familyId, entry.getUserId(), "只能归档自己的日记，或由家族创建者归档");
        Map<String, Object> metadata = MemoryLibrarySupport.mutableMap(entry.getMetadata());
        metadata.put("status", EntityStatus.ARCHIVED.name());
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
        MemoryLibrarySupport.ensureCreatorOrFamilyOwner(familyService, familyId, entry.getUserId(), "只能恢复自己的日记，或由家族创建者恢复");
        Map<String, Object> metadata = MemoryLibrarySupport.mutableMap(entry.getMetadata());
        metadata.put("status", EntityStatus.ACTIVE.name());
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
        MemoryLibrarySupport.ensureCreatorOrFamilyOwner(familyService, familyId, entry.getUserId(), "只能删除自己归档的日记，或由家族创建者删除");
        deleteEmbeddings("DIARY", diaryId);
        diaryEntryRepository.deleteById(diaryId);
    }

    private void archiveMemory(Long familyId, Long memoryId) {
        MemoryEntry entry = memoryEntryRepository.selectById(memoryId);
        if (entry == null || !familyId.equals(entry.getFamilyId()) || !EntityStatus.ACTIVE.name().equals(entry.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        MemoryLibrarySupport.ensureCreatorOrFamilyOwner(familyService, familyId, entry.getUserId(), "只能归档自己的经验，或由家族创建者归档");
        Map<String, Object> metadata = MemoryLibrarySupport.mutableMap(entry.getMetadata());
        metadata.put("archivedBy", CurrentUserGuard.currentUserId());
        metadata.put("archivedAt", LocalDateTime.now().toString());
        metadata.put("archiveSource", "MEMORY_LIBRARY_MAINTENANCE");
        entry.setMetadata(metadata);
        entry.setStatus(EntityStatus.ARCHIVED.name());
        memoryEntryRepository.updateById(entry);
    }

    private void restoreMemory(Long familyId, Long memoryId) {
        MemoryEntry entry = memoryEntryRepository.selectById(memoryId);
        if (entry == null || !familyId.equals(entry.getFamilyId()) || !EntityStatus.ARCHIVED.name().equals(entry.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        MemoryLibrarySupport.ensureCreatorOrFamilyOwner(familyService, familyId, entry.getUserId(), "只能恢复自己的经验，或由家族创建者恢复");
        Map<String, Object> metadata = MemoryLibrarySupport.mutableMap(entry.getMetadata());
        metadata.put("restoredBy", CurrentUserGuard.currentUserId());
        metadata.put("restoredAt", LocalDateTime.now().toString());
        metadata.put("restoreSource", "MEMORY_LIBRARY_ARCHIVE_BOX");
        entry.setMetadata(metadata);
        entry.setStatus(EntityStatus.ACTIVE.name());
        memoryEntryRepository.updateById(entry);
    }

    private void deleteArchivedMemory(Long familyId, Long memoryId) {
        MemoryEntry entry = memoryEntryRepository.selectById(memoryId);
        if (entry == null || !familyId.equals(entry.getFamilyId()) || !EntityStatus.ARCHIVED.name().equals(entry.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        MemoryLibrarySupport.ensureCreatorOrFamilyOwner(familyService, familyId, entry.getUserId(), "只能删除自己归档的经验，或由家族创建者删除");
        deleteEmbeddings("MEMORY", memoryId);
        memoryEntryRepository.deleteById(memoryId);
    }

    private void archiveGrowthRecord(Long familyId, Long recordId) {
        GrowthGuardRecord record = growthRecordRepository.selectById(recordId);
        if (record == null || !familyId.equals(record.getFamilyId()) || !EntityStatus.ACTIVE.name().equals(record.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        growthGuardService.archiveRecord(recordId);
    }

    private void restoreGrowthRecord(Long familyId, Long recordId) {
        GrowthGuardRecord record = growthRecordRepository.selectById(recordId);
        if (record == null || !familyId.equals(record.getFamilyId()) || !EntityStatus.ARCHIVED.name().equals(record.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        MemoryLibrarySupport.ensureCreatorOrFamilyOwner(familyService, familyId, record.getCreatedBy(), "只能恢复自己创建的观察，或由家族创建者恢复");
        record.setStatus(EntityStatus.ACTIVE.name());
        growthRecordRepository.updateById(record);
    }

    private void deleteArchivedGrowthRecord(Long familyId, Long recordId) {
        GrowthGuardRecord record = growthRecordRepository.selectById(recordId);
        if (record == null || !familyId.equals(record.getFamilyId()) || !EntityStatus.ARCHIVED.name().equals(record.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        MemoryLibrarySupport.ensureCreatorOrFamilyOwner(familyService, familyId, record.getCreatedBy(), "只能删除自己归档的观察，或由家族创建者删除");
        deleteEmbeddings("GROWTH_OBSERVATION", recordId);
        growthRecordRepository.deleteById(recordId);
    }

    private void deleteEmbeddings(String sourceType, Long sourceId) {
        jdbcTemplate.update("DELETE FROM memory_embeddings WHERE source_type = ? AND source_id = ?", sourceType, sourceId);
    }

    private static boolean isArchivedMetadata(Object metadata) {
        if (metadata instanceof Map<?, ?> map) {
            return EntityStatus.ARCHIVED.name().equalsIgnoreCase(String.valueOf(map.get("status")));
        }
        return false;
    }

    private static MaintenanceScore maintenanceScore(MemoryLibraryItem item) {
        int score = 0;
        List<String> reasons = new ArrayList<>();
        Map<String, Object> metadata = item.getMetadata() == null ? Map.of() : item.getMetadata();
        String body = item.getBody() == null ? "" : item.getBody().trim();
        String sourceType = item.getSourceType();

        if (Boolean.TRUE.equals(metadata.get("coreMemory"))
                || "true".equalsIgnoreCase(String.valueOf(metadata.get("coreMemory")))) {
            return new MaintenanceScore(0, List.of("核心记忆不自动建议清理"));
        }
        if (body.length() < 12) { score += 35; reasons.add("正文过短，可能缺少长期价值"); }
        else if (body.length() < 30) { score += 18; reasons.add("内容较短，建议补充背景或归档"); }

        String source = MemoryLibrarySupport.asText(metadata.get("source"));
        if (source.contains("TOOL") && body.length() < 60) { score += 18; reasons.add("AI 自动保存且内容较短"); }

        long ageDays = ageDays(item);
        if ("LIFE_RECORD".equals(sourceType) && ageDays > 180) { score += 22; reasons.add("较早的普通日记，默认可淡出展示"); }
        else if ("LIFE_RECORD".equals(sourceType) && ageDays > 60) { score += 12; reasons.add("日记已过近期参考期"); }

        if ("GROWTH_OBSERVATION".equals(sourceType)) {
            int staleVotes = nestedInt(metadata, "stalenessStats", "staleVotes");
            if (staleVotes > 0) { score += Math.min(35, 12 + staleVotes * 8); reasons.add(staleVotes + " 人认为这条观察可能过时"); }
            String followUpStatus = MemoryLibrarySupport.asText(metadata.get("followUpStatus"));
            if (FollowUpStatus.IMPROVED.name().equalsIgnoreCase(followUpStatus)
                    || FollowUpStatus.ARCHIVED.name().equalsIgnoreCase(followUpStatus)) {
                score += 18; reasons.add("跟进状态已结束");
            }
            if (ageDays > 120) { score += 16; reasons.add("观察记录时间较久，需要新证据复核"); }
        }
        if ("FAMILY_EXPERIENCE".equals(sourceType)) {
            int voteScore = nestedInt(metadata, "voteStats", "voteScore");
            int downVotes = nestedInt(metadata, "voteStats", "downVotes");
            if (voteScore < 0 || downVotes >= 2) { score += Math.min(30, 10 + downVotes * 8); reasons.add("家族反馈偏谨慎，建议复核或归档"); }
            if (metadata.get("memoryCard") == null && body.length() < 80) { score += 15; reasons.add("经验卡信息不完整"); }
        }
        return new MaintenanceScore(Math.min(100, score), reasons.isEmpty() ? List.of("暂无明显整理风险") : reasons);
    }

    private static List<MemoryLibraryMaintenanceSuggestion> mergeSuggestions(List<MemoryLibraryItem> items) {
        List<MemoryLibraryMaintenanceSuggestion> suggestions = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            MemoryLibraryItem left = items.get(i);
            for (int j = i + 1; j < items.size(); j++) {
                MemoryLibraryItem right = items.get(j);
                if (!isMergeCandidate(left) || !isMergeCandidate(right)) continue;
                if (!left.getSourceType().equals(right.getSourceType())) continue;
                if (!safeEquals(left.getType(), right.getType()) || !safeEquals(left.getVisibility(), right.getVisibility())) continue;
                int score = librarySimilarityScore(left, right);
                if (score >= 8) {
                    suggestions.add(MemoryLibraryMaintenanceSuggestion.builder()
                            .action("MERGE_REVIEW").score(Math.min(95, 50 + score * 5))
                            .title("内容相近，建议合并为一条更凝练的记忆")
                            .reason("标题、标签、正文关键词高度重合，可能在记录同一类问题。")
                            .reasons(List.of("同类型内容", "关键词重合", "可减少重复记忆"))
                            .items(List.of(left, right)).build());
                    break;
                }
            }
        }
        return suggestions;
    }

    private static boolean isMergeCandidate(MemoryLibraryItem item) {
        return item != null && item.getId() != null && item.getId().startsWith("memory-")
                && ("FAMILY_EXPERIENCE".equals(item.getSourceType()) || "AI_SUMMARY".equals(item.getSourceType()));
    }

    private static int librarySimilarityScore(MemoryLibraryItem left, MemoryLibraryItem right) {
        int score = 0;
        if (safeEquals(left.getType(), right.getType())) score += 2;
        if (safeEquals(left.getVisibility(), right.getVisibility())) score += 1;
        Set<String> leftSignals = itemSignals(left);
        Set<String> rightSignals = itemSignals(right);
        leftSignals.retainAll(rightSignals);
        score += Math.min(8, leftSignals.size());
        return score;
    }

    private static Set<String> itemSignals(MemoryLibraryItem item) {
        Set<String> signals = new LinkedHashSet<>();
        addSignal(signals, item.getType());
        addSignal(signals, item.getTitle());
        if (item.getTags() != null) { for (String tag : item.getTags()) addSignal(signals, tag); }
        String text = MemoryLibrarySupport.normalizeText(
                (item.getTitle() == null ? "" : item.getTitle()) + " " + (item.getBody() == null ? "" : item.getBody()));
        for (String kw : List.of("牙", "视力", "体态", "睡眠", "运动", "屏幕", "情绪", "沟通", "选择", "工作", "规矩", "家风", "教训")) {
            if (text.contains(kw)) signals.add(kw);
        }
        for (String token : text.split("[^\\p{IsHan}\\p{Alnum}]+")) {
            if (token.length() >= 2 && token.length() <= 10) signals.add(token);
        }
        return signals;
    }

    private static void addSignal(Set<String> signals, String value) {
        String text = MemoryLibrarySupport.normalizeText(value);
        if (!text.isBlank()) signals.add(text);
    }

    private static boolean safeEquals(String left, String right) {
        String a = MemoryLibrarySupport.normalizeText(left);
        String b = MemoryLibrarySupport.normalizeText(right);
        return !a.isBlank() && a.equals(b);
    }

    private static long ageDays(MemoryLibraryItem item) {
        LocalDateTime reference = item.getUpdatedAt() == null ? item.getCreatedAt() : item.getUpdatedAt();
        if (reference == null) return 0;
        return Math.max(0, Duration.between(reference, LocalDateTime.now()).toDays());
    }

    private static int nestedInt(Map<String, Object> metadata, String objectKey, String valueKey) {
        Object nested = metadata.get(objectKey);
        if (!(nested instanceof Map<?, ?> map)) return 0;
        Object value = map.get(valueKey);
        if (value instanceof Number number) return number.intValue();
        try { return Integer.parseInt(String.valueOf(value)); } catch (Exception ignored) { return 0; }
    }

    private record MaintenanceScore(int score, List<String> reasons) {}
}
