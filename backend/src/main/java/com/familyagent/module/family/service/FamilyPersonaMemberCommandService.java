package com.familyagent.module.family.service;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.lifecycle.PersonaScopedResourceCleaner;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.family.dto.CreatePersonaMemberRequest;
import com.familyagent.module.family.dto.DeletePersonaMemberRequest;
import com.familyagent.module.family.dto.PersonaMemberVO;
import com.familyagent.module.family.dto.UpdatePersonaMemberRequest;
import com.familyagent.module.family.entity.FamilyPersonaMember;
import com.familyagent.module.family.repository.FamilyRepository;
import com.familyagent.module.family.repository.FamilyPersonaMemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FamilyPersonaMemberCommandService {

    private static final int MAX_PERSONA_MEMBERS = 3;
    private static final String REQUIRED_CONFIRMATION_WORD = "确认删除";

    private final FamilyPersonaMemberRepository repository;
    private final FamilyPersonaMemberQueryService queryService;
    private final FamilyPersonaMemberAssembler assembler;
    private final FamilyPersonaMaterialService materialService;
    private final FamilyService familyService;
    private final FamilyRepository familyRepository;
    private final List<PersonaScopedResourceCleaner> personaResourceCleaners;

    @Transactional
    public PersonaMemberVO create(Long familyId, CreatePersonaMemberRequest request) {
        familyService.checkOwner(familyId);
        familyRepository.lockById(familyId);

        int count = repository.countByFamilyId(familyId);
        if (count >= MAX_PERSONA_MEMBERS) {
            throw new BusinessException(ErrorCode.PERSONA_MEMBER_LIMIT_EXCEEDED,
                    "Each family can have at most " + MAX_PERSONA_MEMBERS + " persona members. Please delete one first.");
        }

        FamilyPersonaMember entity = new FamilyPersonaMember();
        entity.setFamilyId(familyId);
        entity.setName(requireName(request.getName()));
        entity.setDescription(cleanOptional(request.getDescription()));
        entity.setEraIdentity(cleanOptional(request.getEraIdentity()));
        entity.setValues(cleanOptional(request.getValues()));
        entity.setSpeakingStyle(cleanOptional(request.getSpeakingStyle()));
        entity.setPersonality(cleanOptional(request.getPersonality()));
        entity.setCreatedBy(CurrentUserGuard.currentUserId());
        repository.insert(entity);

        log.info("PersonaMember created: familyId={}, personaId={}, name={}", familyId, entity.getId(), entity.getName());
        return assembler.toVO(entity);
    }

    @Transactional
    public PersonaMemberVO update(Long familyId, Long personaId, UpdatePersonaMemberRequest request) {
        familyService.checkOwner(familyId);
        FamilyPersonaMember entity = queryService.requireEntity(familyId, personaId);

        if (request.getName() != null) {
            entity.setName(requireName(request.getName()));
        }
        if (request.getDescription() != null) {
            entity.setDescription(cleanOptional(request.getDescription()));
        }
        if (request.getEraIdentity() != null) {
            entity.setEraIdentity(cleanOptional(request.getEraIdentity()));
        }
        if (request.getValues() != null) {
            entity.setValues(cleanOptional(request.getValues()));
        }
        if (request.getSpeakingStyle() != null) {
            entity.setSpeakingStyle(cleanOptional(request.getSpeakingStyle()));
        }
        if (request.getPersonality() != null) {
            entity.setPersonality(cleanOptional(request.getPersonality()));
        }
        repository.updateById(entity);

        log.info("PersonaMember updated: familyId={}, personaId={}", familyId, personaId);
        return assembler.toVO(entity);
    }

    @Transactional
    public void delete(Long familyId, Long personaId, DeletePersonaMemberRequest request) {
        familyService.checkOwner(familyId);

        if (!REQUIRED_CONFIRMATION_WORD.equals(cleanOptional(request.getConfirmationWord()))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "Invalid confirmation word. Please type the required confirmation.");
        }

        queryService.requireEntity(familyId, personaId);
        personaResourceCleaners.forEach(cleaner -> cleaner.cleanPersonaResources(familyId, personaId));
        materialService.deleteByPersona(familyId, personaId);
        repository.deleteByIdAndFamilyId(personaId, familyId);

        log.info("PersonaMember deleted: familyId={}, personaId={}", familyId, personaId);
    }

    private String requireName(String value) {
        String normalized = cleanOptional(value);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Persona member name is required.");
        }
        return normalized;
    }

    private String cleanOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
