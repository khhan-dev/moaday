package com.couponwith.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class MailDeliveryService {
    private final JavaMailSender mailSender;
    private final String from;

    public MailDeliveryService(JavaMailSender mailSender,
                               @Value("${moaday.mail.from:no-reply@moaday.local}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    public boolean send(String recipient, String subject, String body) {
        if (recipient == null || recipient.isBlank() || subject == null || subject.isBlank()
                || body == null || body.isBlank()) return false;
        try {
            var mail = new SimpleMailMessage();
            mail.setFrom(from);
            mail.setTo(recipient.trim());
            mail.setSubject(subject);
            mail.setText(body);
            mailSender.send(mail);
            return true;
        } catch (MailException ignored) {
            return false;
        }
    }
}
