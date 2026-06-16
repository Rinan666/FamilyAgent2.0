package com.familyagent.module.family.service;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.family.dto.CreatePersonaMemberRequest;
import com.familyagent.module.family.dto.DeletePersonaMemberRequest;
import com.familyagent.module.family.dto.PersonaMemberVO;
import com.familyagent.module.family.dto.UpdatePersonaMemberRequest;
import com.familyagent.module.family.entity.FamilyPersonaMember;
import com.familyagent.module.family.repository.FamilyPersonaMemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FamilyPersonaMemberCommandService {

    private static final int MAX_PERSONA_MEMBERS = 3;
    // "确认删除" == "确认删除"
    private static final String REQUIRED_CONFIRMATION_WORD = "确认删除";

    private final FamilyPersonaMemberRepository repository;
    private final FamilyPersonaMemberQueryService queryService;
    private final FamilyPersonaMemberAssembler assembler;
    private final FamilyService familyService;

    @Transactional
    public PersonaMemberVO create(Long familyId, CreatePersonaMemberRequest request) {
        familyService.checkOwner(familyId);

        int count = repository.countByFamilyId(familyId);
        if (count >= MAX_PERSONA_MEMBERS) {
            throw new BusinessException(ErrorCode.PERSONA_MEMBER_LIMIT_EXCEEDED,
                    "Each family can have at most " + MAX_PERSONA_MEMBERS + " persona members. Please delete one first.");
        }

        FamilyPersonaMember entity = new FamilyPersonaMember();
        entity.setFamilyId(familyId);
        entity.setName(request.getName());
        entity.setDescription(request.getDescription());
        entity.setEraIdentity(request.getEraIdentity());
        entity.setValues(request.getValues());
        entity.setSpeakingStyle(request.getSpeakingStyle());
        entity.setPersonality(request.getPersonality());
        entity.setCreatedBy(CurrentUserGuard.currentUserId());
        repository.insert(entity);

        log.info("PersonaMember created: familyId={}, personaId={}, name={}", familyId, entity.getId(), entity.getName());
        return assembler.toVO(entity);
    }

    @Transactional
    public PersonaMemberVO update(Long familyId, Long personaId, UpdatePersonaMemberRequest request) {
        familyService.checkOwner(familyId);
        FamilyPersonaMember entity = queryService.requireEntity(familyId, personaId);

        if (request.getName() != null && !request.getName().isBlank()) {
            entity.setName(request.getName().trim());
        }
        if (request.getDescription() != null) {
            entity.setDescription(request.getDescription());
        }
        if (request.getEraIdentity() != null) {
            entity.setEraIdentity(request.getEraIdentity());
        }
        if (request.getValues() != null) {
            entity.setValues(request.getValues());
        }
        if (request.getSpeakingStyle() != null) {
            entity.setSpeakingStyle(request.getSpeakingStyle());
        }
        if (request.getPersonality() != null) {
            entity.setPersonality(request.getPersonality());
        }
        repository.updateById(entity);

        log.info("PersonaMember updated: familyId={}, personaId={}", familyId, personaId);
        return assembler.toVO(entity);
    }

    @Transactional
    public void delete(Long familyId, Long personaId, DeletePersonaMemberRequest request) {
        familyService.checkOwner(familyId);

        if (!REQUIRED_CONFIRMATION_WORD.equals(request.getConfirmationWord())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "Invalid confirmation word. Please type the required confirmation.");
        }

        queryService.requireEntity(familyId, personaId);
        repository.deleteByIdAndFamilyId(personaId, familyId);

        log.info("PersonaMember deleted: familyId={}, personaId={}", familyId, personaId);
    }
}
