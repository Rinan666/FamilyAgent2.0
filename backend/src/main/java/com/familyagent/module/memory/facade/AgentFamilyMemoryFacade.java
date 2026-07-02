package com.familyagent.module.memory.facade;

import com.familyagent.module.memory.dto.CreateFamilyMemoryRequest;
import com.familyagent.module.memory.entity.MemoryEntry;
import com.familyagent.module.memory.service.MemoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentFamilyMemoryFacade {

    private final MemoryService memoryService;

    public MemoryEntry create(CreateFamilyMemoryRequest request) {
        return memoryService.createFamilyMemory(request);
    }
}
