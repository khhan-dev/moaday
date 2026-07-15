package com.couponwith.mail;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "email_outbox")
public class EmailOutbox {
    @Id private UUID id;
    @Column(name = "space_id") private UUID spaceId;
    @Column(name = "invitation_id") private UUID invitationId;
    @Column(nullable = false, length = 40) private String category;
    @Column(nullable = false, length = 320) private String recipient;
    @Column(nullable = false, length = 255) private String subject;
    @Column(nullable = false, columnDefinition = "TEXT") private String body;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private EmailOutboxStatus status;
    @Column(name = "attempt_count", nullable = false) private int attemptCount;
    @Column(name = "max_attempts", nullable = false) private int maxAttempts;
    @Column(name = "next_attempt_at") private Instant nextAttemptAt;
    @Column(name = "last_attempt_at") private Instant lastAttemptAt;
    @Column(name = "sent_at") private Instant sentAt;
    @Column(name = "last_error", length = 1000) private String lastError;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected EmailOutbox() {}

    public EmailOutbox(UUID id, UUID spaceId, UUID invitationId, String category, String recipient,
                       String subject, String body, int maxAttempts, Instant now) {
        this.id = id;
        this.spaceId = spaceId;
        this.invitationId = invitationId;
        this.category = category;
        this.recipient = recipient;
        this.subject = subject;
        this.body = body;
        this.status = EmailOutboxStatus.PENDING;
        this.maxAttempts = maxAttempts;
        this.nextAttemptAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void claim(Instant now) {
        if (status != EmailOutboxStatus.PENDING && status != EmailOutboxStatus.RETRY) return;
        status = EmailOutboxStatus.PROCESSING;
        attemptCount++;
        lastAttemptAt = now;
        updatedAt = now;
    }

    public void markSent(Instant now) {
        status = EmailOutboxStatus.SENT;
        sentAt = now;
        nextAttemptAt = null;
        lastError = null;
        body = "[발송 완료 후 본문 제거]";
        updatedAt = now;
    }

    public void markFailed(Instant now, Instant nextAttempt, String error) {
        lastError = cleanError(error);
        if (attemptCount >= maxAttempts) {
            status = EmailOutboxStatus.DEAD;
            nextAttemptAt = null;
            body = "[최종 실패 후 본문 제거]";
        } else {
            status = EmailOutboxStatus.RETRY;
            nextAttemptAt = nextAttempt;
        }
        updatedAt = now;
    }

    public void recover(Instant now) {
        if (status != EmailOutboxStatus.PROCESSING) return;
        status = attemptCount >= maxAttempts ? EmailOutboxStatus.DEAD : EmailOutboxStatus.RETRY;
        nextAttemptAt = status == EmailOutboxStatus.DEAD ? null : now;
        lastError = "발송 처리 중 서버가 중단되어 다시 대기합니다.";
        if (status == EmailOutboxStatus.DEAD) body = "[최종 실패 후 본문 제거]";
        updatedAt = now;
    }

    public void cancel(Instant now) {
        if (status != EmailOutboxStatus.PENDING && status != EmailOutboxStatus.RETRY) return;
        status = EmailOutboxStatus.CANCELLED;
        nextAttemptAt = null;
        lastError = "초대 상태가 변경되거나 새 링크가 발급되어 발송을 취소했습니다.";
        body = "[취소 후 본문 제거]";
        updatedAt = now;
    }

    private String cleanError(String value) {
        if (value == null || value.isBlank()) return "SMTP 발송에 실패했습니다.";
        var clean = value.replaceAll("[\\r\\n]+", " ").trim();
        return clean.substring(0, Math.min(1000, clean.length()));
    }

    public UUID getId() { return id; }
    public UUID getSpaceId() { return spaceId; }
    public UUID getInvitationId() { return invitationId; }
    public String getCategory() { return category; }
    public String getRecipient() { return recipient; }
    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public EmailOutboxStatus getStatus() { return status; }
    public int getAttemptCount() { return attemptCount; }
    public int getMaxAttempts() { return maxAttempts; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public Instant getLastAttemptAt() { return lastAttemptAt; }
    public Instant getSentAt() { return sentAt; }
    public String getLastError() { return lastError; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
