package com.couponwith.identity;

import com.couponwith.common.ApiException;
import com.couponwith.space.Space;
import com.couponwith.space.SpaceMember;
import com.couponwith.space.SpaceMemberRepository;
import com.couponwith.space.SpaceRepository;
import com.couponwith.space.SpaceRole;
import com.couponwith.space.SpaceType;
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
    private final SpaceRepository spaces;
    private final SpaceMemberRepository members;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;

    public AuthService(UserRepository users, SpaceRepository spaces, SpaceMemberRepository members,
                       PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder) {
        this.users = users;
        this.spaces = spaces;
        this.members = members;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
    }

    @Transactional
    public AuthResult register(String rawEmail, String password, String displayName, String timezone) {
        var email = normalizeEmail(rawEmail);
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "이미 가입된 이메일입니다.");
        }
        var user = users.save(new UserAccount(UUID.randomUUID(), email, passwordEncoder.encode(password),
                displayName.trim(), timezone));
        var personalSpace = spaces.save(new Space(UUID.randomUUID(), SpaceType.PERSONAL,
                displayName.trim() + "의 개인 공간", user.getId(), timezone, "sky"));
        members.save(new SpaceMember(personalSpace.getId(), user.getId(), SpaceRole.OWNER));
        return issueToken(user);
    }

    @Transactional(readOnly = true)
    public AuthResult login(String rawEmail, String password) {
        var user = users.findByEmailIgnoreCase(normalizeEmail(rawEmail))
                .orElseThrow(this::invalidCredentials);
        if (!user.isActive() || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw invalidCredentials();
        }
        return issueToken(user);
    }

    private AuthResult issueToken(UserAccount user) {
        var now = Instant.now();
        var expiresAt = now.plus(Duration.ofHours(1));
        var claims = JwtClaimsSet.builder()
                .issuer("moaday-api")
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim("email", user.getEmail())
                .claim("name", user.getDisplayName())
                .build();
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        var token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AuthResult(token, expiresAt, new UserView(user.getId(), user.getEmail(), user.getDisplayName(), user.getTimezone()));
    }

    private ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다.");
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public record AuthResult(String accessToken, Instant expiresAt, UserView user) {}
    public record UserView(UUID id, String email, String displayName, String timezone) {}
}
