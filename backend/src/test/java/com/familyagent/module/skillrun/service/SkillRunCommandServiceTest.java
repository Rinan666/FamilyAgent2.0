package com.familyagent.module.skillrun.service;

import cn.dev33.satoken.stp.StpUtil;
import com.familyagent.module.family.facade.FamilyMembershipFacade;
import com.familyagent.module.skillrun.dto.CreateSkillRunRequest;
import com.familyagent.module.skillrun.dto.SkillRunMetadata;
import com.familyagent.module.skillrun.dto.SkillRunSourceRef;
import com.familyagent.module.skillrun.entity.SkillRun;
import com.familyagent.module.skillrun.repository.SkillRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SkillRunCommandServiceTest {

    @Mock private SkillRunRepository skillRunRepository;
    @Mock private FamilyMembershipFacade familyMembershipFacade;

    private SkillRunCommandService commandService;

    @BeforeEach
    void setUp() {
        commandService = new SkillRunCommandService(
                skillRunRepository,
                familyMembershipFacade,
                new SkillRunInputPolicy());
    }

    @Test
    void createChecksMembershipAndStoresAuditFields() {
        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(101L);
            CreateSkillRunRequest request = new CreateSkillRunRequest();
            request.setFamilyId(10L);
            request.setSkillName("Save_Memory");
            request.setInputSummary("用户要求保存一条家庭经验");
            SkillRunSourceRef source = new SkillRunSourceRef();
            source.setSourceType("CHAT");
            source.setSourceId(1L);
            request.setUsedSources(List.of(source));
            SkillRunMetadata metadata = new SkillRunMetadata();
            metadata.setMemoryLibrary("FAMILY");
            metadata.setMemoryType("EXPERIENCE");
            request.setMetadata(metadata);

            SkillRun result = commandService.create(request);

            verify(familyMembershipFacade).checkMembership(10L);
            ArgumentCaptor<SkillRun> captor = ArgumentCaptor.forClass(SkillRun.class);
            verify(skillRunRepository).insert(captor.capture());
            SkillRun inserted = captor.getValue();
            assertEquals(result, inserted);
            assertEquals(101L, inserted.getTriggeredBy());
            assertEquals("save_memory", inserted.getSkillName());
            assertEquals("PLANNED", inserted.getStatus());
            assertEquals("FAMILY_AGENT", inserted.getSource());
            assertFalse(inserted.getSaved());
            assertEquals("FAMILY", inserted.getMetadata().getMemoryLibrary());
            assertEquals("EXPERIENCE", inserted.getMetadata().getMemoryType());
        }
    }
}
