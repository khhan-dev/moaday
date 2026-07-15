package com.couponwith.mail;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MailDeliveryServiceTest {
    @Test
    void appliesTheConfiguredSenderToEveryMail() {
        var sender = mock(JavaMailSender.class);
        var service = new MailDeliveryService(sender, "sender@moaday.test");

        assertThat(service.send(" member@example.com ", "제목", "본문")).isTrue();

        var message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(message.capture());
        assertThat(message.getValue().getFrom()).isEqualTo("sender@moaday.test");
        assertThat(message.getValue().getTo()).containsExactly("member@example.com");
    }

    @Test
    void reportsSmtpFailureWithoutBreakingTheBusinessTransaction() {
        var sender = mock(JavaMailSender.class);
        doThrow(new MailSendException("SMTP unavailable")).when(sender).send(any(SimpleMailMessage.class));
        var service = new MailDeliveryService(sender, "sender@moaday.test");
        assertThat(service.send("member@example.com", "제목", "본문")).isFalse();
    }
}
