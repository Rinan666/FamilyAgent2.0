package com.familyagent.module.skillrun.service;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.module.family.facade.FamilyMembershipFacade;
import com.familyagent.module.skillrun.entity.SkillRun;
import com.familyagent.module.skillrun.repository.SkillRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SkillRunQueryServiceTest {

    @Mock private SkillRunRepository skillRunRepository;
    @Mock private FamilyMembershipFacade familyMembershipFacade;

    private SkillRunQueryService queryService;

    @BeforeEach
    void setUp() {
        queryService = new SkillRunQueryService(
                skillRunRepository,
                familyMembershipFacade,
                new SkillRunInputPolicy());
    }

    @Test
    void listFamilyRunsChecksMembershipAndNormalizesLimit() {
        List<SkillRun> runs = List.of(new SkillRun());
        when(skillRunRepository.findByFamily(10L, 100)).thenReturn(runs);

        assertSame(runs, queryService.listFamilyRuns(10L, 500));

        verify(familyMembershipFacade).checkMembership(10L);
        verify(skillRunRepository).findByFamily(10L, 100);
    }

    @Test
    void getRejectsMissingRun() {
        BusinessException error = assertThrows(BusinessException.class, () -> queryService.get(7L));

        assertEquals(ErrorCode.NOT_FOUND.getCode(), error.getCode());
    }
}
