package com.couponwith.mail;

import com.couponwith.common.ApiException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class EmailOutboxStore {
    private final EmailOutboxRepository outbox;

    public EmailOutboxStore(EmailOutboxRepository outbox) { this.outbox = outbox; }

    @Transactional
    public List<MailJob> claimDue(Instant now, int batchSize) {
        var items = outbox.findDue(List.of(EmailOutboxStatus.PENDING, EmailOutboxStatus.RETRY), now,
                PageRequest.of(0, Math.max(1, batchSize)));
        items.forEach(item -> item.claim(now));
        return items.stream().map(MailJob::from).toList();
    }

    @Transactional
    public void markSent(UUID id, Instant now) { require(id).markSent(now); }

    @Transactional
    public void markFailed(UUID id, Instant now, Duration retryDelay, String error) {
        require(id).markFailed(now, now.plus(retryDelay), error);
    }

    @Transactional
    public int recoverStale(Instant now, Duration processingTimeout) {
        var stale = outbox.findStale(EmailOutboxStatus.PROCESSING, now.minus(processingTimeout));
        stale.forEach(item -> item.recover(now));
        return stale.size();
    }

    private EmailOutbox require(UUID id) {
        return outbox.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                "EMAIL_OUTBOX_NOT_FOUND", "이메일 발송 작업을 찾을 수 없습니다."));
    }

    public record MailJob(UUID id, String recipient, String subject, String body, int attemptCount) {
        static MailJob from(EmailOutbox item) {
            return new MailJob(item.getId(), item.getRecipient(), item.getSubject(), item.getBody(), item.getAttemptCount());
        }
    }
}
