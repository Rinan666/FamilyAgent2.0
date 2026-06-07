package com.familyagent.module.growth.service;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.family.entity.FamilyMember;
import com.familyagent.module.family.repository.FamilyMemberRepository;
import com.familyagent.module.family.service.CareAuthorizationService;
import com.familyagent.module.family.service.FamilyService;
import com.familyagent.module.growth.dto.CreateGrowthGuardRecordRequest;
import com.familyagent.module.growth.dto.CreateGrowthGuardReportRequest;
import com.familyagent.module.growth.entity.GrowthGuardRecord;
import com.familyagent.module.growth.entity.GrowthGuardReport;
import com.familyagent.module.growth.repository.GrowthGuardRecordRepository;
import com.familyagent.module.growth.repository.GrowthGuardReportRepository;
import lombok.RequiredArgsConstructor;
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
    private static final Set<String> CATEGORIES = Set.of(
            "POSTURE", "DENTAL", "VISION", "SLEEP", "EXERCISE", "SCREEN_TIME", "EMOTION", "COMMUNICATION", "OTHER");
    private static final Set<String> VISIBILITIES = Set.of("PRIVATE", "PARENT_VISIBLE", "CARE_VISIBLE", "FAMILY_VISIBLE");
    private static final Set<String> FOLLOW_UP_STATUSES = Set.of("PENDING", "WATCHING", "IMPROVED", "ARCHIVED");

    private final GrowthGuardRecordRepository recordRepository;
    private final GrowthGuardReportRepository reportRepository;
    private final FamilyService familyService;
    private final FamilyMemberRepository memberRepository;
    private final CareAuthorizationService careAuthorizationService;

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
        record.setStatus("ACTIVE");
        Map<String, Object> metadata = request.getMetadata() == null ? new HashMap<>() : new HashMap<>(request.getMetadata());
        metadata.putIfAbsent("followUpStatus", "PENDING");
        record.setMetadata(metadata);
        recordRepository.insert(record);
        return record;
    }

    public List<GrowthGuardRecord> listFamilyRecords(Long familyId, int limit) {
        familyService.checkMembership(familyId);
        return recordRepository.findVisibleByFamily(familyId, CurrentUserGuard.currentUserId(), normalizeLimit(limit));
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
        report.setStatus("ACTIVE");
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
        if (record == null || !"ACTIVE".equals(record.getStatus())) {
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
    public void archiveRecord(Long id) {
        GrowthGuardRecord record = recordRepository.selectById(id);
        if (record == null || !"ACTIVE".equals(record.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        Long viewerUserId = CurrentUserGuard.currentUserId();
        boolean selfCreated = viewerUserId.equals(record.getCreatedBy());
        boolean ownerOrAdmin = false;
        try {
            familyService.checkOwnerOrAdmin(record.getFamilyId());
            ownerOrAdmin = true;
        } catch (BusinessException ignored) {
            // Fall through to creator-only permission.
        }
        if (!selfCreated && !ownerOrAdmin) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能删除自己创建的记录，或由家庭管理员删除");
        }
        record.setStatus("ARCHIVED");
        recordRepository.updateById(record);
    }

    private static int normalizeLimit(int limit) {
        if (limit <= 0) return DEFAULT_LIMIT;
        return Math.min(limit, MAX_LIMIT);
    }

    private static String normalizeCategory(String category) {
        String normalized = category == null ? "OTHER" : category.trim().toUpperCase(Locale.ROOT);
        if (!CATEGORIES.contains(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "成长记录类别不支持");
        }
        return normalized;
    }

    private static String normalizeVisibility(String visibility) {
        String normalized = visibility == null ? "CARE_VISIBLE" : visibility.trim().toUpperCase(Locale.ROOT);
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
            familyService.checkOwnerOrAdmin(record.getFamilyId());
            return;
        } catch (BusinessException ignored) {
            // Fall through to forbidden.
        }
        throw new BusinessException(ErrorCode.FORBIDDEN, "只能更新自己创建的记录，或由家庭管理员更新");
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

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
