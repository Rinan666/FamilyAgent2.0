package com.familyagent.module.skillrun.service;

import cn.dev33.satoken.stp.StpUtil;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.module.family.service.FamilyService;
import com.familyagent.module.skillrun.dto.CreateSkillRunRequest;
import com.familyagent.module.skillrun.dto.UpdateSkillRunRequest;
import com.familyagent.module.skillrun.entity.SkillRun;
import com.familyagent.module.skillrun.repository.SkillRunRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SkillRunServiceTest {

    @Mock private SkillRunRepository skillRunRepository;
    @Mock private FamilyService familyService;
    @InjectMocks private SkillRunService skillRunService;

    @Test
    void createChecksMembershipAndStoresAuditFields() {
        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(101L);

            CreateSkillRunRequest request = new CreateSkillRunRequest();
            request.setFamilyId(10L);
            request.setSkillName("Save_Memory");
            request.setInputSummary("用户要求保存一条家庭经验");
            request.setUsedSources(List.of(Map.of("sourceType", "CHAT", "sourceId", 1)));
            request.setMetadata(Map.of("confirmationPolicy", "USER_CONFIRMATION"));

            SkillRun result = skillRunService.create(request);

            verify(familyService).checkMembership(10L);
            ArgumentCaptor<SkillRun> captor = ArgumentCaptor.forClass(SkillRun.class);
            verify(skillRunRepository).insert(captor.capture());
            SkillRun inserted = captor.getValue();
            assertEquals(result, inserted);
            assertEquals(10L, inserted.getFamilyId());
            assertEquals(101L, inserted.getTriggeredBy());
            assertEquals("save_memory", inserted.getSkillName());
            assertEquals("PLANNED", inserted.getStatus());
            assertEquals("FAMILY_AGENT", inserted.getSource());
            assertFalse(inserted.getSaved());
            assertEquals("用户要求保存一条家庭经验", inserted.getInputSummary());
        }
    }

    @Test
    void listFamilyRunsChecksMembershipAndNormalizesLimit() {
        when(skillRunRepository.findByFamily(10L, 100)).thenReturn(List.of(new SkillRun()));

        List<SkillRun> result = skillRunService.listFamilyRuns(10L, 500);

        verify(familyService).checkMembership(10L);
        verify(skillRunRepository).findByFamily(10L, 100);
        assertEquals(1, result.size());
    }

    @Test
    void updateChecksMembershipAndAppliesAllowedFields() {
        SkillRun run = new SkillRun();
        run.setId(7L);
        run.setFamilyId(10L);
        run.setStatus("RUNNING");
        run.setSaved(false);
        when(skillRunRepository.selectById(7L)).thenReturn(run);

        UpdateSkillRunRequest request = new UpdateSkillRunRequest();
        request.setStatus("succeeded");
        request.setSaved(true);
        request.setOutputSummary("保存为经验沉淀");
        request.setMetadata(Map.of("memoryId", 88));

        SkillRun result = skillRunService.update(7L, request);

        verify(familyService).checkMembership(10L);
        verify(skillRunRepository).updateById(run);
        assertEquals(result, run);
        assertEquals("SUCCEEDED", run.getStatus());
        assertTrue(run.getSaved());
        assertEquals("保存为经验沉淀", run.getOutputSummary());
        assertEquals(Map.of("memoryId", 88), run.getMetadata());
    }

    @Test
    void createRejectsInvalidSkillName() {
        CreateSkillRunRequest request = new CreateSkillRunRequest();
        request.setFamilyId(10L);
        request.setSkillName("保存记忆");

        BusinessException exception = assertThrows(BusinessException.class, () -> skillRunService.create(request));

        assertEquals(ErrorCode.BAD_REQUEST.getCode(), exception.getCode());
    }

    @Test
    void updateRejectsInvalidStatus() {
        SkillRun run = new SkillRun();
        run.setId(7L);
        run.setFamilyId(10L);
        run.setStatus("RUNNING");
        when(skillRunRepository.selectById(7L)).thenReturn(run);

        UpdateSkillRunRequest request = new UpdateSkillRunRequest();
        request.setStatus("DONE");

        BusinessException exception = assertThrows(BusinessException.class, () -> skillRunService.update(7L, request));

        assertEquals(ErrorCode.BAD_REQUEST.getCode(), exception.getCode());
        verify(familyService).checkMembership(10L);
    }
}
