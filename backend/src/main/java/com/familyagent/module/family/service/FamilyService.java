package com.familyagent.module.family.service;

import cn.hutool.core.util.RandomUtil;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.module.family.dto.CreateFamilyRequest;
import com.familyagent.module.family.entity.Family;
import com.familyagent.module.family.entity.FamilyMember;
import com.familyagent.module.family.repository.FamilyMemberRepository;
import com.familyagent.module.family.repository.FamilyRepository;
import com.familyagent.module.user.entity.User;
import com.familyagent.module.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 家族服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FamilyService {

    private final FamilyRepository familyRepository;
    private final FamilyMemberRepository memberRepository;
    private final UserService userService;

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

        // 创建者为族长
        FamilyMember member = new FamilyMember();
        member.setFamilyId(family.getId());
        member.setUserId(currentUser.getId());
        member.setRole("OWNER");
        memberRepository.insert(member);

        log.info("家族创建成功: name={}, id={}, owner={}", family.getName(), family.getId(), currentUser.getId());
        return family;
    }

    @Transactional
    public FamilyMember joinFamily(String inviteCode) {
        User currentUser = userService.getCurrentUser();

        Family family = familyRepository.findByInviteCode(inviteCode);
        if (family == null) {
            throw new BusinessException(ErrorCode.INVALID_INVITE_CODE);
        }

        // 检查是否已是成员
        FamilyMember existing = memberRepository.findByFamilyAndUser(family.getId(), currentUser.getId());
        if (existing != null) {
            throw new BusinessException(ErrorCode.ALREADY_MEMBER);
        }

        // 检查人数上限
        int count = memberRepository.countByFamilyId(family.getId());
        if (count >= family.getMaxMembers()) {
            throw new BusinessException(ErrorCode.FAMILY_FULL);
        }

        FamilyMember member = new FamilyMember();
        member.setFamilyId(family.getId());
        member.setUserId(currentUser.getId());
        member.setRole("MEMBER");
        memberRepository.insert(member);

        log.info("用户加入家族: userId={}, familyId={}, role=MEMBER", currentUser.getId(), family.getId());
        return member;
    }

    public Family getFamily(Long familyId) {
        Family family = familyRepository.selectById(familyId);
        if (family == null) {
            throw new BusinessException(ErrorCode.FAMILY_NOT_FOUND);
        }
        // 验证是否为成员
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

    public List<FamilyMember> getMembers(Long familyId) {
        checkMembership(familyId);
        return memberRepository.findByFamilyId(familyId);
    }

    public void checkMembership(Long familyId) {
        User currentUser = userService.getCurrentUser();
        FamilyMember member = memberRepository.findByFamilyAndUser(familyId, currentUser.getId());
        if (member == null) {
            throw new BusinessException(ErrorCode.NOT_FAMILY_MEMBER);
        }
    }

    public void checkOwnerOrAdmin(Long familyId) {
        User currentUser = userService.getCurrentUser();
        FamilyMember member = memberRepository.findByFamilyAndUser(familyId, currentUser.getId());
        if (member == null || (!"OWNER".equals(member.getRole()) && !"ADMIN".equals(member.getRole()))) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_PERMISSION);
        }
    }

    private String generateInviteCode() {
        String code;
        do {
            code = RandomUtil.randomString(8).toUpperCase();
        } while (familyRepository.findByInviteCode(code) != null);
        return code;
    }
}
