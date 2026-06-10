package com.familyagent.module.family.service;

import cn.hutool.core.util.RandomUtil;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.module.family.dto.CreateFamilyRequest;
import com.familyagent.module.family.dto.FamilyMemberVO;
import com.familyagent.module.family.entity.Family;
import com.familyagent.module.family.entity.FamilyMember;
import com.familyagent.module.family.entity.FamilyRelationship;
import com.familyagent.module.family.repository.FamilyMemberRepository;
import com.familyagent.module.family.repository.FamilyRelationshipRepository;
import com.familyagent.module.family.repository.FamilyRepository;
import com.familyagent.module.user.entity.User;
import com.familyagent.module.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Family service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FamilyService {

    private final FamilyRepository familyRepository;
    private final FamilyMemberRepository memberRepository;
    private final FamilyRelationshipRepository relationshipRepository;
    private final FamilyLifecycleService familyLifecycleService;
    private final UserService userService;
    private static final Set<String> MUTABLE_FAMILY_ROLES = Set.of("MEMBER");

    @Transactional
    public Family createFamily(CreateFamilyRequest request) {
        User currentUser = userService.getCurrentUser();

        Family family = new Family();
        family.setName(request.getName());
        family.setDescription(request.getDescription());
        family.setCreatedBy(currentUser.getId());
        family.setMaxMembers(20);
        family.setInviteCode(generateInviteCode());
        familyRepository.insert(family);

        // Add the creator as the initial owner.
        FamilyMember member = new FamilyMember();
        member.setFamilyId(family.getId());
        member.setUserId(currentUser.getId());
        member.setRole("OWNER");
        memberRepository.insert(member);

        log.info("Family created: name={}, id={}, owner={}", family.getName(), family.getId(), currentUser.getId());
        return family;
    }

    @Transactional
    public FamilyMember joinFamily(String inviteCode) {
        User currentUser = userService.getCurrentUser();

        Family family = familyRepository.findByInviteCode(inviteCode);
        if (family == null) {
            throw new BusinessException(ErrorCode.INVALID_INVITE_CODE);
        }

        // Reject duplicate memberships.
        FamilyMember existing = memberRepository.findByFamilyAndUser(family.getId(), currentUser.getId());
        if (existing != null) {
            throw new BusinessException(ErrorCode.ALREADY_MEMBER);
        }

        // Enforce the family size limit.
        int count = memberRepository.countByFamilyId(family.getId());
        if (count >= family.getMaxMembers()) {
            throw new BusinessException(ErrorCode.FAMILY_FULL);
        }

        FamilyMember member = new FamilyMember();
        member.setFamilyId(family.getId());
        member.setUserId(currentUser.getId());
        member.setRole("MEMBER");
        memberRepository.insert(member);

        log.info("User joined family: userId={}, familyId={}, role=MEMBER", currentUser.getId(), family.getId());
        return member;
    }

    public Family getFamily(Long familyId) {
        Family family = familyRepository.selectById(familyId);
        if (family == null) {
            throw new BusinessException(ErrorCode.FAMILY_NOT_FOUND);
        }
        // Ensure the current user belongs to the family.
        checkMembership(familyId);
        return family;
    }

    public List<Family> getMyFamilies() {
        User currentUser = userService.getCurrentUser();
        List<FamilyMember> memberships = memberRepository.findByUserId(currentUser.getId());
        List<Long> familyIds = memberships.stream().map(FamilyMember::getFamilyId).toList();
        if (familyIds.isEmpty()) {
            return List.of();
        }
        return familyRepository.findBasicByIds(familyIds);
    }

    public List<FamilyMemberVO> getMembers(Long familyId) {
        User currentUser = userService.getCurrentUser();
        checkMembership(familyId);
        List<FamilyMemberVO> members = memberRepository.findMemberViewsByFamilyId(familyId);
        attachRelationshipLabels(familyId, currentUser.getId(), members);
        return members;
    }

    public void attachRelationshipLabels(Long familyId, Long viewerUserId, List<FamilyMemberVO> members) {
        if (members == null || members.isEmpty()) {
            return;
        }
        Map<Long, FamilyRelationship> relationships = relationshipRepository
                .findByFamilyAndViewer(familyId, viewerUserId)
                .stream()
                .collect(Collectors.toMap(FamilyRelationship::getToUserId, Function.identity(), (left, right) -> left));
        for (FamilyMemberVO member : members) {
            FamilyRelationship relationship = relationships.get(member.getUserId());
            if (relationship != null) {
                member.setRelationshipLabel(relationship.getLabel());
                member.setReverseRelationshipLabel(relationship.getReverseLabel());
            }
        }
    }

    @Transactional
    public FamilyMemberVO updateMemberRole(Long familyId, Long userId, String role) {
        String nextRole = normalizeFamilyRole(role);
        User currentUser = userService.getCurrentUser();
        FamilyMember currentMember = memberRepository.findByFamilyAndUser(familyId, currentUser.getId());
        boolean platformAdmin = "ADMIN".equalsIgnoreCase(currentUser.getRole());
        if (!platformAdmin && (currentMember == null || !isOwner(currentMember.getRole()))) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_PERMISSION);
        }

        FamilyMember targetMember = memberRepository.findByFamilyAndUser(familyId, userId);
        if (targetMember == null) {
            throw new BusinessException(ErrorCode.NOT_FAMILY_MEMBER);
        }
        if (currentUser.getId().equals(userId)) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_PERMISSION, "不能修改自己的家庭角色");
        }
        if ("OWNER".equalsIgnoreCase(targetMember.getRole())) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_PERMISSION, "创建者角色不能在此处修改");
        }
        targetMember.setRole(nextRole);
        memberRepository.updateById(targetMember);
        log.info("Family member role updated: familyId={}, operator={}, target={}, role={}",
                familyId, currentUser.getId(), userId, nextRole);
        FamilyMemberVO updated = memberRepository.findMemberViewByFamilyAndUser(familyId, userId);
        attachRelationshipLabels(familyId, currentUser.getId(), List.of(updated));
        return updated;
    }


    public void checkMembership(Long familyId) {
        User currentUser = userService.getCurrentUser();
        FamilyMember member = memberRepository.findByFamilyAndUser(familyId, currentUser.getId());
        if (member == null) {
            throw new BusinessException(ErrorCode.NOT_FAMILY_MEMBER);
        }
    }

    public void checkOwner(Long familyId) {
        User currentUser = userService.getCurrentUser();
        FamilyMember member = memberRepository.findByFamilyAndUser(familyId, currentUser.getId());
        if (member == null || !isOwner(member.getRole())) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_PERMISSION);
        }
    }

    @Transactional
    public void transferOwner(Long familyId, Long targetUserId) {
        User currentUser = userService.getCurrentUser();
        Family family = familyRepository.selectById(familyId);
        if (family == null) {
            throw new BusinessException(ErrorCode.FAMILY_NOT_FOUND);
        }

        boolean platformAdmin = "ADMIN".equalsIgnoreCase(currentUser.getRole());
        if (!platformAdmin) {
            FamilyMember currentMember = memberRepository.findByFamilyAndUser(familyId, currentUser.getId());
            if (currentMember == null || !isOwner(currentMember.getRole())) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_PERMISSION);
            }
        }

        familyLifecycleService.transferOwner(familyId, targetUserId, currentUser.getId());
    }

    private String normalizeFamilyRole(String role) {
        String normalized = role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
        if ("ADMIN".equals(normalized) || "GUARDIAN".equals(normalized)
                || "MEMBER".equals(normalized) || "GUEST".equals(normalized)) {
            return "MEMBER";
        }
        if (!MUTABLE_FAMILY_ROLES.contains(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "家庭角色只能是 MEMBER，创建者角色不能在此处修改");
        }
        return normalized;
    }

    private boolean isOwner(String role) {
        return "OWNER".equalsIgnoreCase(role);
    }


    private String generateInviteCode() {
        String code;
        do {
            code = RandomUtil.randomString(8).toUpperCase();
        } while (familyRepository.findByInviteCode(code) != null);
        return code;
    }
}
