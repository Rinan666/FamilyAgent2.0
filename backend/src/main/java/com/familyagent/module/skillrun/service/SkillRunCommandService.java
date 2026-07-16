package com.familyagent.module.skillrun.service;

import com.familyagent.common.constant.SkillRunStatus;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.family.facade.FamilyMembershipFacade;
import com.familyagent.module.skillrun.dto.CreateSkillRunRequest;
import com.familyagent.module.skillrun.dto.SkillRunMetadata;
import com.familyagent.module.skillrun.entity.SkillRun;
import com.familyagent.module.skillrun.repository.SkillRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SkillRunCommandService {

    private final SkillRunRepository skillRunRepository;
    private final FamilyMembershipFacade familyMembershipFacade;
    private final SkillRunInputPolicy inputPolicy;

    @Transactional
    public SkillRun create(CreateSkillRunRequest request) {
        familyMembershipFacade.checkMembership(request.getFamilyId());

        SkillRun run = new SkillRun();
        run.setFamilyId(request.getFamilyId());
        run.setTriggeredBy(CurrentUserGuard.currentUserId());
        run.setSkillName(inputPolicy.normalizeSkillName(request.getSkillName()));
        run.setStatus(inputPolicy.normalizeStatus(request.getStatus(), SkillRunStatus.PLANNED));
        run.setSource(inputPolicy.normalizeSource(request.getSource()));
        run.setInputSummary(inputPolicy.normalizeSummary(request.getInputSummary()));
        run.setOutputSummary(inputPolicy.normalizeSummary(request.getOutputSummary()));
        run.setSaved(Boolean.TRUE.equals(request.getSaved()));
        run.setUsedSources(request.getUsedSources() == null ? List.of() : request.getUsedSources());
        run.setMetadata(request.getMetadata() == null ? new SkillRunMetadata() : request.getMetadata());
        skillRunRepository.insert(run);
        return run;
    }
}
