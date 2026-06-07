package com.familyagent.module.family.service;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.family.dto.FamilyRelationshipVO;
import com.familyagent.module.family.dto.UpsertFamilyRelationshipRequest;
import com.familyagent.module.family.entity.FamilyMember;
import com.familyagent.module.family.entity.FamilyRelationship;
import com.familyagent.module.family.repository.FamilyMemberRepository;
import com.familyagent.module.family.repository.FamilyRelationshipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FamilyRelationshipService {

    private final FamilyService familyService;
    private final FamilyMemberRepository memberRepository;
    private final FamilyRelationshipRepository relationshipRepository;

    public List<FamilyRelationshipVO> listMyLabels(Long familyId) {
        Long viewerUserId = CurrentUserGuard.currentUserId();
        familyService.checkMembership(familyId);
        return relationshipRepository.findByFamilyAndViewer(familyId, viewerUserId)
                .stream()
                .map(this::toVO)
                .toList();
    }

    @Transactional
    public FamilyRelationshipVO upsertMyLabel(
            Long familyId,
            Long targetUserId,
            UpsertFamilyRelationshipRequest request) {
        Long viewerUserId = CurrentUserGuard.currentUserId();
        familyService.checkMembership(familyId);

        if (viewerUserId.equals(targetUserId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不能给自己设置亲属称呼");
        }

        FamilyMember targetMember = memberRepository.findByFamilyAndUser(familyId, targetUserId);
        if (targetMember == null) {
            throw new BusinessException(ErrorCode.NOT_FAMILY_MEMBER, "目标成员不属于当前家族");
        }

        String label = normalizeRequired(request == null ? null : request.getLabel(), 60, "称呼不能为空");
        String reverseLabel = normalizeOptional(request == null ? null : request.getReverseLabel(), 60);
        String note = normalizeOptional(request == null ? null : request.getNote(), 500);

        FamilyRelationship relationship = relationshipRepository.findByFamilyViewerAndTarget(
                familyId,
                viewerUserId,
                targetUserId);
        LocalDateTime now = LocalDateTime.now();
        if (relationship == null) {
            relationship = new FamilyRelationship();
            relationship.setFamilyId(familyId);
            relationship.setFromUserId(viewerUserId);
            relationship.setToUserId(targetUserId);
            relationship.setCreatedBy(viewerUserId);
            relationship.setCreatedAt(now);
        }

        relationship.setLabel(label);
        relationship.setReverseLabel(reverseLabel);
        relationship.setNote(note);
        relationship.setUpdatedBy(viewerUserId);
        relationship.setUpdatedAt(now);

        if (relationship.getId() == null) {
            relationshipRepository.insert(relationship);
        } else {
            relationshipRepository.updateById(relationship);
        }
        return toVO(relationship);
    }

    private String normalizeRequired(String value, int maxLength, String message) {
        String text = value == null ? "" : value.trim();
        if (text.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, message);
        }
        if (text.length() > maxLength) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "称呼过长");
        }
        return text;
    }

    private String normalizeOptional(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        if (text.isEmpty()) {
            return null;
        }
        if (text.length() > maxLength) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "内容过长");
        }
        return text;
    }

    private FamilyRelationshipVO toVO(FamilyRelationship relationship) {
        return FamilyRelationshipVO.builder()
                .id(relationship.getId())
                .familyId(relationship.getFamilyId())
                .fromUserId(relationship.getFromUserId())
                .toUserId(relationship.getToUserId())
                .label(relationship.getLabel())
                .reverseLabel(relationship.getReverseLabel())
                .note(relationship.getNote())
                .createdAt(relationship.getCreatedAt())
                .updatedAt(relationship.getUpdatedAt())
                .build();
    }
}
