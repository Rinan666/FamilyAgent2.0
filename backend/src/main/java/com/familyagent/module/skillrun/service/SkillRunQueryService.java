package com.familyagent.module.skillrun.service;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.module.family.facade.FamilyMembershipFacade;
import com.familyagent.module.skillrun.entity.SkillRun;
import com.familyagent.module.skillrun.repository.SkillRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SkillRunQueryService {

    private final SkillRunRepository skillRunRepository;
    private final FamilyMembershipFacade familyMembershipFacade;
    private final SkillRunInputPolicy inputPolicy;

    public List<SkillRun> listFamilyRuns(Long familyId, int limit) {
        familyMembershipFacade.checkMembership(familyId);
        return skillRunRepository.findByFamily(familyId, inputPolicy.normalizeLimit(limit));
    }

    public SkillRun get(Long id) {
        SkillRun run = skillRunRepository.selectById(id);
        if (run == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "技能运行记录不存在");
        }
        familyMembershipFacade.checkMembership(run.getFamilyId());
        return run;
    }
}
