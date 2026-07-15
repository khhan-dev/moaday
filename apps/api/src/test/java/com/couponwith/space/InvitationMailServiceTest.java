package com.couponwith.space;

import com.couponwith.mail.EmailOutboxService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class InvitationMailServiceTest {
    @Test
    void queuesAnInvitationWithTheAcceptLink() {
        var outbox = mock(EmailOutboxService.class);
        when(outbox.enqueue(any(), any(), anyString(), anyString(), anyString(), anyString())).thenReturn(UUID.randomUUID());
        var service = new InvitationMailService(outbox);
        var spaceId = UUID.randomUUID();
        var invitationId = UUID.randomUUID();

        var queued = service.enqueue(spaceId, invitationId, "member@example.com", "초대자", "우리 가족",
                SpaceRole.MEMBER, Instant.parse("2026-07-22T03:00:00Z"),
                "https://moaday.test/?invite=one-time-token");

        assertThat(queued).isTrue();
        var body = ArgumentCaptor.forClass(String.class);
        verify(outbox).enqueue(any(), any(), anyString(), anyString(), anyString(), body.capture());
        assertThat(body.getValue()).contains("초대자", "우리 가족", "멤버", "https://moaday.test/?invite=one-time-token");
    }

    @Test
    void doesNotQueueWithoutAnInvitationLink() {
        var outbox = mock(EmailOutboxService.class);
        var service = new InvitationMailService(outbox);
        assertThat(service.enqueue(UUID.randomUUID(), UUID.randomUUID(), "member@example.com", "초대자",
                "우리 가족", SpaceRole.MEMBER, Instant.now(), null)).isFalse();
        verifyNoInteractions(outbox);
    }
}
