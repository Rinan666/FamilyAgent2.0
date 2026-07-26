package com.familyagent.module.agent.harness.provenance;

import com.familyagent.common.constant.MemoryOriginType;
import com.familyagent.module.memory.facade.UnifiedMemoryIdentityFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AgentRecordProvenanceQueryService {

    private final AgentRecordProvenanceRepository repository;
    private final UnifiedMemoryIdentityFacade memoryIdentityFacade;

    @Transactional(readOnly = true)
    public Optional<AgentRecordProvenanceView> find(
            AgentCreatedRecordType recordType,
            Long recordId) {
        if (recordType == null || recordId == null) {
            return Optional.empty();
        }
        Long memoryEntryId = resolveMemoryEntryId(recordType, recordId);
        if (memoryEntryId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(repository.findByRecord(recordType, memoryEntryId))
                .map(record -> new AgentRecordProvenanceView(
                        recordType,
                        recordId,
                        record.getAgentRunId(),
                        record.getToolCallId(),
                        record.getToolName(),
                        record.getToolVersion(),
                        record.getCreatedAt()));
    }

    private Long resolveMemoryEntryId(AgentCreatedRecordType type, Long recordId) {
        return switch (type) {
            case MEMORY_ENTRY -> recordId;
            case DIARY_ENTRY -> memoryIdentityFacade.findMemoryEntryId(MemoryOriginType.DIARY, recordId);
            case GROWTH_GUARD_RECORD -> memoryIdentityFacade.findMemoryEntryId(MemoryOriginType.GROWTH, recordId);
        };
    }
}
