package com.couponwith.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class EmailOutboxService {
    private final EmailOutboxRepository outbox;
    private final int maxAttempts;

    public EmailOutboxService(EmailOutboxRepository outbox,
                              @Value("${moaday.mail.outbox.max-attempts:5}") int maxAttempts) {
        this.outbox = outbox;
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    @Transactional
    public UUID enqueue(UUID spaceId, UUID invitationId, String category, String recipient,
                        String subject, String body) {
        var item = outbox.save(new EmailOutbox(UUID.randomUUID(), spaceId, invitationId, category,
                recipient.trim(), subject, body, maxAttempts, Instant.now()));
        return item.getId();
    }

    @Transactional
    public UUID enqueuePasswordReset(UUID passwordResetTokenId, String recipient, String subject, String body) {
        var item = outbox.save(new EmailOutbox(UUID.randomUUID(), null, null, passwordResetTokenId,
                "PASSWORD_RESET", recipient.trim(), subject, body, maxAttempts, Instant.now()));
        return item.getId();
    }

    @Transactional
    public UUID enqueueEmailVerification(UUID emailVerificationTokenId, String recipient, String subject, String body) {
        var item = outbox.save(new EmailOutbox(UUID.randomUUID(), emailVerificationTokenId,
                "EMAIL_VERIFICATION", recipient.trim(), subject, body, maxAttempts, Instant.now()));
        return item.getId();
    }

    @Transactional(readOnly = true)
    public List<DeliveryView> list(UUID spaceId) {
        return outbox.findTop100BySpaceIdOrderByCreatedAtDesc(spaceId).stream().map(DeliveryView::from).toList();
    }

    @Transactional
    public int cancelInvitationDeliveries(UUID invitationId) {
        var items = outbox.findByInvitationIdAndStatusIn(invitationId,
                List.of(EmailOutboxStatus.PENDING, EmailOutboxStatus.RETRY));
        var now = Instant.now();
        items.forEach(item -> item.cancel(now));
        return items.size();
    }

    @Transactional
    public int cancelPasswordResetDeliveries(UUID passwordResetTokenId) {
        var items = outbox.findByPasswordResetTokenIdAndStatusIn(passwordResetTokenId,
                List.of(EmailOutboxStatus.PENDING, EmailOutboxStatus.RETRY));
        var now = Instant.now();
        items.forEach(item -> item.cancel(now));
        return items.size();
    }

    @Transactional
    public int cancelEmailVerificationDeliveries(UUID emailVerificationTokenId) {
        var items = outbox.findByEmailVerificationTokenIdAndStatusIn(emailVerificationTokenId,
                List.of(EmailOutboxStatus.PENDING, EmailOutboxStatus.RETRY));
        var now = Instant.now();
        items.forEach(item -> item.cancel(now));
        return items.size();
    }

    public record DeliveryView(UUID id, UUID invitationId, String category, String recipient, String subject,
                               EmailOutboxStatus status, int attemptCount, int maxAttempts, Instant nextAttemptAt,
                               Instant lastAttemptAt, Instant sentAt, String lastError, Instant createdAt) {
        static DeliveryView from(EmailOutbox item) {
            return new DeliveryView(item.getId(), item.getInvitationId(), item.getCategory(), item.getRecipient(),
                    item.getSubject(), item.getStatus(), item.getAttemptCount(), item.getMaxAttempts(),
                    item.getNextAttemptAt(), item.getLastAttemptAt(), item.getSentAt(), item.getLastError(), item.getCreatedAt());
        }
    }
}
