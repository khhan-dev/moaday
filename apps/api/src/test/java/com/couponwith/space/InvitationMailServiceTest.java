package com.couponwith.space;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class InvitationMailServiceTest {

    @Test
    void sendsAnInvitationWithTheAcceptLink() {
        var sender = mock(JavaMailSender.class);
        var service = new InvitationMailService(sender, "sender@moaday.test");
        var expiresAt = Instant.parse("2026-07-22T03:00:00Z");

        var sent = service.send("member@example.com", "초대자", "우리 가족", SpaceRole.MEMBER,
                expiresAt, "https://moaday.test/?invite=one-time-token");

        assertThat(sent).isTrue();
        var mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(mail.capture());
        assertThat(mail.getValue().getFrom()).isEqualTo("sender@moaday.test");
        assertThat(mail.getValue().getTo()).containsExactly("member@example.com");
        assertThat(mail.getValue().getSubject()).contains("MoaDay", "우리 가족");
        assertThat(mail.getValue().getText())
                .contains("초대자", "우리 가족", "멤버", "https://moaday.test/?invite=one-time-token");
    }

    @Test
    void doesNotTryToSendWithoutAnInvitationLink() {
        var sender = mock(JavaMailSender.class);
        var service = new InvitationMailService(sender, "sender@moaday.test");

        assertThat(service.send("member@example.com", "초대자", "우리 가족", SpaceRole.MEMBER,
                Instant.now(), null)).isFalse();
        verifyNoInteractions(sender);
    }

    @Test
    void reportsDeliveryFailureWithoutFailingInvitationCreation() {
        var sender = mock(JavaMailSender.class);
        doThrow(new MailSendException("SMTP unavailable")).when(sender).send(any(SimpleMailMessage.class));
        var service = new InvitationMailService(sender, "sender@moaday.test");

        assertThat(service.send("member@example.com", "초대자", "우리 가족", SpaceRole.MEMBER,
                Instant.now(), "https://moaday.test/?invite=token")).isFalse();
    }
}
