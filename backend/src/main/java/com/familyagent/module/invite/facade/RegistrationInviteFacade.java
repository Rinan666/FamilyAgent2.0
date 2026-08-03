package com.familyagent.module.invite.facade;

import com.familyagent.common.constant.EntityStatus;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.module.invite.entity.InviteCode;
import com.familyagent.module.invite.repository.InviteCodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class RegistrationInviteFacade {

    private final InviteCodeRepository inviteCodeRepository;

    public RegistrationInviteDetails consume(String rawInviteCode) {
        String normalizedCode = normalize(rawInviteCode);
        InviteCode inviteCode = requireUsable(inviteCodeRepository.findByCode(normalizedCode));

        if (inviteCodeRepository.incrementUsedCountByCode(normalizedCode) == 0) {
            resolveConsumptionFailure(normalizedCode);
        }
        return new RegistrationInviteDetails(inviteCode.getCode(), inviteCode.getSource());
    }

    private String normalize(String rawInviteCode) {
        if (rawInviteCode == null || rawInviteCode.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.INVITE_CODE_REQUIRED);
        }
        return rawInviteCode.trim().toUpperCase();
    }

    private InviteCode requireUsable(InviteCode inviteCode) {
        if (inviteCode == null || !EntityStatus.ACTIVE.name().equals(inviteCode.getStatus())) {
            throw new BusinessException(ErrorCode.INVITE_CODE_INVALID);
        }
        if (inviteCode.getExpiresAt() != null && !inviteCode.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.INVITE_CODE_INVALID);
        }
        if (inviteCode.getMaxUses() != null && inviteCode.getUsedCount() != null
                && inviteCode.getUsedCount() >= inviteCode.getMaxUses()) {
            throw new BusinessException(ErrorCode.INVITE_CODE_EXHAUSTED);
        }
        return inviteCode;
    }

    private void resolveConsumptionFailure(String normalizedCode) {
        InviteCode latest = inviteCodeRepository.findByCode(normalizedCode);
        if (latest == null || !EntityStatus.ACTIVE.name().equals(latest.getStatus())) {
            throw new BusinessException(ErrorCode.INVITE_CODE_INVALID);
        }
        if (latest.getExpiresAt() != null && !latest.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.INVITE_CODE_INVALID);
        }
        throw new BusinessException(ErrorCode.INVITE_CODE_EXHAUSTED);
    }
}
