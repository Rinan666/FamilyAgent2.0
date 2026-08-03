package com.familyagent.module.invite.facade;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.module.invite.entity.InviteCode;
import com.familyagent.module.invite.repository.InviteCodeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrationInviteFacadeTest {

    @Mock private InviteCodeRepository inviteCodeRepository;
    @InjectMocks private RegistrationInviteFacade facade;

    @Test
    void consumeReturnsNormalizedInviteDetails() {
        InviteCode invite = invite("LOCAL-CODE", 5, 1);
        when(inviteCodeRepository.findByCode("LOCAL-CODE")).thenReturn(invite);
        when(inviteCodeRepository.incrementUsedCountByCode("LOCAL-CODE")).thenReturn(1);

        RegistrationInviteDetails result = facade.consume(" local-code ");

        assertEquals("LOCAL-CODE", result.code());
        assertEquals("test-source", result.source());
    }

    @Test
    void consumeRejectsMissingCode() {
        BusinessException error = assertThrows(BusinessException.class, () -> facade.consume(" "));

        assertEquals(ErrorCode.INVITE_CODE_REQUIRED.getCode(), error.getCode());
        verify(inviteCodeRepository, never()).findByCode(" ");
    }

    @Test
    void consumeRejectsExpiredCodeBeforeIncrement() {
        InviteCode invite = invite("EXPIRED", 5, 1);
        invite.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(inviteCodeRepository.findByCode("EXPIRED")).thenReturn(invite);

        BusinessException error = assertThrows(BusinessException.class, () -> facade.consume("EXPIRED"));

        assertEquals(ErrorCode.INVITE_CODE_INVALID.getCode(), error.getCode());
        verify(inviteCodeRepository, never()).incrementUsedCountByCode("EXPIRED");
    }

    @Test
    void consumeRejectsExhaustedCodeBeforeIncrement() {
        InviteCode invite = invite("FULL", 2, 2);
        when(inviteCodeRepository.findByCode("FULL")).thenReturn(invite);

        BusinessException error = assertThrows(BusinessException.class, () -> facade.consume("FULL"));

        assertEquals(ErrorCode.INVITE_CODE_EXHAUSTED.getCode(), error.getCode());
        verify(inviteCodeRepository, never()).incrementUsedCountByCode("FULL");
    }

    @Test
    void consumeReportsConcurrentExhaustion() {
        InviteCode invite = invite("RACE", 2, 1);
        InviteCode latest = invite("RACE", 2, 2);
        when(inviteCodeRepository.findByCode("RACE")).thenReturn(invite, latest);
        when(inviteCodeRepository.incrementUsedCountByCode("RACE")).thenReturn(0);

        BusinessException error = assertThrows(BusinessException.class, () -> facade.consume("RACE"));

        assertEquals(ErrorCode.INVITE_CODE_EXHAUSTED.getCode(), error.getCode());
    }

    private InviteCode invite(String code, Integer maxUses, Integer usedCount) {
        InviteCode invite = new InviteCode();
        invite.setCode(code);
        invite.setSource("test-source");
        invite.setStatus("ACTIVE");
        invite.setMaxUses(maxUses);
        invite.setUsedCount(usedCount);
        return invite;
    }
}
