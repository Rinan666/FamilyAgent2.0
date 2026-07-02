package com.familyagent.module.agent.harness.tool;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.module.agent.harness.AgentRunContext;
import com.familyagent.module.agent.harness.constant.AgentToolConfirmationRequirement;
import com.familyagent.module.agent.harness.constant.AgentToolName;
import com.familyagent.module.agent.harness.constant.AgentToolSideEffect;
import com.familyagent.module.agent.harness.dto.CreateDiaryEntryInput;
import com.familyagent.module.agent.harness.dto.CreateDiaryEntryOutput;
import com.familyagent.module.diary.dto.CreateDiaryEntryRequest;
import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.diary.facade.AgentDiaryEntryFacade;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CreateDiaryEntryToolTest {

    private final AgentDiaryEntryFacade diaryEntryFacade = mock(AgentDiaryEntryFacade.class);
    private final CreateDiaryEntryTool tool = new CreateDiaryEntryTool(diaryEntryFacade);

    private final AgentRunContext context = new AgentRunContext(
            "req-1",
            10L,
            101L,
            null,
            "family_memory",
            "family",
            "test");

    @Test
    void descriptor_requiresConfirmationForWrite() {
        assertEquals(AgentToolName.CREATE_DIARY_ENTRY.value(), tool.descriptor().name());
        assertEquals(AgentToolSideEffect.WRITE, tool.descriptor().sideEffect());
        assertEquals(AgentToolConfirmationRequirement.REQUIRED, tool.descriptor().confirmationRequirement());
    }

    @Test
    void execute_usesContextFamilyAndDiaryFacade() {
        CreateDiaryEntryInput input = new CreateDiaryEntryInput(
                "Today we talked about bedtime.",
                "DAILY",
                "Bedtime",
                "calm",
                List.of("bedtime"),
                "FAMILY_SHARED");
        DiaryEntry entry = new DiaryEntry();
        entry.setId(88L);
        when(diaryEntryFacade.create(org.mockito.ArgumentMatchers.any(CreateDiaryEntryRequest.class)))
                .thenReturn(entry);

        CreateDiaryEntryOutput output = tool.execute(context, input);

        ArgumentCaptor<CreateDiaryEntryRequest> captor = ArgumentCaptor.forClass(CreateDiaryEntryRequest.class);
        verify(diaryEntryFacade).create(captor.capture());
        CreateDiaryEntryRequest request = captor.getValue();
        assertEquals(10L, request.getFamilyId());
        assertEquals("Today we talked about bedtime.", request.getContent());
        assertEquals("DAILY", request.getEntryType());
        assertEquals("Bedtime", request.getTitle());
        assertEquals("calm", request.getMood());
        assertEquals(List.of("bedtime"), request.getTags());
        assertEquals("FAMILY_SHARED", request.getVisibility());
        assertEquals(88L, output.diaryEntryId());
    }

    @Test
    void execute_blankContent_rejectsBeforeFacadeCall() {
        CreateDiaryEntryInput input = new CreateDiaryEntryInput(
                " ",
                "DAILY",
                null,
                null,
                List.of(),
                null);

        assertThrows(BusinessException.class, () -> tool.execute(context, input));
    }
}
