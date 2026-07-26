package com.familyagent.module.memory.facade;

import com.familyagent.module.memory.dto.CreatePersonalMemoryRequest;
import com.familyagent.module.memory.dto.PersonalMemoryView;
import com.familyagent.module.memory.service.PersonalMemoryCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentPersonalMemoryFacade {

    private final PersonalMemoryCommandService commandService;

    public PersonalMemoryView create(CreatePersonalMemoryRequest request) {
        return commandService.create(request);
    }
}
