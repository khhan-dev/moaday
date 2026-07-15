package com.couponwith.mail;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailOutboxProcessorTest {
    @Test
    void marksSuccessfulDeliveryAfterClaimingOutsideTheBusinessTransaction() {
        var store = mock(EmailOutboxStore.class);
        var delivery = mock(MailDeliveryService.class);
        var id = UUID.randomUUID();
        when(store.claimDue(any(), eq(10))).thenReturn(List.of(
                new EmailOutboxStore.MailJob(id, "member@example.com", "제목", "본문", 1)));
        when(delivery.send("member@example.com", "제목", "본문")).thenReturn(true);
        var processor = new EmailOutboxProcessor(store, delivery, 10, 60, 3600, 300);

        assertThat(processor.processAt(Instant.parse("2026-07-15T00:00:00Z"))).isEqualTo(1);
        verify(store).markSent(eq(id), any());
    }

    @Test
    void schedulesAnExponentialRetryAfterFailure() {
        var store = mock(EmailOutboxStore.class);
        var delivery = mock(MailDeliveryService.class);
        var id = UUID.randomUUID();
        when(store.claimDue(any(), eq(10))).thenReturn(List.of(
                new EmailOutboxStore.MailJob(id, "member@example.com", "제목", "본문", 3)));
        when(delivery.send(anyString(), anyString(), anyString())).thenReturn(false);
        var processor = new EmailOutboxProcessor(store, delivery, 10, 60, 3600, 300);

        processor.processAt(Instant.parse("2026-07-15T00:00:00Z"));

        verify(store).markFailed(eq(id), any(), eq(Duration.ofSeconds(240)), anyString());
    }
}
