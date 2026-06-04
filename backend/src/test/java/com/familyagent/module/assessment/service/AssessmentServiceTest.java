package com.familyagent.module.assessment.service;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.infra.ai.AIServiceClient;
import com.familyagent.module.assessment.entity.AbilityProfile;
import com.familyagent.module.assessment.repository.AbilityProfileRepository;
import com.familyagent.module.assessment.repository.TestRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssessmentServiceTest {

    @Mock private TestRecordRepository testRecordRepository;
    @Mock private AbilityProfileRepository abilityProfileRepository;
    @Mock private AIServiceClient aiServiceClient;
    @InjectMocks private AssessmentService assessmentService;

    // ============================================
    // BKT delegation tests
    // ============================================

    @Test
    void updateProfile_shouldCreateNewProfileAndCallPythonBKT() {
        when(abilityProfileRepository.findByUserAndKp(1L, 6L)).thenReturn(null);
        when(aiServiceClient.updateBKT(eq(0.5), eq(true), eq(0)))
            .thenReturn(Map.of(
                "success", true,
                "posterior_mastery", 0.8182,
                "mastery_level", "强",
                "delta", 0.3182,
                "fallback", false
            ));

        assessmentService.updateProfile(1L, 6L, true);

        ArgumentCaptor<AbilityProfile> captor = ArgumentCaptor.forClass(AbilityProfile.class);
        verify(abilityProfileRepository).insert(captor.capture()); // 新建档案
        AbilityProfile profile = captor.getValue();

        assertEquals(0.8182, profile.getMasteryProbability(), 0.0001);
        assertEquals(1, profile.getTotalAttempts());
        assertEquals(1, profile.getCorrectAttempts());
        assertEquals(1, profile.getConsecutiveCorrect());
        assertNotNull(profile.getLastAttemptAt());
        assertNotNull(profile.getLastCorrectAt());
    }

    @Test
    void updateProfile_shouldUpdateExistingProfile() {
        AbilityProfile existing = new AbilityProfile();
        existing.setId(10L);
        existing.setUserId(1L);
        existing.setKpId(6L);
        existing.setMasteryProbability(0.5);
        existing.setTotalAttempts(3);
        existing.setCorrectAttempts(2);
        existing.setConsecutiveCorrect(1);
        existing.setLastAttemptAt(LocalDateTime.now().minusDays(2));

        when(abilityProfileRepository.findByUserAndKp(1L, 6L)).thenReturn(existing);
        when(aiServiceClient.updateBKT(eq(0.5), eq(false), anyInt()))
            .thenReturn(Map.of(
                "success", true,
                "posterior_mastery", 0.1111,
                "mastery_level", "弱",
                "delta", -0.3889,
                "fallback", false
            ));

        assessmentService.updateProfile(1L, 6L, false);

        verify(abilityProfileRepository).updateById(existing); // 更新已有档案
        assertEquals(0.1111, existing.getMasteryProbability(), 0.0001);
        assertEquals(4, existing.getTotalAttempts());
        assertEquals(2, existing.getCorrectAttempts()); // 未增加
        assertEquals(0, existing.getConsecutiveCorrect()); // 重置连续正确
    }

    @Test
    void updateProfile_shouldThrowWhenPythonUnreachableAndNoFallback() {
        when(abilityProfileRepository.findByUserAndKp(1L, 6L)).thenReturn(null);
        when(aiServiceClient.updateBKT(anyDouble(), anyBoolean(), anyInt()))
            .thenThrow(new RuntimeException("Connection refused"));

        assertThrows(BusinessException.class, () -> assessmentService.updateProfile(1L, 6L, true));
    }

    @Test
    void updateProfile_shouldUseFallbackWhenPythonReturnsFallback() {
        when(abilityProfileRepository.findByUserAndKp(1L, 6L)).thenReturn(null);
        when(aiServiceClient.updateBKT(eq(0.5), eq(true), eq(0)))
            .thenReturn(Map.of(
                "success", true,
                "posterior_mastery", 0.6,
                "mastery_level", "中",
                "delta", 0.1,
                "fallback", true
            ));

        assessmentService.updateProfile(1L, 6L, true);

        // 降级情况下不抛异常，正常持久化
        verify(abilityProfileRepository).insert(any());
    }

    // ============================================
    // Query tests
    // ============================================

    @Test
    void getUserProfiles_shouldReturnProfileList() {
        when(abilityProfileRepository.findByUserId(1L))
            .thenReturn(List.of(new AbilityProfile()));

        List<AbilityProfile> result = assessmentService.getUserProfiles(1L);

        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    void getZPD_shouldReturnLimitedResults() {
        when(abilityProfileRepository.findZPD(eq(1L), eq(10)))
            .thenReturn(List.of());

        List<AbilityProfile> result = assessmentService.getZPD(1L);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
