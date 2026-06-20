package com.familyagent.module.family.service;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.module.family.dto.PersonaMemberVO;
import com.familyagent.module.family.entity.FamilyPersonaMember;
import com.familyagent.module.family.repository.FamilyPersonaMaterialRepository;
import com.familyagent.module.family.repository.FamilyPersonaMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FamilyPersonaMemberQueryService {

    private final FamilyPersonaMemberRepository repository;
    private final FamilyPersonaMaterialRepository materialRepository;
    private final FamilyPersonaMemberAssembler assembler;
    private final FamilyService familyService;

    public List<PersonaMemberVO> listByFamily(Long familyId) {
        familyService.checkMembership(familyId);
        return repository.findByFamilyId(familyId)
                .stream()
                .map(this::toVO)
                .toList();
    }

    public PersonaMemberVO getById(Long familyId, Long personaId) {
        familyService.checkMembership(familyId);
        FamilyPersonaMember entity = repository.findByIdAndFamilyId(personaId, familyId);
        if (entity == null) {
            throw new BusinessException(ErrorCode.PERSONA_MEMBER_NOT_FOUND, "精神成员不存在");
        }
        return toVO(entity);
    }

    /** Package-private helper for loading entities without VO conversion. */
    FamilyPersonaMember requireEntity(Long familyId, Long personaId) {
        FamilyPersonaMember entity = repository.findByIdAndFamilyId(personaId, familyId);
        if (entity == null) {
            throw new BusinessException(ErrorCode.PERSONA_MEMBER_NOT_FOUND, "精神成员不存在");
        }
        return entity;
    }

    private PersonaMemberVO toVO(FamilyPersonaMember entity) {
        PersonaMemberVO vo = assembler.toVO(entity);
        vo.setHasMaterial(materialRepository.countByPersonaId(entity.getFamilyId(), entity.getId()) > 0);
        return vo;
    }
}
