package com.familyagent.module.family.service;

import cn.hutool.core.util.RandomUtil;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.module.family.dto.CreateFamilyRequest;
import com.familyagent.module.family.dto.DeleteFamilyRequest;
import com.familyagent.module.family.dto.FamilyCreationQuotaVO;
import com.familyagent.module.family.dto.FamilyMemberVO;
import com.familyagent.module.family.entity.Family;
import com.familyagent.module.family.entity.FamilyMember;
import com.familyagent.module.family.entity.FamilyRelationship;
import com.familyagent.module.family.repository.FamilyMemberRepository;
import com.familyagent.module.family.repository.FamilyRelationshipRepository;
import com.familyagent.module.family.repository.FamilyRepository;
import com.familyagent.module.user.facade.UserAccountAccess;
import com.familyagent.module.user.facade.UserAccountAccessFacade;
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

    private static final int MAX_FAMILIES_PER_USER = 3;
    private static final String FAMILY_DELETE_REASON = "FAMILY_DELETE_BY_OWNER";

    private final FamilyRepository familyRepository;
    private final FamilyMemberRepository memberRepository;
    private final FamilyRelationshipRepository relationshipRepository;
    private final FamilyLifecycleService familyLifecycleService;
    private final UserAccountAccessFacade userAccountAccessFacade;
    private static final Set<String> MUTABLE_FAMILY_ROLES = Set.of("MEMBER");

    @Transactional
    public Family createFamily(CreateFamilyRequest request) {
        UserAccountAccess currentUser = userAccountAccessFacade.requireCurrent();

        if (familyRepository.countByCreatedBy(currentUser.userId()) >= MAX_FAMILIES_PER_USER) {
            throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED,
                    "每个用户最多创建 " + MAX_FAMILIES_PER_USER + " 个家族空间");
        }

        Family family = new Family();
        family.setName(request.getName());
        family.setDescription(request.getDescription());
        family.setCreatedBy(currentUser.userId());
        family.setMaxMembers(20);
        family.setInviteCode(generateInviteCode());
        familyRepository.insert(family);

        // Add the creator as the initial owner.
        FamilyMember member = new FamilyMember();
        member.setFamilyId(family.getId());
        member.setUserId(currentUser.userId());
        member.setRole("OWNER");
        memberRepository.insert(member);

        log.info("Family created: name={}, id={}, owner={}", family.getName(), family.getId(), currentUser.userId());
        return family;
    }

    @Transactional
    public FamilyMember joinFamily(String inviteCode) {
        UserAccountAccess currentUser = userAccountAccessFacade.requireCurrent();

        Family family = familyRepository.findByInviteCode(inviteCode);
        if (family == null) {
            throw new BusinessException(ErrorCode.INVALID_INVITE_CODE);
        }

        // Reject duplicate memberships.
        FamilyMember existing = memberRepository.findByFamilyAndUser(family.getId(), currentUser.userId());
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
        member.setUserId(currentUser.userId());
        member.setRole("MEMBER");
        memberRepository.insert(member);

        log.info("User joined family: userId={}, familyId={}, role=MEMBER", currentUser.userId(), family.getId());
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
        UserAccountAccess currentUser = userAccountAccessFacade.requireCurrent();
        List<FamilyMember> memberships = memberRepository.findByUserId(currentUser.userId());
        List<Long> familyIds = memberships.stream().map(FamilyMember::getFamilyId).toList();
        if (familyIds.isEmpty()) {
            return List.of();
        }
        return familyRepository.findBasicByIds(familyIds);
    }

    public FamilyCreationQuotaVO getCreationQuota() {
        UserAccountAccess currentUser = userAccountAccessFacade.requireCurrent();
        int createdFamilies = familyRepository.countByCreatedBy(currentUser.userId());
        return FamilyCreationQuotaVO.builder()
                .maxFamilies(MAX_FAMILIES_PER_USER)
                .createdFamilies(createdFamilies)
                .remainingFamilies(Math.max(0, MAX_FAMILIES_PER_USER - createdFamilies))
                .build();
    }

    public List<FamilyMemberVO> getMembers(Long familyId) {
        UserAccountAccess currentUser = userAccountAccessFacade.requireCurrent();
        checkMembership(familyId);
        List<FamilyMemberVO> members = memberRepository.findMemberViewsByFamilyId(familyId);
        attachRelationshipLabels(familyId, currentUser.userId(), members);
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
        UserAccountAccess currentUser = userAccountAccessFacade.requireCurrent();
        FamilyMember currentMember = memberRepository.findByFamilyAndUser(familyId, currentUser.userId());
        boolean platformAdmin = currentUser.platformAdmin();
        if (!platformAdmin && (currentMember == null || !isOwner(currentMember.getRole()))) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_PERMISSION);
        }

        FamilyMember targetMember = memberRepository.findByFamilyAndUser(familyId, userId);
        if (targetMember == null) {
            throw new BusinessException(ErrorCode.NOT_FAMILY_MEMBER);
        }
        if (currentUser.userId().equals(userId)) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_PERMISSION, "不能修改自己的家庭角色");
        }
        if ("OWNER".equalsIgnoreCase(targetMember.getRole())) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_PERMISSION, "创建者角色不能在此处修改");
        }
        targetMember.setRole(nextRole);
        memberRepository.updateById(targetMember);
        log.info("Family member role updated: familyId={}, operator={}, target={}, role={}",
                familyId, currentUser.userId(), userId, nextRole);
        FamilyMemberVO updated = memberRepository.findMemberViewByFamilyAndUser(familyId, userId);
        attachRelationshipLabels(familyId, currentUser.userId(), List.of(updated));
        return updated;
    }


    public void checkMembership(Long familyId) {
        UserAccountAccess currentUser = userAccountAccessFacade.requireCurrent();
        FamilyMember member = memberRepository.findByFamilyAndUser(familyId, currentUser.userId());
        if (member == null) {
            throw new BusinessException(ErrorCode.NOT_FAMILY_MEMBER);
        }
    }

    /**
     * Look up a family member by family and user id.
     * Throws {@link BusinessException} with {@code NOT_FAMILY_MEMBER} when the user is not in the family.
     * This is the supported external API — other modules should call this instead of dipping into
     * {@code FamilyMemberRepository} directly.
     */
    public FamilyMember getFamilyMember(Long familyId, Long userId) {
        FamilyMember member = memberRepository.findByFamilyAndUser(familyId, userId);
        if (member == null) {
            throw new BusinessException(ErrorCode.NOT_FAMILY_MEMBER);
        }
        return member;
    }

    /**
     * Look up a family member view (enriched DTO) by family and user id.
     * Throws {@link BusinessException} with {@code NOT_FAMILY_MEMBER} when the user is not in the family.
     */
    public FamilyMemberVO getMemberView(Long familyId, Long userId) {
        FamilyMemberVO view = memberRepository.findMemberViewByFamilyAndUser(familyId, userId);
        if (view == null) {
            throw new BusinessException(ErrorCode.NOT_FAMILY_MEMBER);
        }
        return view;
    }

    /**
     * Admin-facing: list all member views for a family without requiring the caller to be a member.
     * The caller must enforce platform-admin access separately. Throws
     * {@link BusinessException} with {@code FAMILY_NOT_FOUND} if the family does not exist.
     */
    public List<FamilyMemberVO> listMemberViewsForAdmin(Long familyId) {
        if (familyRepository.selectById(familyId) == null) {
            throw new BusinessException(ErrorCode.FAMILY_NOT_FOUND);
        }
        return memberRepository.findMemberViewsByFamilyId(familyId);
    }

    public void checkOwner(Long familyId) {
        UserAccountAccess currentUser = userAccountAccessFacade.requireCurrent();
        FamilyMember member = memberRepository.findByFamilyAndUser(familyId, currentUser.userId());
        if (member == null || !isOwner(member.getRole())) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_PERMISSION);
        }
    }

    @Transactional
    public void transferOwner(Long familyId, Long targetUserId) {
        UserAccountAccess currentUser = userAccountAccessFacade.requireCurrent();
        Family family = familyRepository.selectById(familyId);
        if (family == null) {
            throw new BusinessException(ErrorCode.FAMILY_NOT_FOUND);
        }

        boolean platformAdmin = currentUser.platformAdmin();
        if (!platformAdmin) {
            FamilyMember currentMember = memberRepository.findByFamilyAndUser(familyId, currentUser.userId());
            if (currentMember == null || !isOwner(currentMember.getRole())) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_PERMISSION);
            }
        }

        familyLifecycleService.transferOwner(familyId, targetUserId, currentUser.userId());
    }

    @Transactional
    public void deleteFamily(Long familyId, DeleteFamilyRequest request) {
        UserAccountAccess currentUser = userAccountAccessFacade.requireCurrent();
        Family family = familyRepository.selectById(familyId);
        if (family == null) {
            throw new BusinessException(ErrorCode.FAMILY_NOT_FOUND);
        }

        boolean platformAdmin = currentUser.platformAdmin();
        FamilyMember currentMember = memberRepository.findByFamilyAndUser(familyId, currentUser.userId());
        if (!platformAdmin && (currentMember == null || !isOwner(currentMember.getRole()))) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_PERMISSION);
        }

        String expectedName = family.getName() == null ? "" : family.getName().trim();
        String confirmationName = request.getConfirmationName() == null ? "" : request.getConfirmationName().trim();
        if (!request.isDeleteAllData() || !expectedName.equals(confirmationName)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Family deletion confirmation does not match");
        }

        familyLifecycleService.dissolveFamily(familyId, FAMILY_DELETE_REASON);
        log.info("Family delete requested: familyId={}, operatorUserId={}, platformAdmin={}",
                familyId, currentUser.userId(), platformAdmin);
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
