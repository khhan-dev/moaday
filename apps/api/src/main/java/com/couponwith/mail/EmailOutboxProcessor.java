package com.couponwith.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
public class EmailOutboxProcessor {
    private final EmailOutboxStore store;
    private final MailDeliveryService delivery;
    private final int batchSize;
    private final Duration baseRetryDelay;
    private final Duration maxRetryDelay;
    private final Duration processingTimeout;

    public EmailOutboxProcessor(EmailOutboxStore store, MailDeliveryService delivery,
                                @Value("${moaday.mail.outbox.batch-size:20}") int batchSize,
                                @Value("${moaday.mail.outbox.retry-base-seconds:60}") long retryBaseSeconds,
                                @Value("${moaday.mail.outbox.retry-max-seconds:3600}") long retryMaxSeconds,
                                @Value("${moaday.mail.outbox.processing-timeout-seconds:300}") long processingTimeoutSeconds) {
        this.store = store;
        this.delivery = delivery;
        this.batchSize = Math.max(1, batchSize);
        var safeBaseSeconds = Math.max(1, retryBaseSeconds);
        this.baseRetryDelay = Duration.ofSeconds(safeBaseSeconds);
        this.maxRetryDelay = Duration.ofSeconds(Math.max(safeBaseSeconds, retryMaxSeconds));
        this.processingTimeout = Duration.ofSeconds(Math.max(30, processingTimeoutSeconds));
    }

    @Scheduled(fixedDelayString = "${moaday.mail.outbox.interval-ms:10000}",
            initialDelayString = "${moaday.mail.outbox.initial-delay-ms:5000}")
    public void deliverQueuedMail() { processAt(Instant.now()); }

    public int processAt(Instant now) {
        store.recoverStale(now, processingTimeout);
        var processed = 0;
        for (var job : store.claimDue(now, batchSize)) {
            try {
                if (delivery.send(job.recipient(), job.subject(), job.body())) {
                    store.markSent(job.id(), Instant.now());
                } else {
                    store.markFailed(job.id(), Instant.now(), retryDelay(job.attemptCount()), "SMTP 발송에 실패했습니다.");
                }
            } catch (RuntimeException failure) {
                store.markFailed(job.id(), Instant.now(), retryDelay(job.attemptCount()), failure.getClass().getSimpleName());
            }
            processed++;
        }
        return processed;
    }

    private Duration retryDelay(int attemptCount) {
        var multiplier = 1L << Math.min(20, Math.max(0, attemptCount - 1));
        var maximum = maxRetryDelay.toSeconds();
        var base = baseRetryDelay.toSeconds();
        var seconds = base > maximum / multiplier ? maximum : Math.min(maximum, base * multiplier);
        return Duration.ofSeconds(seconds);
    }
}
