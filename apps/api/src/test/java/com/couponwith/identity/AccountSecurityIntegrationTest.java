package com.couponwith.identity;

import com.couponwith.mail.EmailOutboxRepository;
import com.couponwith.mail.EmailOutboxStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class AccountSecurityIntegrationTest {
    @Autowired AuthService auth;
    @Autowired PasswordRecoveryService recovery;
    @Autowired PasswordResetTokenRepository resetTokens;
    @Autowired EmailOutboxRepository emailOutbox;
    @Autowired UserRepository users;
    @Autowired JwtDecoder jwtDecoder;

    @Test
    void resetRequestIsGenericRateLimitedAndQueuedThroughTheOutbox() {
        var registered = auth.register("recovery-request@example.com", "password123!", "복구 요청", "Asia/Seoul");
        var before = resetTokens.count();

        recovery.requestReset(registered.user().email());

        assertThat(resetTokens.count()).isEqualTo(before + 1);
        var token = resetTokens.findByUserIdAndConsumedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(
                registered.user().id()).getFirst();
        assertThat(emailOutbox.findAll()).anySatisfy(mail -> {
            assertThat(mail.getPasswordResetTokenId()).isEqualTo(token.getId());
            assertThat(mail.getCategory()).isEqualTo("PASSWORD_RESET");
            assertThat(mail.getStatus()).isEqualTo(EmailOutboxStatus.PENDING);
            assertThat(mail.getBody()).doesNotContain(token.getTokenHash());
        });

        recovery.requestReset(registered.user().email());
        recovery.requestReset("not-registered@example.com");
        assertThat(resetTokens.count()).isEqualTo(before + 1);
    }

    @Test
    void resetTokenIsSingleUseAndInvalidatesExistingJwtAndOldPassword() {
        var registered = auth.register("recovery-confirm@example.com", "password123!", "복구 확인", "Asia/Seoul");
        var rawToken = "one-time-reset-token";
        var entity = resetTokens.save(new PasswordResetToken(UUID.randomUUID(), registered.user().id(),
                PasswordRecoveryService.hash(rawToken), Instant.now().plusSeconds(1800), Instant.now()));

        recovery.resetPassword(rawToken, "new-password123!");

        assertThat(resetTokens.findById(entity.getId()).orElseThrow().getConsumedAt()).isNotNull();
        assertThatThrownBy(() -> recovery.resetPassword(rawToken, "another-password123!"))
                .hasMessageContaining("올바르지 않거나 만료");
        assertThatThrownBy(() -> auth.login(registered.user().email(), "password123!"))
                .hasMessageContaining("올바르지 않습니다");
        assertThat(auth.login(registered.user().email(), "new-password123!").user().id())
                .isEqualTo(registered.user().id());
        assertThatThrownBy(() -> jwtDecoder.decode(registered.accessToken()))
                .isInstanceOf(org.springframework.security.oauth2.jwt.JwtValidationException.class);
    }

    @Test
    void repeatedLoginFailuresLockTheAccountAndPasswordResetUnlocksIt() {
        var registered = auth.register("locked-account@example.com", "password123!", "잠금 계정", "Asia/Seoul");
        for (var attempt = 0; attempt < 5; attempt++) {
            assertThatThrownBy(() -> auth.login(registered.user().email(), "wrong-password"))
                    .hasMessageContaining("올바르지 않습니다");
        }
        assertThatThrownBy(() -> auth.login(registered.user().email(), "password123!"))
                .hasMessageContaining("잠시 잠겼습니다");

        var rawToken = "unlock-reset-token";
        resetTokens.save(new PasswordResetToken(UUID.randomUUID(), registered.user().id(),
                PasswordRecoveryService.hash(rawToken), Instant.now().plusSeconds(1800), Instant.now()));
        recovery.resetPassword(rawToken, "unlocked-password123!");

        assertThat(auth.login(registered.user().email(), "unlocked-password123!").user().id())
                .isEqualTo(registered.user().id());
        assertThat(users.findById(registered.user().id()).orElseThrow().getLockedUntil()).isNull();
    }

    @Test
    void authenticatedPasswordChangeIssuesANewTokenAndRevokesTheOldVersion() {
        var registered = auth.register("password-change@example.com", "password123!", "변경 계정", "Asia/Seoul");

        var changed = auth.changePassword(registered.user().id(), "password123!", "changed-password123!");

        assertThat(jwtDecoder.decode(changed.accessToken()).getSubject()).isEqualTo(registered.user().id().toString());
        assertThatThrownBy(() -> jwtDecoder.decode(registered.accessToken()))
                .isInstanceOf(org.springframework.security.oauth2.jwt.JwtValidationException.class);
    }
}
