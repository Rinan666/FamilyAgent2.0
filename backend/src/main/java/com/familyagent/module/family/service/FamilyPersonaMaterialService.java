package com.familyagent.module.family.service;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.family.dto.PersonaMaterialVO;
import com.familyagent.module.family.dto.UpsertPersonaMaterialRequest;
import com.familyagent.module.family.entity.FamilyPersonaMaterial;
import com.familyagent.module.family.repository.FamilyPersonaMaterialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FamilyPersonaMaterialService {

    private final FamilyPersonaMaterialRepository repository;
    private final FamilyPersonaMemberQueryService personaQueryService;
    private final FamilyPersonaMaterialAssembler assembler;
    private final FamilyService familyService;

    public List<PersonaMaterialVO> list(Long familyId, Long personaId) {
        familyService.checkMembership(familyId);
        personaQueryService.requireEntity(familyId, personaId);
        return repository.findByPersonaId(familyId, personaId)
                .stream()
                .map(assembler::toVO)
                .toList();
    }

    @Transactional
    public PersonaMaterialVO create(Long familyId, Long personaId, UpsertPersonaMaterialRequest request) {
        familyService.checkOwner(familyId);
        personaQueryService.requireEntity(familyId, personaId);

        FamilyPersonaMaterial entity = new FamilyPersonaMaterial();
        entity.setFamilyId(familyId);
        entity.setPersonaId(personaId);
        entity.setTitle(requireText(request.getTitle(), "Material title is required."));
        entity.setContent(requireText(request.getContent(), "Material content is required."));
        entity.setTags(normalizeTags(request.getTags()));
        entity.setCreatedBy(CurrentUserGuard.currentUserId());
        repository.insert(entity);
        return assembler.toVO(entity);
    }

    @Transactional
    public PersonaMaterialVO update(Long familyId, Long personaId, Long materialId, UpsertPersonaMaterialRequest request) {
        familyService.checkOwner(familyId);
        personaQueryService.requireEntity(familyId, personaId);
        FamilyPersonaMaterial entity = requireMaterial(familyId, personaId, materialId);

        entity.setTitle(requireText(request.getTitle(), "Material title is required."));
        entity.setContent(requireText(request.getContent(), "Material content is required."));
        entity.setTags(normalizeTags(request.getTags()));
        repository.updateById(entity);
        return assembler.toVO(entity);
    }

    @Transactional
    public void delete(Long familyId, Long personaId, Long materialId) {
        familyService.checkOwner(familyId);
        personaQueryService.requireEntity(familyId, personaId);
        FamilyPersonaMaterial entity = requireMaterial(familyId, personaId, materialId);
        repository.deleteById(entity.getId());
    }

    @Transactional
    public void deleteByPersona(Long familyId, Long personaId) {
        repository.deleteByPersonaId(familyId, personaId);
    }

    public boolean hasMaterial(Long familyId, Long personaId) {
        return repository.countByPersonaId(familyId, personaId) > 0;
    }

    private FamilyPersonaMaterial requireMaterial(Long familyId, Long personaId, Long materialId) {
        FamilyPersonaMaterial entity = repository.findByIdAndPersonaId(materialId, familyId, personaId);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "精神成员材料不存在");
        }
        return entity;
    }

    private String requireText(String value, String message) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, message);
        }
        return text;
    }

    private String[] normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return new String[0];
        }
        return tags.stream()
                .filter(tag -> tag != null && !tag.isBlank())
                .map(String::trim)
                .distinct()
                .limit(12)
                .toArray(String[]::new);
    }
}
