package com.familyagent.module.skillrun.service;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.module.skillrun.dto.SkillRunMetadata;
import com.familyagent.module.skillrun.dto.UpdateSkillRunRequest;
import com.familyagent.module.skillrun.entity.SkillRun;
import com.familyagent.module.skillrun.repository.SkillRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SkillRunLifecycleServiceTest {

    @Mock private SkillRunRepository skillRunRepository;
    @Mock private SkillRunQueryService queryService;

    private SkillRunLifecycleService lifecycleService;

    @BeforeEach
    void setUp() {
        lifecycleService = new SkillRunLifecycleService(
                skillRunRepository,
                queryService,
                new SkillRunInputPolicy());
    }

    @Test
    void updateAppliesLifecycleFields() {
        SkillRun run = new SkillRun();
        run.setId(7L);
        run.setStatus("RUNNING");
        run.setSaved(false);
        when(queryService.get(7L)).thenReturn(run);
        UpdateSkillRunRequest request = new UpdateSkillRunRequest();
        request.setStatus("succeeded");
        request.setSaved(true);
        request.setOutputSummary("保存为经验沉淀");
        SkillRunMetadata metadata = new SkillRunMetadata();
        metadata.setSavedRecordType("FAMILY_MEMORY");
        request.setMetadata(metadata);

        SkillRun result = lifecycleService.update(7L, request);

        verify(skillRunRepository).updateById(run);
        assertEquals(run, result);
        assertEquals("SUCCEEDED", run.getStatus());
        assertTrue(run.getSaved());
        assertEquals("保存为经验沉淀", run.getOutputSummary());
        assertEquals("FAMILY_MEMORY", run.getMetadata().getSavedRecordType());
    }

    @Test
    void updateRejectsInvalidStatus() {
        SkillRun run = new SkillRun();
        run.setStatus("RUNNING");
        when(queryService.get(7L)).thenReturn(run);
        UpdateSkillRunRequest request = new UpdateSkillRunRequest();
        request.setStatus("DONE");

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> lifecycleService.update(7L, request));

        assertEquals(ErrorCode.BAD_REQUEST.getCode(), error.getCode());
    }
}
