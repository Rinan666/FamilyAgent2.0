package com.familyagent.module.agent.service;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.module.agent.dto.AgentSaveMemoryToolRequest;
import com.familyagent.module.agent.harness.AgentToolExecutor;
import com.familyagent.module.agent.harness.constant.AgentToolCallStatus;
import com.familyagent.module.agent.harness.constant.AgentToolErrorCode;
import com.familyagent.module.agent.harness.constant.AgentToolName;
import com.familyagent.module.agent.harness.dto.AgentSaveMemoryMetadata;
import com.familyagent.module.agent.harness.dto.AgentToolCallRequest;
import com.familyagent.module.agent.harness.dto.AgentToolCallResult;
import com.familyagent.module.agent.harness.dto.CreateDiaryEntryInput;
import com.familyagent.module.agent.harness.dto.CreateFamilyMemoryInput;
import com.familyagent.module.agent.harness.dto.CreateGrowthGuardRecordInput;
import com.familyagent.module.agent.harness.dto.CreatePersonalMemoryInput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AgentSaveMemoryToolCommandServiceTest {

    @Mock private AgentToolExecutor toolExecutor;

    @Test
    void requestSave_experienceMapsToFamilyMemoryToolWithContextAndMetadata() {
        AgentToolCallResult<?> pending = AgentToolCallResult.confirmationRequired(
                AgentToolErrorCode.CONFIRMATION_REQUIRED.code(),
                "Agent tool requires confirmation",
                55L);
        doReturn(pending).when(toolExecutor).execute(any());
        AgentSaveMemoryToolCommandService service = new AgentSaveMemoryToolCommandService(toolExecutor);
        AgentSaveMemoryToolRequest request = request("EXPERIENCE");
        request.setTitle("Word problem strategy");
        request.setTags(List.of("learning", "learning", " "));
        request.setRequestId("request-1");
        request.setSessionId(201L);
        request.setMetadata(metadata());

        AgentToolCallResult<?> result = service.requestSave(request, 101L);

        assertEquals(AgentToolCallStatus.CONFIRMATION_REQUIRED, result.status());
        ArgumentCaptor<AgentToolCallRequest<?>> captor = toolRequestCaptor();
        verify(toolExecutor).execute(captor.capture());
        AgentToolCallRequest<?> toolRequest = captor.getValue();
        assertEquals(AgentToolName.CREATE_FAMILY_MEMORY.value(), toolRequest.toolName());
        assertEquals("request-1", toolRequest.context().requestId());
        assertEquals(10L, toolRequest.context().familyId());
        assertEquals(101L, toolRequest.context().viewerUserId());
        assertEquals(201L, toolRequest.context().sessionId());
        CreateFamilyMemoryInput input = assertInstanceOf(CreateFamilyMemoryInput.class, toolRequest.input());
        assertEquals("The child explains the problem before calculating.", input.content());
        assertEquals("ELDER_ADVICE", input.type());
        assertEquals("FAMILY_VISIBLE", input.scope());
        assertEquals("Word problem strategy", input.summary());
        assertEquals(3, input.importance());
        assertEquals("FAMILY_COMPANION_TOOL", input.metadata().getSource());
        assertEquals("FAMILY_MEMORY", input.metadata().getPlannedTool());
    }

    @Test
    void requestSave_recordMapsToDiaryToolWithTitleTagsAndMetadata() {
        doReturn(AgentToolCallResult.confirmationRequired(
                AgentToolErrorCode.CONFIRMATION_REQUIRED.code(),
                "Agent tool requires confirmation",
                57L)).when(toolExecutor).execute(any());
        AgentSaveMemoryToolCommandService service = new AgentSaveMemoryToolCommandService(toolExecutor);
        AgentSaveMemoryToolRequest request = request("RECORD");
        request.setTitle("Study reflection");
        request.setTags(List.of("learning", " ", "reflection"));
        request.setMetadata(metadata());

        service.requestSave(request, 101L);

        ArgumentCaptor<AgentToolCallRequest<?>> captor = toolRequestCaptor();
        verify(toolExecutor).execute(captor.capture());
        AgentToolCallRequest<?> toolRequest = captor.getValue();
        assertEquals(AgentToolName.CREATE_DIARY_ENTRY.value(), toolRequest.toolName());
        CreateDiaryEntryInput input = assertInstanceOf(CreateDiaryEntryInput.class, toolRequest.input());
        assertEquals("Study reflection", input.title());
        assertEquals(List.of("learning", "reflection"), input.tags());
        assertEquals("SELF_REFLECTION", input.entryType());
        assertEquals("FAMILY_COMPANION_TOOL", input.metadata().getSource());
        assertEquals("FAMILY_MEMORY", input.metadata().getPlannedTool());
    }

    @Test
    void requestSave_personalExperienceMapsToPersonalMemoryTool() {
        doReturn(AgentToolCallResult.confirmationRequired(
                AgentToolErrorCode.CONFIRMATION_REQUIRED.code(),
                "Agent tool requires confirmation",
                58L)).when(toolExecutor).execute(any());
        AgentSaveMemoryToolCommandService service = new AgentSaveMemoryToolCommandService(toolExecutor);
        AgentSaveMemoryToolRequest request = request("EXPERIENCE");
        request.setMemoryLibrary("PERSONAL");
        request.setPersonalMemoryType("KNOWLEDGE");
        request.setVisibility("SELECTED_FAMILIES_VISIBLE");
        request.setSelectedFamilyIds(List.of(10L, 12L));
        request.setMetadata(metadata());

        service.requestSave(request, 101L);

        ArgumentCaptor<AgentToolCallRequest<?>> captor = toolRequestCaptor();
        verify(toolExecutor).execute(captor.capture());
        AgentToolCallRequest<?> toolRequest = captor.getValue();
        assertEquals(AgentToolName.CREATE_PERSONAL_MEMORY.value(), toolRequest.toolName());
        CreatePersonalMemoryInput input = assertInstanceOf(CreatePersonalMemoryInput.class, toolRequest.input());
        assertEquals("KNOWLEDGE", input.type());
        assertEquals("SELECTED_FAMILIES_VISIBLE", input.visibility());
        assertEquals(List.of(10L, 12L), input.selectedFamilyIds());
    }

    @Test
    void requestSave_observationMapsToGrowthToolWithTargetFollowUpAndMetadata() {
        doReturn(AgentToolCallResult.confirmationRequired(
                AgentToolErrorCode.CONFIRMATION_REQUIRED.code(),
                "Agent tool requires confirmation",
                56L)).when(toolExecutor).execute(any());
        AgentSaveMemoryToolCommandService service = new AgentSaveMemoryToolCommandService(toolExecutor);
        AgentSaveMemoryToolRequest request = request("OBSERVATION");
        request.setRelatedUserId(202L);
        request.setGrowthCategory("VISION");
        request.setGrowthSeverity(4);
        request.setMetadata(metadata());
        request.getMetadata().setRelatedUserId(202L);
        request.getMetadata().setFollowUpStatus("PENDING");

        service.requestSave(request, 101L);

        ArgumentCaptor<AgentToolCallRequest<?>> captor = toolRequestCaptor();
        verify(toolExecutor).execute(captor.capture());
        AgentToolCallRequest<?> toolRequest = captor.getValue();
        assertEquals(AgentToolName.CREATE_GROWTH_GUARD_RECORD.value(), toolRequest.toolName());
        CreateGrowthGuardRecordInput input = assertInstanceOf(CreateGrowthGuardRecordInput.class, toolRequest.input());
        assertEquals(202L, input.targetUserId());
        assertEquals("VISION", input.category());
        assertEquals(4, input.severity());
        assertEquals(202L, input.metadata().getRelatedUserId());
        assertEquals("PENDING", input.metadata().getFollowUpStatus());
        assertEquals("FAMILY_COMPANION_TOOL", input.metadata().getSource());
    }

    @Test
    void requestSave_rejectsUnsupportedCategoryBeforeToolExecution() {
        AgentSaveMemoryToolCommandService service = new AgentSaveMemoryToolCommandService(toolExecutor);
        AgentSaveMemoryToolRequest request = request("UNKNOWN");

        assertThrows(BusinessException.class, () -> service.requestSave(request, 101L));
        verify(toolExecutor, never()).execute(any());
    }

    @Test
    void requestSave_rejectsGrowthObservationWithoutTargetBeforeConfirmation() {
        AgentSaveMemoryToolCommandService service = new AgentSaveMemoryToolCommandService(toolExecutor);
        AgentSaveMemoryToolRequest request = request("OBSERVATION");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.requestSave(request, 101L));

        assertEquals(ErrorCode.BAD_REQUEST.getCode(), exception.getCode());
        verify(toolExecutor, never()).execute(any());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ArgumentCaptor<AgentToolCallRequest<?>> toolRequestCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(AgentToolCallRequest.class);
    }

    private static AgentSaveMemoryToolRequest request(String category) {
        AgentSaveMemoryToolRequest request = new AgentSaveMemoryToolRequest();
        request.setFamilyId(10L);
        request.setWriteCategory(category);
        request.setContent("The child explains the problem before calculating.");
        request.setVisibility("FAMILY_VISIBLE");
        request.setMemoryType("ELDER_ADVICE");
        request.setDiaryEntryType("SELF_REFLECTION");
        request.setAgentMode("family");
        request.setSubject("FamilyAgent");
        request.setContextLabel("save_memory");
        return request;
    }

    private static AgentSaveMemoryMetadata metadata() {
        AgentSaveMemoryMetadata metadata = new AgentSaveMemoryMetadata();
        metadata.setSkillName("save_memory");
        metadata.setSource("FAMILY_COMPANION_TOOL");
        metadata.setRelationSource("FAMILY_AGENT_TOOL");
        metadata.setFamilyName("Chen Family");
        metadata.setViewerRole("GUARDIAN");
        metadata.setSavedFromMessageRole("user");
        metadata.setPlannedTool("FAMILY_MEMORY");
        metadata.setPlannedTitle("Word problem strategy");
        metadata.setPlannedReason("Has durable learning value");
        metadata.setVisibility("FAMILY_VISIBLE");
        metadata.setScope("FAMILY_VISIBLE");
        metadata.setConfirmationPolicy("USER_CONFIRMATION_OR_EXPLICIT_SAVE_COMMAND");
        metadata.setSavedAt("2026-07-04T10:00:00.000Z");
        return metadata;
    }
}
