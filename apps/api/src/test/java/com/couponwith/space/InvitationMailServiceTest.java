package com.couponwith.space;

import com.couponwith.mail.MailDeliveryService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class InvitationMailServiceTest {
    @Test
    void sendsAnInvitationWithTheAcceptLink() {
        var delivery = mock(MailDeliveryService.class);
        when(delivery.send(anyString(), anyString(), anyString())).thenReturn(true);
        var service = new InvitationMailService(delivery);
        var expiresAt = Instant.parse("2026-07-22T03:00:00Z");

        var sent = service.send("member@example.com", "초대자", "우리 가족", SpaceRole.MEMBER,
                expiresAt, "https://moaday.test/?invite=one-time-token");

        assertThat(sent).isTrue();
        var recipient = ArgumentCaptor.forClass(String.class);
        var subject = ArgumentCaptor.forClass(String.class);
        var body = ArgumentCaptor.forClass(String.class);
        verify(delivery).send(recipient.capture(), subject.capture(), body.capture());
        assertThat(recipient.getValue()).isEqualTo("member@example.com");
        assertThat(subject.getValue()).contains("MoaDay", "우리 가족");
        assertThat(body.getValue()).contains("초대자", "우리 가족", "멤버", "https://moaday.test/?invite=one-time-token");
    }

    @Test
    void doesNotTryToSendWithoutAnInvitationLink() {
        var delivery = mock(MailDeliveryService.class);
        var service = new InvitationMailService(delivery);
        assertThat(service.send("member@example.com", "초대자", "우리 가족", SpaceRole.MEMBER,
                Instant.now(), null)).isFalse();
        verifyNoInteractions(delivery);
    }

    @Test
    void reportsDeliveryFailureWithoutFailingInvitationCreation() {
        var delivery = mock(MailDeliveryService.class);
        when(delivery.send(anyString(), anyString(), anyString())).thenReturn(false);
        var service = new InvitationMailService(delivery);
        assertThat(service.send("member@example.com", "초대자", "우리 가족", SpaceRole.MEMBER,
                Instant.now(), "https://moaday.test/?invite=token")).isFalse();
    }
}
