package com.familyagent.module.skillrun.service;

import com.familyagent.module.skillrun.dto.CreateSkillRunRequest;
import com.familyagent.module.skillrun.dto.UpdateSkillRunRequest;
import com.familyagent.module.skillrun.entity.SkillRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SkillRunServiceTest {

    @Mock private SkillRunCommandService commandService;
    @Mock private SkillRunQueryService queryService;
    @Mock private SkillRunLifecycleService lifecycleService;
    @InjectMocks private SkillRunService skillRunService;

    @Test
    void delegatesToResponsibilityServices() {
        CreateSkillRunRequest createRequest = new CreateSkillRunRequest();
        UpdateSkillRunRequest updateRequest = new UpdateSkillRunRequest();
        SkillRun run = new SkillRun();
        List<SkillRun> runs = List.of(run);
        when(commandService.create(createRequest)).thenReturn(run);
        when(queryService.listFamilyRuns(10L, 30)).thenReturn(runs);
        when(queryService.get(7L)).thenReturn(run);
        when(lifecycleService.update(7L, updateRequest)).thenReturn(run);

        assertSame(run, skillRunService.create(createRequest));
        assertSame(runs, skillRunService.listFamilyRuns(10L, 30));
        assertSame(run, skillRunService.get(7L));
        assertSame(run, skillRunService.update(7L, updateRequest));

        verify(commandService).create(createRequest);
        verify(queryService).listFamilyRuns(10L, 30);
        verify(queryService).get(7L);
        verify(lifecycleService).update(7L, updateRequest);
    }
}
