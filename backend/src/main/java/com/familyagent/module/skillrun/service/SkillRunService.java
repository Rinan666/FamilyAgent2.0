package com.familyagent.module.skillrun.service;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.family.service.FamilyService;
import com.familyagent.module.skillrun.dto.CreateSkillRunRequest;
import com.familyagent.module.skillrun.dto.UpdateSkillRunRequest;
import com.familyagent.module.skillrun.entity.SkillRun;
import com.familyagent.module.skillrun.repository.SkillRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SkillRunService {

    private static final int DEFAULT_LIMIT = 30;
    private static final int MAX_LIMIT = 100;
    private static final Set<String> VALID_STATUSES = Set.of(
            "PLANNED",
            "RUNNING",
            "SUCCEEDED",
            "FAILED",
            "CANCELED"
    );

    private final SkillRunRepository skillRunRepository;
    private final FamilyService familyService;

    @Transactional
    public SkillRun create(CreateSkillRunRequest request) {
        String skillName = normalizeSkillName(request.getSkillName());
        String status = normalizeStatus(request.getStatus(), "PLANNED");
        Long userId = CurrentUserGuard.currentUserId();
        familyService.checkMembership(request.getFamilyId());

        SkillRun run = new SkillRun();
        run.setFamilyId(request.getFamilyId());
        run.setTriggeredBy(userId);
        run.setSkillName(skillName);
        run.setStatus(status);
        run.setSource(blankToDefault(request.getSource(), "FAMILY_AGENT"));
        run.setInputSummary(trimToNull(request.getInputSummary(), 2000));
        run.setOutputSummary(trimToNull(request.getOutputSummary(), 2000));
        run.setSaved(Boolean.TRUE.equals(request.getSaved()));
        run.setUsedSources(request.getUsedSources() == null ? java.util.List.of() : request.getUsedSources());
        run.setMetadata(request.getMetadata() == null ? Map.of() : request.getMetadata());
        skillRunRepository.insert(run);
        return run;
    }

    public java.util.List<SkillRun> listFamilyRuns(Long familyId, int limit) {
        familyService.checkMembership(familyId);
        return skillRunRepository.findByFamily(familyId, normalizeLimit(limit));
    }

    public SkillRun get(Long id) {
        SkillRun run = requireRun(id);
        familyService.checkMembership(run.getFamilyId());
        return run;
    }

    @Transactional
    public SkillRun update(Long id, UpdateSkillRunRequest request) {
        SkillRun run = requireRun(id);
        familyService.checkMembership(run.getFamilyId());

        if (request.getStatus() != null) {
            run.setStatus(normalizeStatus(request.getStatus(), run.getStatus()));
        }
        if (request.getOutputSummary() != null) {
            run.setOutputSummary(trimToNull(request.getOutputSummary(), 2000));
        }
        if (request.getSaved() != null) {
            run.setSaved(request.getSaved());
        }
        if (request.getUsedSources() != null) {
            run.setUsedSources(request.getUsedSources());
        }
        if (request.getMetadata() != null) {
            run.setMetadata(request.getMetadata());
        }

        skillRunRepository.updateById(run);
        return run;
    }

    private SkillRun requireRun(Long id) {
        SkillRun run = skillRunRepository.selectById(id);
        if (run == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "技能运行记录不存在");
        }
        return run;
    }

    private static String normalizeSkillName(String value) {
        String name = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (name.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "技能名称不能为空");
        }
        if (!name.matches("[a-z0-9_\\-]{2,80}")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "技能名称格式不正确");
        }
        return name;
    }

    private static String normalizeStatus(String value, String fallback) {
        String status = value == null || value.isBlank()
                ? fallback
                : value.trim().toUpperCase(Locale.ROOT);
        if (!VALID_STATUSES.contains(status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的技能运行状态");
        }
        return status;
    }

    private static int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String trimToNull(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim();
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
