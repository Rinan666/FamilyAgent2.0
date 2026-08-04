package com.familyagent.module.family.facade;

import com.familyagent.module.family.dto.FamilyMemberVO;
import com.familyagent.module.family.dto.PersonaMemberVO;
import com.familyagent.module.family.service.FamilyPersonaMemberQueryService;
import com.familyagent.module.family.service.FamilyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class AgentContextTargetFacade {

    private final FamilyService familyService;
    private final FamilyPersonaMemberQueryService personaQueryService;

    public AgentContextTargetCatalog listAuthorizedTargets(Long familyId) {
        List<AgentContextTarget> members = familyService.getMembers(familyId).stream()
                .map(this::memberTarget)
                .toList();
        List<AgentContextTarget> personas = personaQueryService.listByFamily(familyId).stream()
                .map(this::personaTarget)
                .toList();
        return new AgentContextTargetCatalog(members, personas);
    }

    public AgentContextTarget requireMember(Long familyId, Long userId) {
        return listAuthorizedTargets(familyId).members().stream()
                .filter(target -> target.id().equals(userId))
                .findFirst()
                .orElseThrow(() -> new com.familyagent.common.exception.BusinessException(
                        com.familyagent.common.response.ErrorCode.NOT_FOUND));
    }

    public AgentContextTarget requirePersona(Long familyId, Long personaId) {
        PersonaMemberVO persona = personaQueryService.getById(familyId, personaId);
        return personaTarget(persona);
    }

    private AgentContextTarget memberTarget(FamilyMemberVO member) {
        List<String> aliases = Stream.of(
                        member.getUsername(),
                        member.getNickname(),
                        member.getRelationshipLabel(),
                        member.getReverseRelationshipLabel())
                .filter(value -> value != null && !value.isBlank())
                .toList();
        String displayName = aliases.isEmpty() ? "用户 " + member.getUserId() : aliases.get(0);
        return new AgentContextTarget(member.getUserId(), displayName, aliases);
    }

    private AgentContextTarget personaTarget(PersonaMemberVO persona) {
        return new AgentContextTarget(persona.getId(), persona.getName(), List.of(persona.getName()));
    }
}
