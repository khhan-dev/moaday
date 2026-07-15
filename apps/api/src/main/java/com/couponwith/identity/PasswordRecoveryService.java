package com.couponwith.identity;

import com.couponwith.common.ApiException;
import com.couponwith.mail.EmailOutboxService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Service
public class PasswordRecoveryService {
    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final EmailOutboxService emailOutbox;
    private final PasswordEncoder passwordEncoder;
    private final Duration tokenLifetime;
    private final Duration requestCooldown;
    private final String webBaseUrl;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordRecoveryService(UserRepository users, PasswordResetTokenRepository tokens,
                                   EmailOutboxService emailOutbox, PasswordEncoder passwordEncoder,
                                   @Value("${moaday.security.password-reset.expiry-minutes:30}") long expiryMinutes,
                                   @Value("${moaday.security.password-reset.request-cooldown-seconds:60}") long cooldownSeconds,
                                   @Value("${moaday.web-base-url:http://localhost:3000}") String webBaseUrl) {
        this.users = users;
        this.tokens = tokens;
        this.emailOutbox = emailOutbox;
        this.passwordEncoder = passwordEncoder;
        this.tokenLifetime = Duration.ofMinutes(Math.max(5, expiryMinutes));
        this.requestCooldown = Duration.ofSeconds(Math.max(30, cooldownSeconds));
        this.webBaseUrl = webBaseUrl.replaceAll("/+$", "");
    }

    @Transactional
    public void requestReset(String rawEmail) {
        var user = users.findByEmailForUpdate(normalizeEmail(rawEmail)).filter(UserAccount::isActive).orElse(null);
        if (user == null) return;
        var now = Instant.now();
        var outstanding = tokens.findByUserIdAndConsumedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(user.getId());
        if (!outstanding.isEmpty() && outstanding.getFirst().getCreatedAt().plus(requestCooldown).isAfter(now)) return;
        outstanding.forEach(token -> revoke(token, now));

        var rawToken = generateToken();
        var token = tokens.save(new PasswordResetToken(UUID.randomUUID(), user.getId(), hash(rawToken),
                now.plus(tokenLifetime), now));
        var resetUrl = webBaseUrl + "/reset-password/" + rawToken;
        var body = """
                %s 님, MoaDay 비밀번호 재설정 요청을 받았습니다.

                아래 링크에서 새 비밀번호를 설정해 주세요.
                %s

                링크는 %d분 동안 한 번만 사용할 수 있습니다.
                본인이 요청하지 않았다면 이 메일을 무시해 주세요.
                """.formatted(user.getDisplayName(), resetUrl, tokenLifetime.toMinutes());
        emailOutbox.enqueuePasswordReset(token.getId(), user.getEmail(), "[MoaDay] 비밀번호 재설정", body);
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        var now = Instant.now();
        var token = tokens.findByTokenHashForUpdate(hash(rawToken))
                .filter(item -> item.isActive(now))
                .orElseThrow(this::invalidToken);
        var user = users.findByIdForUpdate(token.getUserId())
                .filter(UserAccount::isActive)
                .orElseThrow(this::invalidToken);
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PASSWORD_UNCHANGED",
                    "새 비밀번호는 기존 비밀번호와 다르게 입력해 주세요.");
        }
        user.changePassword(passwordEncoder.encode(newPassword));
        token.consume(now);
        emailOutbox.cancelPasswordResetDeliveries(token.getId());
        tokens.findByUserIdAndConsumedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(user.getId()).stream()
                .filter(item -> !item.getId().equals(token.getId()))
                .forEach(item -> revoke(item, now));
    }

    private void revoke(PasswordResetToken token, Instant now) {
        token.revoke(now);
        emailOutbox.cancelPasswordResetDeliveries(token.getId());
    }

    private String generateToken() {
        var bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private String normalizeEmail(String email) { return email.trim().toLowerCase(Locale.ROOT); }
    private ApiException invalidToken() {
        return new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_RESET_TOKEN_INVALID",
                "비밀번호 재설정 링크가 올바르지 않거나 만료되었습니다.");
    }
}
