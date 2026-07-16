package com.familyagent.module.skillrun.facade;

import com.familyagent.module.skillrun.dto.CreateSkillRunRequest;
import com.familyagent.module.skillrun.dto.UpdateSkillRunRequest;
import com.familyagent.module.skillrun.entity.SkillRun;
import com.familyagent.module.skillrun.service.SkillRunCommandService;
import com.familyagent.module.skillrun.service.SkillRunLifecycleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentSkillRunFacade {

    private final SkillRunCommandService commandService;
    private final SkillRunLifecycleService lifecycleService;

    public SkillRun create(CreateSkillRunRequest request) {
        return commandService.create(request);
    }

    public SkillRun update(Long id, UpdateSkillRunRequest request) {
        return lifecycleService.update(id, request);
    }
}
