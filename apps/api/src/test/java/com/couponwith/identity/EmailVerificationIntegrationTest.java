package com.couponwith.identity;

import com.couponwith.common.ApiException;
import com.couponwith.mail.EmailOutboxRepository;
import com.couponwith.mail.EmailOutboxStatus;
import com.couponwith.space.SpaceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class EmailVerificationIntegrationTest {
    @Autowired AuthService auth;
    @Autowired MockMvc mockMvc;
    @Autowired UserRepository users;
    @Autowired SpaceRepository spaces;
    @Autowired EmailVerificationTokenRepository verificationTokens;
    @Autowired EmailOutboxRepository emailOutbox;
    @Autowired EmailVerificationService verification;

    @Test
    void registrationCreatesAPendingAccountAndQueuesVerificationWithoutCreatingASpace() {
        var email = "verification-" + System.nanoTime() + "@example.com";

        var pending = auth.register(email, "password123!", "인증 대기", "Asia/Seoul");

        assertThat(pending.email()).isEqualTo(email);
        var user = users.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(user.isActive()).isFalse();
        assertThat(spaces.findAll()).noneMatch(space -> space.getOwnerUserId().equals(user.getId()));
        var token = verificationTokens.findByUserIdAndConsumedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(user.getId()).getFirst();
        assertThat(emailOutbox.findAll()).anySatisfy(mail -> {
            assertThat(mail.getEmailVerificationTokenId()).isEqualTo(token.getId());
            assertThat(mail.getCategory()).isEqualTo("EMAIL_VERIFICATION");
            assertThat(mail.getStatus()).isEqualTo(EmailOutboxStatus.PENDING);
            assertThat(mail.getBody()).doesNotContain(token.getTokenHash());
        });
    }

    @Test
    void confirmationActivatesTheAccountCreatesOnePersonalSpaceAndConsumesTheLink() {
        var email = "verify-confirm-" + System.nanoTime() + "@example.com";
        auth.register(email, "password123!", "인증 완료", "Asia/Seoul");
        var user = users.findByEmailIgnoreCase(email).orElseThrow();
        var rawToken = "email-verification-test-token";
        verificationTokens.save(new EmailVerificationToken(UUID.randomUUID(), user.getId(),
                PasswordRecoveryService.hash(rawToken), Instant.now().plusSeconds(1_800), Instant.now()));

        verification.confirm(rawToken);

        assertThat(users.findById(user.getId()).orElseThrow().isActive()).isTrue();
        assertThat(spaces.findAll()).filteredOn(space -> space.getOwnerUserId().equals(user.getId())).hasSize(1);
        assertThat(auth.login(email, "password123!").user().id()).isEqualTo(user.getId());
        assertThatThrownBy(() -> verification.confirm(rawToken)).hasMessageContaining("올바르지 않거나 만료");
    }

    @Test
    void resendRevokesThePreviousTokenAndThePreviousLinkCannotActivateTheAccount() {
        var email = "verify-resend-" + System.nanoTime() + "@example.com";
        var user = pendingUser(email);
        var oldRawToken = "old-verification-token-" + UUID.randomUUID();
        var oldToken = verificationTokens.saveAndFlush(new EmailVerificationToken(UUID.randomUUID(), user.getId(),
                PasswordRecoveryService.hash(oldRawToken), Instant.now().plusSeconds(1_800), Instant.now().minusSeconds(120)));

        verification.resend(email);

        assertThat(verificationTokens.findById(oldToken.getId()).orElseThrow().getRevokedAt()).isNotNull();
        assertThatThrownBy(() -> verification.confirm(oldRawToken)).hasMessageContaining("올바르지 않거나 만료");
        assertThat(verificationTokens.findByUserIdAndConsumedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(user.getId())).hasSize(1);
        assertThat(users.findById(user.getId()).orElseThrow().isPendingEmailVerification()).isTrue();
    }

    @Test
    void simultaneousConfirmationAndResendCompleteWithoutDeadlockOrDuplicatePersonalSpace() throws Exception {
        var email = "verify-concurrent-" + System.nanoTime() + "@example.com";
        var user = pendingUser(email);
        var rawToken = "concurrent-verification-token-" + UUID.randomUUID();
        verificationTokens.saveAndFlush(new EmailVerificationToken(UUID.randomUUID(), user.getId(),
                PasswordRecoveryService.hash(rawToken), Instant.now().plusSeconds(1_800), Instant.now().minusSeconds(120)));
        var start = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var confirmation = executor.submit(() -> {
                start.await();
                try { verification.confirm(rawToken); } catch (RuntimeException ignored) { }
                return null;
            });
            var resend = executor.submit(() -> {
                start.await();
                verification.resend(email);
                return null;
            });

            confirmation.get(10, TimeUnit.SECONDS);
            resend.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(spaces.findAll()).filteredOn(space -> space.getOwnerUserId().equals(user.getId())).hasSizeLessThanOrEqualTo(1);
        assertThat(users.findById(user.getId()).orElseThrow().isActive()
                || users.findById(user.getId()).orElseThrow().isPendingEmailVerification()).isTrue();
    }

    @Test
    void simultaneousFirstRegistrationsFollowThePendingRegistrationPathWithoutPersistenceFailure() throws Exception {
        var email = "duplicate-registration-" + System.nanoTime() + "@example.com";
        var start = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> registerAfter(start, email));
            var second = executor.submit(() -> registerAfter(start, email));
            var outcomes = Stream.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)).toList();

            assertThat(outcomes).containsExactly("PENDING", "PENDING");
        } finally {
            executor.shutdownNow();
        }
        var user = users.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(users.findAll()).filteredOn(account -> account.getEmail().equals(email)).hasSize(1);
        assertThat(verificationTokens.findByUserIdAndConsumedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(user.getId())).hasSize(1);
        assertThat(spaces.findAll()).filteredOn(space -> space.getOwnerUserId().equals(user.getId())).isEmpty();
    }

    @Test
    void duplicatePendingRegistrationViaHttpReturnsAcceptedAndKeepsOnePendingAccountAndToken() throws Exception {
        var email = "duplicate-http-registration-" + System.nanoTime() + "@example.com";
        var request = """
                {"email":"%s","password":"password123!","displayName":"HTTP duplicate","timezone":"Asia/Seoul"}
                """.formatted(email);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isAccepted());
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isAccepted());

        var user = users.findByEmailIgnoreCase(email.toUpperCase()).orElseThrow();
        assertThat(users.findAll()).filteredOn(account -> account.getEmail().equalsIgnoreCase(email)).hasSize(1);
        assertThat(user.isPendingEmailVerification()).isTrue();
        assertThat(user.isActive()).isFalse();
        assertThat(spaces.findAll()).filteredOn(space -> space.getOwnerUserId().equals(user.getId())).isEmpty();
        assertThat(verificationTokens.findByUserIdAndConsumedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(user.getId()))
                .hasSize(1);
    }

    private String registerAfter(CyclicBarrier start, String email) throws Exception {
        start.await();
        try {
            auth.register(email, "password123!", "중복 가입", "Asia/Seoul");
            return "PENDING";
        } catch (ApiException exception) {
            return exception.code();
        }
    }

    private UserAccount pendingUser(String email) {
        return users.saveAndFlush(new UserAccount(UUID.randomUUID(), email, "not-used", "인증 대기", "Asia/Seoul"));
    }
}
