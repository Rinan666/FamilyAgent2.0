package com.familyagent.module.assessment.service;

import com.familyagent.module.assessment.dto.SubmitTestRequest;
import com.familyagent.module.assessment.entity.AbilityProfile;
import com.familyagent.module.assessment.entity.TestRecord;
import com.familyagent.module.assessment.entity.WrongQuestionRecord;
import com.familyagent.module.assessment.repository.AbilityProfileRepository;
import com.familyagent.module.assessment.repository.TestRecordRepository;
import com.familyagent.module.assessment.repository.WrongQuestionRecordRepository;
import com.familyagent.module.family.service.FamilyService;
import com.familyagent.module.question.repository.QuestionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssessmentServiceTest {

    @Mock private TestRecordRepository testRecordRepository;
    @Mock private WrongQuestionRecordRepository wrongQuestionRecordRepository;
    @Mock private AbilityProfileRepository abilityProfileRepository;
    @Mock private QuestionRepository questionRepository;
    @Mock private FamilyService familyService;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks private AssessmentService assessmentService;

    @Test
    void updateProfile_shouldCreateNewProfileWithLocalStats() {
        when(abilityProfileRepository.findByUserAndKp(1L, 6L)).thenReturn(null);

        assessmentService.updateProfile(1L, 6L, true);

        ArgumentCaptor<AbilityProfile> captor = ArgumentCaptor.forClass(AbilityProfile.class);
        verify(abilityProfileRepository).insert(captor.capture());
        AbilityProfile profile = captor.getValue();

        assertEquals(1L, profile.getUserId());
        assertEquals(6L, profile.getKpId());
        assertEquals(1.0, profile.getMasteryProbability(), 0.0001);
        assertEquals(1, profile.getTotalAttempts());
        assertEquals(1, profile.getCorrectAttempts());
        assertEquals(1, profile.getConsecutiveCorrect());
        assertEquals("PRIVATE", profile.getVisibility());
        assertNotNull(profile.getLastAttemptAt());
        assertNotNull(profile.getLastCorrectAt());
    }

    @Test
    void updateProfile_shouldUpdateExistingProfileWithLocalStats() {
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

        assessmentService.updateProfile(1L, 6L, false);

        verify(abilityProfileRepository).updateById(existing);
        assertEquals(0.5, existing.getMasteryProbability(), 0.0001);
        assertEquals(4, existing.getTotalAttempts());
        assertEquals(2, existing.getCorrectAttempts());
        assertEquals(0, existing.getConsecutiveCorrect());
    }

    @Test
    void submitTest_shouldPersistRecordWithSubmittedInsertAndUpdateProfiles() {
        SubmitTestRequest.TestQuestionResult first = new SubmitTestRequest.TestQuestionResult();
        first.setQuestionId(101L);
        first.setKpId(6L);
        first.setAnswer("x = 4");
        first.setScore(90.0);
        first.setCorrect(true);
        first.setTimeSpent(30);

        SubmitTestRequest.TestQuestionResult second = new SubmitTestRequest.TestQuestionResult();
        second.setQuestionId(102L);
        second.setKpId(7L);
        second.setAnswer("x > 5");
        second.setScore(40.0);
        second.setCorrect(false);
        second.setErrorType("concept misunderstanding");
        second.setFeedback("Boundary handling needs review.");
        second.setParentExplanation("The learner is not yet stable on inequality boundaries.");
        second.setNextSuggestion("Fix this item first, then try another similar basic item.");
        second.setTimeSpent(45);

        SubmitTestRequest request = new SubmitTestRequest();
        request.setUserId(1L);
        request.setFamilyId(2L);
        request.setSource("GENERATED_TEST");
        request.setTotalTime(75);
        request.setResults(List.of(first, second));

        when(abilityProfileRepository.findByUserAndKp(eq(1L), anyLong())).thenReturn(null);
        when(testRecordRepository.insertSubmitted(any(), anyList(), anyString(), anyString(), anyString(), anyList()))
            .thenAnswer(invocation -> {
                TestRecord saved = invocation.getArgument(0);
                saved.setId(500L);
                return 1;
            });

        TestRecord record = assessmentService.submitTest(request);

        assertEquals(1L, record.getUserId());
        assertEquals(2L, record.getFamilyId());
        assertEquals(List.of(101L, 102L), record.getQuestionIds());
        assertEquals(List.of(30, 45), record.getTimeSpent());
        assertEquals(65.0, record.getTotalScore(), 0.0001);
        assertEquals("PRIVATE", record.getVisibility());

        verify(testRecordRepository).insertSubmitted(
            same(record),
            eq(List.of(101L, 102L)),
            contains("\"101\":\"x = 4\""),
            contains("\"102\":40.0"),
            eq("{}"),
            eq(List.of(30, 45))
        );
        verify(testRecordRepository, never()).insert(any());

        ArgumentCaptor<WrongQuestionRecord> wrongCaptor = ArgumentCaptor.forClass(WrongQuestionRecord.class);
        verify(wrongQuestionRecordRepository).insertOrUpdate(wrongCaptor.capture());
        WrongQuestionRecord wrongRecord = wrongCaptor.getValue();
        assertEquals(1L, wrongRecord.getUserId());
        assertEquals(2L, wrongRecord.getFamilyId());
        assertEquals(500L, wrongRecord.getTestRecordId());
        assertEquals(102L, wrongRecord.getQuestionId());
        assertEquals(7L, wrongRecord.getKpId());
        assertEquals("x > 5", wrongRecord.getStudentAnswer());
        assertEquals(40.0, wrongRecord.getScore(), 0.0001);
        assertEquals("concept misunderstanding", wrongRecord.getErrorType());
        assertEquals("Boundary handling needs review.", wrongRecord.getFeedback());
        assertEquals("The learner is not yet stable on inequality boundaries.", wrongRecord.getParentExplanation());
        assertEquals("Fix this item first, then try another similar basic item.", wrongRecord.getNextSuggestion());
        assertEquals("OPEN", wrongRecord.getStatus());
        verify(familyService).checkMembership(2L);
        verify(abilityProfileRepository, times(2)).insert(any());
    }

    @Test
    void getUserProfiles_shouldReturnProfileList() {
        when(abilityProfileRepository.findByUserId(1L)).thenReturn(List.of(new AbilityProfile()));

        List<AbilityProfile> result = assessmentService.getUserProfiles(1L);

        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    void getZPD_shouldReturnLimitedResults() {
        when(abilityProfileRepository.findZPD(eq(1L), eq(10))).thenReturn(List.of());

        List<AbilityProfile> result = assessmentService.getZPD(1L);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
