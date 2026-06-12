package com.familyagent.module.growth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.familyagent.common.constant.EntityStatus;
import com.familyagent.common.constant.FollowUpStatus;
import com.familyagent.common.constant.MemoryScope;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.common.response.PageResult;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.family.entity.FamilyMember;
import com.familyagent.module.family.repository.FamilyMemberRepository;
import com.familyagent.module.family.service.CareAuthorizationService;
import com.familyagent.module.family.service.FamilyService;
import com.familyagent.module.growth.dto.CreateGrowthGuardRecordRequest;
import com.familyagent.module.growth.dto.CreateGrowthGuardReportRequest;
import com.familyagent.module.growth.dto.GrowthStalenessStats;
import com.familyagent.module.growth.entity.GrowthGuardRecord;
import com.familyagent.module.growth.entity.GrowthGuardReport;
import com.familyagent.module.growth.entity.GrowthGuardStalenessVote;
import com.familyagent.module.growth.repository.GrowthGuardRecordRepository;
import com.familyagent.module.growth.repository.GrowthGuardReportRepository;
import com.familyagent.module.growth.repository.GrowthGuardStalenessVoteRepository;
import com.familyagent.module.memory.service.MemoryEmbeddingService;
import com.familyagent.module.memory.service.MemoryIndexMetadataBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class GrowthGuardService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 60;
    private static final int DEFAULT_PAGE_SIZE = 6;
    private static final int MAX_PAGE_SIZE = 20;
    private static final Set<String> CATEGORIES = Set.of(
            "POSTURE", "DENTAL", "VISION", "SLEEP", "EXERCISE", "SCREEN_TIME", "EMOTION", "COMMUNICATION", "OTHER");
    private static final Set<String> VISIBILITIES = MemoryScope.familyNames();
    private static final Set<String> FOLLOW_UP_STATUSES = FollowUpStatus.names();

    private final GrowthGuardRecordRepository recordRepository;
    private final GrowthGuardReportRepository reportRepository;
    private final GrowthGuardStalenessVoteRepository stalenessVoteRepository;
    private final FamilyService familyService;
    private final FamilyMemberRepository memberRepository;
    private final CareAuthorizationService careAuthorizationService;
    private final MemoryEmbeddingService memoryEmbeddingService;

    @Transactional
    public GrowthGuardRecord createRecord(CreateGrowthGuardRecordRequest request) {
        Long viewerUserId = CurrentUserGuard.currentUserId();
        familyService.checkMembership(request.getFamilyId());

        Long targetUserId = request.getTargetUserId() == null ? viewerUserId : request.getTargetUserId();
        FamilyMember targetMember = memberRepository.findByFamilyAndUser(request.getFamilyId(), targetUserId);
        if (targetMember == null) {
            throw new BusinessException(ErrorCode.NOT_FAMILY_MEMBER, "记录对象不属于该家庭");
        }
        ensureCanCareForTarget(request.getFamilyId(), targetUserId, viewerUserId);

        GrowthGuardRecord record = new GrowthGuardRecord();
        record.setFamilyId(request.getFamilyId());
        record.setTargetUserId(targetUserId);
        record.setCreatedBy(viewerUserId);
        record.setCategory(normalizeCategory(request.getCategory()));
        record.setContent(request.getContent().trim());
        record.setSeverity(clamp(request.getSeverity() == null ? 3 : request.getSeverity(), 1, 5));
        record.setObservedAt(request.getObservedAt() == null ? LocalDate.now() : request.getObservedAt());
        record.setFollowUpAt(request.getFollowUpAt());
        record.setVisibility(normalizeVisibility(request.getVisibility()));
        record.setStatus(EntityStatus.ACTIVE.name());
        Map<String, Object> metadata = request.getMetadata() == null ? new HashMap<>() : new HashMap<>(request.getMetadata());
        metadata.putIfAbsent("followUpStatus", FollowUpStatus.PENDING.name());
        record.setMetadata(MemoryIndexMetadataBuilder.enrichGrowth(
                metadata,
                record.getContent(),
                record.getCategory(),
                record.getSeverity(),
                record.getObservedAt()));
        recordRepository.insert(record);
        memoryEmbeddingService.indexGrowthAfterCommit(record);
        return record;
    }

    public List<GrowthGuardRecord> listFamilyRecords(Long familyId, int limit) {
        familyService.checkMembership(familyId);
        Long viewerUserId = CurrentUserGuard.currentUserId();
        List<GrowthGuardRecord> records = recordRepository.findVisibleByFamily(familyId, viewerUserId, normalizeLimit(limit));
        records.forEach(record -> attachStalenessStats(record, viewerUserId));
        return records;
    }

    public PageResult<GrowthGuardRecord> searchFamilyRecords(Long familyId, Long targetUserId, String keyword, int page, int pageSize) {
        familyService.checkMembership(familyId);
        Long viewerUserId = CurrentUserGuard.currentUserId();
        int normalizedPageSize = normalizePageSize(pageSize);
        String normalizedKeyword = normalizeKeyword(keyword);
        long total = recordRepository.countVisibleByFamilySearch(familyId, viewerUserId, targetUserId, normalizedKeyword);
        long resolvedPage = resolvePage(page, normalizedPageSize, total);
        long offset = (resolvedPage - 1L) * normalizedPageSize;
        List<GrowthGuardRecord> items = total == 0
                ? List.of()
                : recordRepository.searchVisibleByFamily(
                        familyId,
                        viewerUserId,
                        targetUserId,
                        normalizedKeyword,
                        normalizedPageSize,
                        offset);
        items.forEach(record -> attachStalenessStats(record, viewerUserId));
        return PageResult.of(items, resolvedPage, normalizedPageSize, total);
    }

    @Transactional
    public GrowthGuardReport createReport(CreateGrowthGuardReportRequest request) {
        Long viewerUserId = CurrentUserGuard.currentUserId();
        familyService.checkMembership(request.getFamilyId());

        if (request.getTargetUserId() != null) {
            FamilyMember targetMember = memberRepository.findByFamilyAndUser(request.getFamilyId(), request.getTargetUserId());
            if (targetMember == null) {
                throw new BusinessException(ErrorCode.NOT_FAMILY_MEMBER, "报告对象不属于该家庭");
            }
            ensureCanCareForTarget(request.getFamilyId(), request.getTargetUserId(), viewerUserId);
        }

        LocalDate today = LocalDate.now();
        GrowthGuardReport report = new GrowthGuardReport();
        report.setFamilyId(request.getFamilyId());
        report.setTargetUserId(request.getTargetUserId());
        report.setCreatedBy(viewerUserId);
        report.setWeekStart(request.getWeekStart() == null ? today.minusDays(6) : request.getWeekStart());
        report.setWeekEnd(request.getWeekEnd() == null ? today : request.getWeekEnd());
        report.setTitle(request.getTitle().trim());
        report.setSummary(blankToNull(request.getSummary()));
        report.setVisibility(normalizeVisibility(request.getVisibility()));
        report.setStatus(EntityStatus.ACTIVE.name());
        report.setReport(new HashMap<>(request.getReport()));
        report.setMetadata(request.getMetadata() == null ? Map.of() : new HashMap<>(request.getMetadata()));
        reportRepository.insert(report);
        return report;
    }

    public List<GrowthGuardReport> listFamilyReports(Long familyId, int limit) {
        familyService.checkMembership(familyId);
        return reportRepository.findVisibleByFamily(familyId, CurrentUserGuard.currentUserId(), normalizeLimit(limit));
    }

    @Transactional
    public GrowthGuardRecord updateFollowUpStatus(Long id, String followUpStatus) {
        GrowthGuardRecord record = recordRepository.selectById(id);
        if (record == null || !EntityStatus.ACTIVE.name().equals(record.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        ensureCanModifyRecord(record);
        Map<String, Object> metadata = toMutableMap(record.getMetadata());
        metadata.put("followUpStatus", normalizeFollowUpStatus(followUpStatus));
        record.setMetadata(metadata);
        recordRepository.updateById(record);
        return record;
    }

    @Transactional
    public GrowthGuardRecord markRecordStale(Long id) {
        Long viewerUserId = CurrentUserGuard.currentUserId();
        GrowthGuardRecord record = recordRepository.selectById(id);
        if (record == null || !EntityStatus.ACTIVE.name().equals(record.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        familyService.checkMembership(record.getFamilyId());
        ensureCanViewRecord(record, viewerUserId);

        GrowthGuardStalenessVote existing = stalenessVoteRepository.selectOne(
                new LambdaQueryWrapper<GrowthGuardStalenessVote>()
                        .eq(GrowthGuardStalenessVote::getRecordId, id)
                        .eq(GrowthGuardStalenessVote::getUserId, viewerUserId)
                        .last("LIMIT 1"));
        if (existing == null) {
            insertStalenessVoteIfMissing(id, record.getFamilyId(), viewerUserId);
        }
        attachStalenessStats(record, viewerUserId);
        return record;
    }

    @Transactional
    public void archiveRecord(Long id) {
        GrowthGuardRecord record = recordRepository.selectById(id);
        if (record == null || !EntityStatus.ACTIVE.name().equals(record.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        Long viewerUserId = CurrentUserGuard.currentUserId();
        boolean selfCreated = viewerUserId.equals(record.getCreatedBy());
        boolean familyOwner = false;
        try {
            familyService.checkOwner(record.getFamilyId());
            familyOwner = true;
        } catch (BusinessException ignored) {
            // Fall through to creator-only permission.
        }
        if (!selfCreated && !familyOwner) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能删除自己创建的记录，或由家族创建者删除");
        }
        record.setStatus(EntityStatus.ARCHIVED.name());
        recordRepository.updateById(record);
    }

    private static int normalizeLimit(int limit) {
        if (limit <= 0) return DEFAULT_LIMIT;
        return Math.min(limit, MAX_LIMIT);
    }

    private static String normalizeKeyword(String keyword) {
        return keyword == null || keyword.isBlank() ? null : keyword.trim();
    }

    private static int normalizePageSize(int pageSize) {
        if (pageSize <= 0) return DEFAULT_PAGE_SIZE;
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private static long resolvePage(int page, int pageSize, long total) {
        long normalizedPage = Math.max(page, 1);
        if (total <= 0) {
            return normalizedPage;
        }
        long totalPages = (total + pageSize - 1L) / pageSize;
        return Math.min(normalizedPage, totalPages);
    }

    private static String normalizeCategory(String category) {
        String normalized = category == null ? "OTHER" : category.trim().toUpperCase(Locale.ROOT);
        if (!CATEGORIES.contains(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "成长记录类别不支持");
        }
        return normalized;
    }

    private static String normalizeVisibility(String visibility) {
        String normalized = visibility == null ? MemoryScope.DEFAULT_GROWTH.name() : visibility.trim().toUpperCase(Locale.ROOT);
        if (!VISIBILITIES.contains(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "可见范围不支持");
        }
        return normalized;
    }

    private static String normalizeFollowUpStatus(String followUpStatus) {
        String normalized = followUpStatus == null ? "" : followUpStatus.trim().toUpperCase(Locale.ROOT);
        if (!FOLLOW_UP_STATUSES.contains(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "跟进状态不支持");
        }
        return normalized;
    }

    private void ensureCanModifyRecord(GrowthGuardRecord record) {
        Long viewerUserId = CurrentUserGuard.currentUserId();
        if (viewerUserId.equals(record.getCreatedBy())) {
            return;
        }
        try {
            familyService.checkOwner(record.getFamilyId());
            return;
        } catch (BusinessException ignored) {
            // Fall through to forbidden.
        }
        throw new BusinessException(ErrorCode.FORBIDDEN, "只能更新自己创建的记录，或由家族创建者更新");
    }

    private void ensureCanViewRecord(GrowthGuardRecord record, Long viewerUserId) {
        if (viewerUserId.equals(record.getCreatedBy()) || viewerUserId.equals(record.getTargetUserId())
                || MemoryScope.FAMILY_VISIBLE.name().equalsIgnoreCase(record.getVisibility())) {
            return;
        }
        if (record.getTargetUserId() != null && careAuthorizationService.canViewCareScope(
                record.getFamilyId(),
                record.getTargetUserId(),
                viewerUserId,
                CareAuthorizationService.SCOPE_GROWTH_GUARD)) {
            return;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN, "无权标记该成长观察");
    }

    private void ensureCanCareForTarget(Long familyId, Long targetUserId, Long viewerUserId) {
        if (!careAuthorizationService.canViewCareScope(
                familyId,
                targetUserId,
                viewerUserId,
                CareAuthorizationService.SCOPE_GROWTH_GUARD)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "没有该成员的照护授权");
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toMutableMap(Object metadata) {
        if (metadata instanceof Map<?, ?> map) {
            return new HashMap<>((Map<String, Object>) map);
        }
        return new HashMap<>();
    }

    private void attachStalenessStats(GrowthGuardRecord record, Long viewerUserId) {
        GrowthStalenessStats stats = stalenessVoteRepository.statsByRecordId(record.getId(), viewerUserId);
        if (stats == null) {
            stats = new GrowthStalenessStats(record.getId(), 0, 1.0, false);
        }
        Map<String, Object> metadata = toMutableMap(record.getMetadata());
        metadata.put("stalenessStats", Map.of(
                "recordId", record.getId(),
                "staleVotes", stats.getStaleVotes(),
                "stalenessWeight", stats.getStalenessWeight(),
                "myVoted", stats.isMyVoted()));
        record.setMetadata(metadata);
    }

    private void insertStalenessVoteIfMissing(Long recordId, Long familyId, Long viewerUserId) {
        GrowthGuardStalenessVote vote = new GrowthGuardStalenessVote();
        vote.setRecordId(recordId);
        vote.setFamilyId(familyId);
        vote.setUserId(viewerUserId);
        try {
            stalenessVoteRepository.insert(vote);
        } catch (DataIntegrityViolationException ex) {
            GrowthGuardStalenessVote existing = stalenessVoteRepository.selectOne(
                    new LambdaQueryWrapper<GrowthGuardStalenessVote>()
                            .eq(GrowthGuardStalenessVote::getRecordId, recordId)
                            .eq(GrowthGuardStalenessVote::getUserId, viewerUserId)
                            .last("LIMIT 1"));
            if (existing == null) {
                throw ex;
            }
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
