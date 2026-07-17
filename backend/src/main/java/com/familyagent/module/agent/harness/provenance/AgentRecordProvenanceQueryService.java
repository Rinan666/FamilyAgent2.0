package com.familyagent.module.agent.harness.provenance;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AgentRecordProvenanceQueryService {

    private final AgentRecordProvenanceRepository repository;

    @Transactional(readOnly = true)
    public Optional<AgentRecordProvenanceView> find(
            AgentCreatedRecordType recordType,
            Long recordId) {
        if (recordType == null || recordId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(repository.findByRecord(recordType, recordId))
                .map(record -> new AgentRecordProvenanceView(
                        recordType,
                        recordId,
                        record.getAgentRunId(),
                        record.getToolCallId(),
                        record.getToolName(),
                        record.getToolVersion(),
                        record.getCreatedAt()));
    }
}
