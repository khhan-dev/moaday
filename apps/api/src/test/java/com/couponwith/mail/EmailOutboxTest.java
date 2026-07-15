package com.couponwith.mail;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EmailOutboxTest {
    @Test
    void movesToDeadAfterTheMaximumAttemptsAndRemovesSensitiveBody() {
        var now = Instant.parse("2026-07-15T00:00:00Z");
        var item = new EmailOutbox(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "INVITATION",
                "member@example.com", "초대", "secret-token", 2, now);

        item.claim(now);
        item.markFailed(now, now.plusSeconds(60), "SMTP 실패");
        assertThat(item.getStatus()).isEqualTo(EmailOutboxStatus.RETRY);
        assertThat(item.getBody()).isEqualTo("secret-token");

        item.claim(now.plusSeconds(60));
        item.markFailed(now.plusSeconds(60), now.plusSeconds(180), "SMTP 실패");
        assertThat(item.getStatus()).isEqualTo(EmailOutboxStatus.DEAD);
        assertThat(item.getBody()).doesNotContain("secret-token");
    }

    @Test
    void cancelsAnUnsentInvitationAndRemovesItsToken() {
        var now = Instant.parse("2026-07-15T00:00:00Z");
        var item = new EmailOutbox(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "INVITATION",
                "member@example.com", "초대", "secret-token", 5, now);

        item.cancel(now.plusSeconds(1));

        assertThat(item.getStatus()).isEqualTo(EmailOutboxStatus.CANCELLED);
        assertThat(item.getBody()).doesNotContain("secret-token");
        assertThat(item.getNextAttemptAt()).isNull();
    }

    @Test
    void staleFinalAttemptIsRecoveredAsDeadWithoutKeepingTheMailBody() {
        var now = Instant.parse("2026-07-15T00:00:00Z");
        var item = new EmailOutbox(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "INVITATION",
                "member@example.com", "초대", "secret-token", 1, now);

        item.claim(now);
        item.recover(now.plusSeconds(300));

        assertThat(item.getStatus()).isEqualTo(EmailOutboxStatus.DEAD);
        assertThat(item.getBody()).doesNotContain("secret-token");
        assertThat(item.getNextAttemptAt()).isNull();
    }
}
