package com.familyagent.module.user.facade;

import cn.dev33.satoken.stp.StpUtil;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.module.user.entity.User;
import com.familyagent.module.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAccountAccessFacadeTest {

    @Mock private UserRepository userRepository;
    @InjectMocks private UserAccountAccessFacade facade;

    @Test
    void findByIdReturnsEmptyWhenUserDoesNotExist() {
        when(userRepository.findBasicById(9L)).thenReturn(null);

        Optional<UserAccountAccess> result = facade.findById(9L);

        assertTrue(result.isEmpty());
    }

    @Test
    void findByIdMapsPlatformAdminRoleCaseInsensitively() {
        User user = new User();
        user.setRole("admin");
        when(userRepository.findBasicById(1L)).thenReturn(user);

        UserAccountAccess result = facade.findById(1L).orElseThrow();

        assertEquals(1L, result.userId());
        assertTrue(result.platformAdmin());
    }

    @Test
    void findByIdTreatsRegularUserAsNonAdmin() {
        User user = new User();
        user.setRole("USER");
        when(userRepository.findBasicById(2L)).thenReturn(user);

        UserAccountAccess result = facade.findById(2L).orElseThrow();

        assertFalse(result.platformAdmin());
    }

    @Test
    void requireCurrentReturnsAuthenticatedAccount() {
        User user = new User();
        user.setRole("USER");
        when(userRepository.findBasicById(7L)).thenReturn(user);

        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(7L);

            UserAccountAccess result = facade.requireCurrent();

            assertEquals(7L, result.userId());
            assertFalse(result.platformAdmin());
        }
    }

    @Test
    void requireCurrentRejectsMissingAuthenticatedAccount() {
        when(userRepository.findBasicById(7L)).thenReturn(null);

        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(7L);

            BusinessException error = assertThrows(BusinessException.class, facade::requireCurrent);

            assertEquals(ErrorCode.USER_NOT_FOUND.getCode(), error.getCode());
        }
    }
}
