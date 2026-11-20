package com.familyagent.module.agent.harness.provenance;

import com.familyagent.module.agent.harness.AgentRunContext;
import com.familyagent.module.agent.harness.AgentToolDescriptor;
import com.familyagent.module.agent.harness.entity.AgentToolCallRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentRecordProvenanceWriter {

    private final AgentRecordProvenanceRepository repository;

    public void recordCreatedOutput(
            AgentRunContext context,
            AgentToolDescriptor descriptor,
            AgentToolCallRecord toolCall,
            Object output) {
        if (!(output instanceof AgentCreatedRecordOutput created)
                || context == null
                || context.runId() == null
                || descriptor == null
                || toolCall == null
                || toolCall.getId() == null
                || created.recordId() == null) {
            return;
        }
        AgentRecordProvenance provenance = new AgentRecordProvenance();
        provenance.setFamilyId(context.familyId());
        provenance.setAgentRunId(context.runId());
        provenance.setToolCallId(toolCall.getId());
        provenance.setToolName(descriptor.name());
        provenance.setToolVersion(descriptor.version());
        provenance.setRecordType(created.recordType().name());
        provenance.setMemoryEntryId(created.recordId());
        repository.insert(provenance);
    }
}
