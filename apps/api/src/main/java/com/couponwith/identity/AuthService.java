package com.couponwith.identity;

import com.couponwith.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final LoginProtectionService loginProtection;
    private final EmailRegistrationService registrations;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder,
                       LoginProtectionService loginProtection, EmailRegistrationService registrations) {
        this.users = users; this.passwordEncoder = passwordEncoder; this.jwtEncoder = jwtEncoder;
        this.loginProtection = loginProtection; this.registrations = registrations;
    }

    public RegistrationPending register(String rawEmail, String password, String displayName, String timezone) {
        var email = normalizeEmail(rawEmail);
        try {
            return registrations.register(email, password, displayName, timezone);
        } catch (EmailRegistrationConflictException ignored) {
            return registrations.register(email, password, displayName, timezone);
        }
    }

    public AuthResult login(String rawEmail, String password) {
        var decision = loginProtection.authenticate(normalizeEmail(rawEmail), password);
        if (decision.status() == LoginProtectionService.Status.LOCKED) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "LOGIN_TEMPORARILY_LOCKED", "로그인 시도가 많아 계정이 잠시 잠겼습니다. 잠시 후 다시 시도하거나 비밀번호를 재설정해 주세요.");
        if (decision.status() != LoginProtectionService.Status.AUTHENTICATED) throw invalidCredentials();
        return issueToken(decision.user());
    }

    @Transactional
    public AuthResult changePassword(UUID userId, String currentPassword, String newPassword) {
        var user = users.findByIdForUpdate(userId).filter(UserAccount::isActive).orElseThrow(this::invalidCredentials);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_PASSWORD", "현재 비밀번호가 올바르지 않습니다.");
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PASSWORD_UNCHANGED", "새 비밀번호는 현재 비밀번호와 다르게 입력해 주세요.");
        user.changePassword(passwordEncoder.encode(newPassword));
        return issueToken(user);
    }

    private AuthResult issueToken(UserAccount user) {
        var now = Instant.now(); var expiresAt = now.plus(Duration.ofHours(1));
        var claims = JwtClaimsSet.builder().issuer("moaday-api").subject(user.getId().toString()).issuedAt(now).expiresAt(expiresAt)
                .claim("email", user.getEmail()).claim("name", user.getDisplayName()).claim("security_version", user.getSecurityVersion()).build();
        var token = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new AuthResult(token, expiresAt, new UserView(user.getId(), user.getEmail(), user.getDisplayName(), user.getTimezone()));
    }
    private ApiException invalidCredentials() { return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다."); }
    private String normalizeEmail(String email) { return email.trim().toLowerCase(Locale.ROOT); }
    public record AuthResult(String accessToken, Instant expiresAt, UserView user) {}
    public record RegistrationPending(String email, String message) {}
    public record UserView(UUID id, String email, String displayName, String timezone) {}
}
