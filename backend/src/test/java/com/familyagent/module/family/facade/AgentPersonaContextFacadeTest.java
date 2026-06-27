package com.familyagent.module.family.facade;

import com.familyagent.module.family.dto.PersonaMaterialVO;
import com.familyagent.module.family.dto.PersonaMemberVO;
import com.familyagent.module.family.service.FamilyPersonaMaterialService;
import com.familyagent.module.family.service.FamilyPersonaMemberQueryService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentPersonaContextFacadeTest {

    private final FamilyPersonaMemberQueryService personaQueryService = mock(FamilyPersonaMemberQueryService.class);
    private final FamilyPersonaMaterialService materialService = mock(FamilyPersonaMaterialService.class);
    private final AgentPersonaContextFacade facade = new AgentPersonaContextFacade(personaQueryService, materialService);

    @Test
    void buildPersonaAgentContext_usesServerPersonaProfileAndMaterials() {
        when(personaQueryService.getById(10L, 7L)).thenReturn(PersonaMemberVO.builder()
                .id(7L)
                .familyId(10L)
                .name("Grandpa")
                .eraIdentity("old village doctor")
                .values("steady and protective")
                .speakingStyle("short and warm")
                .build());
        when(materialService.list(10L, 7L)).thenReturn(List.of(PersonaMaterialVO.builder()
                .title("Advice card")
                .content("Do the small useful thing first.")
                .build()));

        String context = facade.buildPersonaAgentContext(10L, 7L);

        verify(personaQueryService).getById(10L, 7L);
        verify(materialService).list(10L, 7L);
        assertTrue(context.contains("persona_profile:"));
        assertTrue(context.contains("name: Grandpa"));
        assertTrue(context.contains("Advice card"));
        assertTrue(context.contains("Do the small useful thing first."));
    }
}
