package com.familyagent.module.agent.service;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.module.agent.dto.AgentSaveMemoryRequest;
import com.familyagent.module.agent.harness.AgentToolExecutor;
import com.familyagent.module.agent.harness.constant.AgentToolCallStatus;
import com.familyagent.module.agent.harness.constant.AgentToolErrorCode;
import com.familyagent.module.agent.harness.constant.AgentToolName;
import com.familyagent.module.agent.harness.dto.AgentSaveMemoryMetadata;
import com.familyagent.module.agent.harness.dto.AgentToolCallRequest;
import com.familyagent.module.agent.harness.dto.AgentToolCallResult;
import com.familyagent.module.agent.harness.dto.CreateFamilyMemoryInput;
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
class AgentSaveMemoryCommandServiceTest {

    @Mock
    private AgentToolExecutor toolExecutor;

    @Test
    void requestSave_routesFamilyLibraryWithDatabaseMemoryType() {
        doReturn(pending()).when(toolExecutor).execute(any());
        AgentSaveMemoryCommandService service = new AgentSaveMemoryCommandService(toolExecutor);
        AgentSaveMemoryRequest request = request("FAMILY", "KNOWLEDGE");
        request.setTitle("Word problem strategy");
        request.setTags(List.of("learning", "learning", " "));
        request.setRequestId("request-1");
        request.setSessionId(201L);
        request.setMetadata(metadata("FAMILY", "KNOWLEDGE"));

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
        assertEquals("KNOWLEDGE", input.type());
        assertEquals("FAMILY_VISIBLE", input.scope());
        assertEquals("Word problem strategy", input.summary());
        assertEquals(List.of("learning"), input.tags());
        assertEquals("FAMILY", input.metadata().getMemoryLibrary());
        assertEquals("KNOWLEDGE", input.metadata().getMemoryType());
    }

    @Test
    void requestSave_routesPersonalLibraryWithSameDatabaseMemoryType() {
        doReturn(pending()).when(toolExecutor).execute(any());
        AgentSaveMemoryCommandService service = new AgentSaveMemoryCommandService(toolExecutor);
        AgentSaveMemoryRequest request = request("PERSONAL", "EXPERIENCE");
        request.setVisibility("SELECTED_FAMILIES_VISIBLE");
        request.setSelectedFamilyIds(List.of(10L, 12L));
        request.setMetadata(metadata("PERSONAL", "EXPERIENCE"));

        service.requestSave(request, 101L);

        ArgumentCaptor<AgentToolCallRequest<?>> captor = toolRequestCaptor();
        verify(toolExecutor).execute(captor.capture());
        AgentToolCallRequest<?> toolRequest = captor.getValue();
        assertEquals(AgentToolName.CREATE_PERSONAL_MEMORY.value(), toolRequest.toolName());
        CreatePersonalMemoryInput input = assertInstanceOf(CreatePersonalMemoryInput.class, toolRequest.input());
        assertEquals("EXPERIENCE", input.type());
        assertEquals("SELECTED_FAMILIES_VISIBLE", input.visibility());
        assertEquals(List.of(10L, 12L), input.selectedFamilyIds());
    }

    @Test
    void requestSave_keepsObservationAsDatabaseTypeWithoutSceneFields() {
        doReturn(pending()).when(toolExecutor).execute(any());
        AgentSaveMemoryCommandService service = new AgentSaveMemoryCommandService(toolExecutor);
        AgentSaveMemoryRequest request = request("FAMILY", "OBSERVATION");
        request.setRelatedUserId(202L);
        request.setMetadata(metadata("FAMILY", "OBSERVATION"));

        service.requestSave(request, 101L);

        ArgumentCaptor<AgentToolCallRequest<?>> captor = toolRequestCaptor();
        verify(toolExecutor).execute(captor.capture());
        CreateFamilyMemoryInput input = assertInstanceOf(
                CreateFamilyMemoryInput.class,
                captor.getValue().input());
        assertEquals(202L, input.relatedUserId());
        assertEquals("OBSERVATION", input.type());
        assertEquals(4, input.importance());
    }

    @Test
    void requestSave_rejectsUnsupportedMemoryTypeBeforeExecution() {
        AgentSaveMemoryCommandService service = new AgentSaveMemoryCommandService(toolExecutor);
        AgentSaveMemoryRequest request = request("FAMILY", "UNKNOWN");

        assertThrows(BusinessException.class, () -> service.requestSave(request, 101L));
        verify(toolExecutor, never()).execute(any());
    }

    private static AgentToolCallResult<?> pending() {
        return AgentToolCallResult.confirmationRequired(
                AgentToolErrorCode.CONFIRMATION_REQUIRED.code(),
                "Agent tool requires confirmation",
                55L);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ArgumentCaptor<AgentToolCallRequest<?>> toolRequestCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(AgentToolCallRequest.class);
    }

    private static AgentSaveMemoryRequest request(String memoryLibrary, String memoryType) {
        AgentSaveMemoryRequest request = new AgentSaveMemoryRequest();
        request.setFamilyId(10L);
        request.setMemoryLibrary(memoryLibrary);
        request.setMemoryType(memoryType);
        request.setContent("The child explains the problem before calculating.");
        request.setVisibility("FAMILY_VISIBLE");
        request.setAgentMode("family");
        request.setSubject("FamilyAgent");
        request.setContextLabel("save_memory");
        return request;
    }

    private static AgentSaveMemoryMetadata metadata(String memoryLibrary, String memoryType) {
        AgentSaveMemoryMetadata metadata = new AgentSaveMemoryMetadata();
        metadata.setSkillName("save_memory");
        metadata.setSource("FAMILY_COMPANION_TOOL");
        metadata.setRelationSource("FAMILY_AGENT_TOOL");
        metadata.setMemoryLibrary(memoryLibrary);
        metadata.setMemoryType(memoryType);
        metadata.setConfirmationPolicy("USER_APPROVED_EDITABLE_DRAFT");
        return metadata;
    }
}
