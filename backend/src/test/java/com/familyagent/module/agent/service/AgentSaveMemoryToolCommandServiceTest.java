package com.familyagent.module.agent.service;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.module.agent.dto.AgentSaveMemoryToolRequest;
import com.familyagent.module.agent.harness.AgentToolExecutor;
import com.familyagent.module.agent.harness.constant.AgentToolCallStatus;
import com.familyagent.module.agent.harness.constant.AgentToolErrorCode;
import com.familyagent.module.agent.harness.constant.AgentToolName;
import com.familyagent.module.agent.harness.dto.AgentToolCallRequest;
import com.familyagent.module.agent.harness.dto.AgentToolCallResult;
import com.familyagent.module.agent.harness.dto.CreateFamilyMemoryInput;
import com.familyagent.module.agent.harness.dto.CreateGrowthGuardRecordInput;
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

@ExtendWith(MockitoExtension.class)
class AgentSaveMemoryToolCommandServiceTest {

    @Mock private AgentToolExecutor toolExecutor;

    @Test
    void requestSave_experienceMapsToFamilyMemoryToolWithContext() {
        AgentToolCallResult<?> pending = AgentToolCallResult.confirmationRequired(
                AgentToolErrorCode.CONFIRMATION_REQUIRED.code(),
                "Agent tool requires confirmation",
                55L);
        doReturn(pending).when(toolExecutor).execute(any());
        AgentSaveMemoryToolCommandService service = new AgentSaveMemoryToolCommandService(toolExecutor);
        AgentSaveMemoryToolRequest request = request("EXPERIENCE");
        request.setTitle("应用题先拆题意");
        request.setTags(List.of("学习", "学习", " "));
        request.setRequestId("request-1");
        request.setSessionId(201L);

        AgentToolCallResult<?> result = service.requestSave(request, 101L);

        assertEquals(AgentToolCallStatus.CONFIRMATION_REQUIRED, result.status());
        ArgumentCaptor<AgentToolCallRequest<?>> captor = toolRequestCaptor();
        org.mockito.Mockito.verify(toolExecutor).execute(captor.capture());
        AgentToolCallRequest<?> toolRequest = captor.getValue();
        assertEquals(AgentToolName.CREATE_FAMILY_MEMORY.value(), toolRequest.toolName());
        assertEquals("request-1", toolRequest.context().requestId());
        assertEquals(10L, toolRequest.context().familyId());
        assertEquals(101L, toolRequest.context().viewerUserId());
        assertEquals(201L, toolRequest.context().sessionId());
        CreateFamilyMemoryInput input = assertInstanceOf(CreateFamilyMemoryInput.class, toolRequest.input());
        assertEquals("孩子先复述题意后列式更稳定。", input.content());
        assertEquals("ELDER_ADVICE", input.type());
        assertEquals("FAMILY_VISIBLE", input.scope());
        assertEquals("应用题先拆题意", input.summary());
        assertEquals(3, input.importance());
    }

    @Test
    void requestSave_observationMapsToGrowthTool() {
        doReturn(AgentToolCallResult.confirmationRequired(
                AgentToolErrorCode.CONFIRMATION_REQUIRED.code(),
                "Agent tool requires confirmation",
                56L)).when(toolExecutor).execute(any());
        AgentSaveMemoryToolCommandService service = new AgentSaveMemoryToolCommandService(toolExecutor);
        AgentSaveMemoryToolRequest request = request("OBSERVATION");
        request.setRelatedUserId(202L);
        request.setGrowthCategory("VISION");
        request.setGrowthSeverity(4);

        service.requestSave(request, 101L);

        ArgumentCaptor<AgentToolCallRequest<?>> captor = toolRequestCaptor();
        org.mockito.Mockito.verify(toolExecutor).execute(captor.capture());
        AgentToolCallRequest<?> toolRequest = captor.getValue();
        assertEquals(AgentToolName.CREATE_GROWTH_GUARD_RECORD.value(), toolRequest.toolName());
        CreateGrowthGuardRecordInput input = assertInstanceOf(CreateGrowthGuardRecordInput.class, toolRequest.input());
        assertEquals(202L, input.targetUserId());
        assertEquals("VISION", input.category());
        assertEquals(4, input.severity());
    }

    @Test
    void requestSave_rejectsUnsupportedCategoryBeforeToolExecution() {
        AgentSaveMemoryToolCommandService service = new AgentSaveMemoryToolCommandService(toolExecutor);
        AgentSaveMemoryToolRequest request = request("UNKNOWN");

        assertThrows(BusinessException.class, () -> service.requestSave(request, 101L));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ArgumentCaptor<AgentToolCallRequest<?>> toolRequestCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(AgentToolCallRequest.class);
    }

    private static AgentSaveMemoryToolRequest request(String category) {
        AgentSaveMemoryToolRequest request = new AgentSaveMemoryToolRequest();
        request.setFamilyId(10L);
        request.setWriteCategory(category);
        request.setContent("孩子先复述题意后列式更稳定。");
        request.setVisibility("FAMILY_VISIBLE");
        request.setMemoryType("ELDER_ADVICE");
        request.setDiaryEntryType("SELF_REFLECTION");
        request.setAgentMode("family");
        request.setSubject("FamilyAgent");
        request.setContextLabel("save_memory");
        return request;
    }
}
