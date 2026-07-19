package com.couponwith.identity;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceConflictTest {
    @Test
    void usesTheExistingPendingRegistrationPathAfterAUniqueEmailInsertConflict() {
        var users = mock(UserRepository.class);
        var passwordEncoder = mock(PasswordEncoder.class);
        var jwtEncoder = mock(JwtEncoder.class);
        var loginProtection = mock(LoginProtectionService.class);
        var registrations = mock(EmailRegistrationService.class);
        var auth = new AuthService(users, passwordEncoder, jwtEncoder, loginProtection, registrations);
        var email = "concurrent@example.com";
        var pending = new AuthService.RegistrationPending(email, "인증 이메일을 확인해 계정을 활성화해 주세요.");

        when(registrations.register(email, "password123!", "동시 가입", "Asia/Seoul"))
                .thenThrow(new EmailRegistrationConflictException(new DataIntegrityViolationException("users_email_key")))
                .thenReturn(pending);

        assertThat(auth.register(" CONCURRENT@example.com ", "password123!", "동시 가입", "Asia/Seoul"))
                .isEqualTo(pending);
        verify(registrations, times(2)).register(email, "password123!", "동시 가입", "Asia/Seoul");
    }
}
