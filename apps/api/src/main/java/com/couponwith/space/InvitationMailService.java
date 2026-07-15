package com.couponwith.space;

import com.couponwith.mail.MailDeliveryService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Service
public class InvitationMailService {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy년 M월 d일 HH:mm")
            .withZone(ZoneId.of("Asia/Seoul"));

    private final MailDeliveryService mailDelivery;

    public InvitationMailService(MailDeliveryService mailDelivery) {
        this.mailDelivery = mailDelivery;
    }

    public boolean send(String recipient, String inviterName, String spaceName, SpaceRole role,
                        Instant expiresAt, String invitationUrl) {
        if (invitationUrl == null || invitationUrl.isBlank()) return false;
        return mailDelivery.send(recipient, "[MoaDay] " + spaceName + " 공간에 초대되었습니다", """
                    %s 님이 MoaDay의 '%s' 공간에 초대했습니다.

                    역할: %s
                    초대 만료: %s (한국 시간)

                    아래 링크에서 초대를 확인하고 수락하거나, MoaDay에 로그인하여 받은 초대에서 응답해 주세요.
                    %s

                    본인이 요청하지 않은 초대라면 거절하거나 이 메일을 무시해 주세요.
                    """.formatted(inviterName, spaceName, roleLabel(role), DATE_FORMAT.format(expiresAt), invitationUrl));
    }

    private String roleLabel(SpaceRole role) {
        return switch (role) {
            case OWNER -> "소유자";
            case ADMIN -> "관리자";
            case MEMBER -> "멤버";
            case VIEWER -> "열람자";
        };
    }
}
