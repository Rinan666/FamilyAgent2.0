package com.familyagent.module.agent.service;

import com.familyagent.common.constant.SkillRunStatus;
import com.familyagent.module.agent.constant.AgentSaveMemorySkillContract;
import com.familyagent.module.agent.dto.AgentSaveMemoryPlanRequest;
import com.familyagent.module.agent.dto.AgentMemorySavePlan;
import com.familyagent.module.skillrun.dto.CreateSkillRunRequest;
import com.familyagent.module.skillrun.dto.SkillRunMetadata;
import com.familyagent.module.skillrun.dto.UpdateSkillRunRequest;
import org.springframework.stereotype.Component;

@Component
public class AgentSaveMemoryPlanAssembler {

    public CreateSkillRunRequest createRunRequest(
            AgentSaveMemoryPlanRequest request,
            String requestId,
            Long agentRunId) {
        CreateSkillRunRequest runRequest = new CreateSkillRunRequest();
        runRequest.setFamilyId(request.getFamilyId());
        runRequest.setSkillName(AgentSaveMemorySkillContract.SKILL_NAME);
        runRequest.setStatus(SkillRunStatus.RUNNING.name());
        runRequest.setSource(request.getSource());
        runRequest.setInputSummary("Save-memory planning requested");
        runRequest.setSaved(false);
        runRequest.setMetadata(metadata(requestId, agentRunId));
        return runRequest;
    }

    public UpdateSkillRunRequest completedRunUpdate(
            AgentMemorySavePlan plan,
            String requestId,
            Long agentRunId) {
        boolean awaitingPersistence = plan.isShouldSave();
        UpdateSkillRunRequest update = new UpdateSkillRunRequest();
        update.setStatus((awaitingPersistence ? SkillRunStatus.PLANNED : SkillRunStatus.SUCCEEDED).name());
        update.setOutputSummary(plan.getReason());
        update.setSaved(false);
        SkillRunMetadata metadata = metadata(requestId, agentRunId);
        metadata.setMemoryLibrary(plan.getMemoryLibrary());
        metadata.setMemoryType(plan.getMemoryType());
        metadata.setPlannedReason(plan.getReason());
        update.setMetadata(metadata);
        return update;
    }

    public UpdateSkillRunRequest failedRunUpdate(
            String requestId,
            String errorCode,
            Long agentRunId) {
        UpdateSkillRunRequest update = new UpdateSkillRunRequest();
        update.setStatus(SkillRunStatus.FAILED.name());
        update.setOutputSummary("Save-memory planning failed");
        update.setSaved(false);
        SkillRunMetadata metadata = metadata(requestId, agentRunId);
        metadata.setExecutionErrorCode(errorCode);
        update.setMetadata(metadata);
        return update;
    }

    private SkillRunMetadata metadata(String requestId, Long agentRunId) {
        SkillRunMetadata metadata = new SkillRunMetadata();
        metadata.setRequestId(requestId);
        metadata.setAgentRunId(agentRunId);
        metadata.setSkillVersion(AgentSaveMemorySkillContract.SKILL_VERSION);
        metadata.setPromptVersion(AgentSaveMemorySkillContract.PROMPT_VERSION);
        metadata.setSchemaVersion(AgentSaveMemorySkillContract.SCHEMA_VERSION);
        return metadata;
    }
}
