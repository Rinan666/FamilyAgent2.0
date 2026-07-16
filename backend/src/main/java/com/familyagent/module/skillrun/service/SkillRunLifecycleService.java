package com.familyagent.module.skillrun.service;

import com.familyagent.common.constant.SkillRunStatus;
import com.familyagent.module.skillrun.dto.UpdateSkillRunRequest;
import com.familyagent.module.skillrun.entity.SkillRun;
import com.familyagent.module.skillrun.repository.SkillRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SkillRunLifecycleService {

    private final SkillRunRepository skillRunRepository;
    private final SkillRunQueryService queryService;
    private final SkillRunInputPolicy inputPolicy;

    @Transactional
    public SkillRun update(Long id, UpdateSkillRunRequest request) {
        SkillRun run = queryService.get(id);
        if (request.getStatus() != null) {
            SkillRunStatus fallback = SkillRunStatus.valueOf(run.getStatus());
            run.setStatus(inputPolicy.normalizeStatus(request.getStatus(), fallback));
        }
        if (request.getOutputSummary() != null) {
            run.setOutputSummary(inputPolicy.normalizeSummary(request.getOutputSummary()));
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
}
