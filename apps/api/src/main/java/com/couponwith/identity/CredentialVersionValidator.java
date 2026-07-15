package com.couponwith.identity;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CredentialVersionValidator implements OAuth2TokenValidator<Jwt> {
    private static final OAuth2Error INVALIDATED = new OAuth2Error(
            "invalid_token", "비밀번호 변경 또는 계정 상태 변경으로 만료된 로그인입니다.", null);
    private final UserRepository users;

    public CredentialVersionValidator(UserRepository users) { this.users = users; }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        try {
            var user = users.findById(UUID.fromString(jwt.getSubject())).orElse(null);
            Number claim = jwt.getClaim("security_version");
            var tokenVersion = claim == null ? 0 : claim.intValue();
            if (user != null && user.isActive() && user.getSecurityVersion() == tokenVersion) {
                return OAuth2TokenValidatorResult.success();
            }
        } catch (RuntimeException ignored) {
            // Malformed subjects and claims are rejected as invalid tokens.
        }
        return OAuth2TokenValidatorResult.failure(INVALIDATED);
    }
}
