package com.familyagent.module.skillrun.service;

import com.familyagent.module.skillrun.dto.CreateSkillRunRequest;
import com.familyagent.module.skillrun.dto.UpdateSkillRunRequest;
import com.familyagent.module.skillrun.entity.SkillRun;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SkillRunService {

    private final SkillRunCommandService commandService;
    private final SkillRunQueryService queryService;
    private final SkillRunLifecycleService lifecycleService;

    public SkillRun create(CreateSkillRunRequest request) {
        return commandService.create(request);
    }

    public List<SkillRun> listFamilyRuns(Long familyId, int limit) {
        return queryService.listFamilyRuns(familyId, limit);
    }

    public SkillRun get(Long id) {
        return queryService.get(id);
    }

    public SkillRun update(Long id, UpdateSkillRunRequest request) {
        return lifecycleService.update(id, request);
    }
}
