package com.familyagent.module.agent.service;

import com.familyagent.common.constant.SkillRunStatus;
import com.familyagent.module.agent.constant.AgentDraftSkillContract;
import com.familyagent.module.skillrun.dto.CreateSkillRunRequest;
import com.familyagent.module.skillrun.dto.SkillRunMetadata;
import com.familyagent.module.skillrun.dto.UpdateSkillRunRequest;
import org.springframework.stereotype.Component;

@Component
public class AgentDraftRunAssembler {

    public CreateSkillRunRequest create(
            AgentDraftSkillContract contract,
            Long familyId,
            String requestId,
            Long agentRunId) {
        CreateSkillRunRequest request = new CreateSkillRunRequest();
        request.setFamilyId(familyId);
        request.setSkillName(contract.getSkillName());
        request.setStatus(SkillRunStatus.RUNNING.name());
        request.setSource(AgentDraftSkillContract.SOURCE);
        request.setInputSummary("Draft generation requested");
        request.setSaved(false);
        request.setMetadata(metadata(contract, requestId, agentRunId));
        return request;
    }

    public UpdateSkillRunRequest completed(
            AgentDraftSkillContract contract,
            String requestId,
            Long agentRunId) {
        UpdateSkillRunRequest update = new UpdateSkillRunRequest();
        update.setStatus(SkillRunStatus.SUCCEEDED.name());
        update.setOutputSummary("Draft generated for user review");
        update.setSaved(false);
        update.setMetadata(metadata(contract, requestId, agentRunId));
        return update;
    }

    public UpdateSkillRunRequest failed(
            AgentDraftSkillContract contract,
            String requestId,
            Long agentRunId,
            String errorCode) {
        UpdateSkillRunRequest update = new UpdateSkillRunRequest();
        update.setStatus(SkillRunStatus.FAILED.name());
        update.setOutputSummary("Draft generation failed");
        update.setSaved(false);
        SkillRunMetadata metadata = metadata(contract, requestId, agentRunId);
        metadata.setExecutionErrorCode(errorCode);
        update.setMetadata(metadata);
        return update;
    }

    private SkillRunMetadata metadata(
            AgentDraftSkillContract contract,
            String requestId,
            Long agentRunId) {
        SkillRunMetadata metadata = new SkillRunMetadata();
        metadata.setRequestId(requestId);
        metadata.setAgentRunId(agentRunId);
        metadata.setSkillVersion(contract.getSkillVersion());
        metadata.setPromptVersion(contract.getPromptVersion());
        metadata.setSchemaVersion(contract.getSchemaVersion());
        return metadata;
    }
}
