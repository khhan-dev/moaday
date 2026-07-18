package com.couponwith.identity;

import com.couponwith.common.ApiException;
import com.couponwith.mail.EmailOutboxService;
import com.couponwith.space.Space;
import com.couponwith.space.SpaceMember;
import com.couponwith.space.SpaceMemberRepository;
import com.couponwith.space.SpaceRepository;
import com.couponwith.space.SpaceRole;
import com.couponwith.space.SpaceType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

@Service
public class EmailVerificationService {
    private final UserRepository users; private final EmailVerificationTokenRepository tokens; private final EmailOutboxService outbox;
    private final SpaceRepository spaces; private final SpaceMemberRepository members; private final Duration lifetime; private final Duration cooldown; private final String webBaseUrl;
    public EmailVerificationService(UserRepository users, EmailVerificationTokenRepository tokens, EmailOutboxService outbox, SpaceRepository spaces, SpaceMemberRepository members,
            @Value("${moaday.security.email-verification.expiry-minutes:30}") long expiryMinutes,
            @Value("${moaday.security.email-verification.request-cooldown-seconds:60}") long cooldownSeconds,
            @Value("${moaday.web-base-url:http://localhost:3000}") String webBaseUrl) {
        this.users=users; this.tokens=tokens; this.outbox=outbox; this.spaces=spaces; this.members=members;
        lifetime=Duration.ofMinutes(Math.max(5,expiryMinutes)); cooldown=Duration.ofSeconds(Math.max(30,cooldownSeconds)); this.webBaseUrl=webBaseUrl.replaceAll("/+$", "");
    }
    @Transactional public void issueFor(UserAccount user) {
        var lockedUser = users.findByIdForUpdate(user.getId()).filter(UserAccount::isPendingEmailVerification).orElseThrow();
        issueIfAllowed(lockedUser, Instant.now());
    }
    @Transactional public void resend(String rawEmail) { users.findByEmailForUpdate(rawEmail.trim().toLowerCase(Locale.ROOT)).filter(UserAccount::isPendingEmailVerification).ifPresent(user -> issueIfAllowed(user, Instant.now())); }
    @Transactional public void confirm(String rawToken) {
        var now=Instant.now(); var tokenHash=PasswordRecoveryService.hash(rawToken);
        var candidate=tokens.findByTokenHash(tokenHash).orElseThrow(this::invalidToken);
        var user=users.findByIdForUpdate(candidate.getUserId()).filter(UserAccount::isPendingEmailVerification).orElseThrow(this::invalidToken);
        var token=tokens.findByIdAndUserIdAndTokenHashForUpdate(candidate.getId(), user.getId(), tokenHash).filter(item->item.isActive(now)).orElseThrow(this::invalidToken);
        user.activate(); var space=spaces.save(new Space(UUID.randomUUID(),SpaceType.PERSONAL,user.getDisplayName()+"의 개인 공간",user.getId(),user.getTimezone(),"sky"));
        members.save(new SpaceMember(space.getId(),user.getId(),SpaceRole.OWNER)); token.consume(now); outbox.cancelEmailVerificationDeliveries(token.getId());
    }
    private void issueIfAllowed(UserAccount user, Instant now) {
        var active=tokens.findActiveByUserIdForUpdate(user.getId());
        if(!active.isEmpty() && active.getFirst().getCreatedAt().plus(cooldown).isAfter(now)) return;
        active.forEach(token->{token.revoke(now);outbox.cancelEmailVerificationDeliveries(token.getId());});
        var raw=generateToken(); var token=tokens.save(new EmailVerificationToken(UUID.randomUUID(),user.getId(),PasswordRecoveryService.hash(raw),now.plus(lifetime),now));
        var body="%s 님, MoaDay 계정 인증을 완료해 주세요.\n\n아래 링크를 클릭하면 계정이 활성화됩니다.\n%s/verify-email/%s\n\n링크는 %d분 동안 한 번만 사용할 수 있습니다.".formatted(user.getDisplayName(),webBaseUrl,raw,lifetime.toMinutes());
        outbox.enqueueEmailVerification(token.getId(),user.getEmail(),"[MoaDay] 이메일 인증",body);
    }
    private String generateToken(){var bytes=new byte[32];new java.security.SecureRandom().nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
    private ApiException invalidToken(){return new ApiException(HttpStatus.BAD_REQUEST,"EMAIL_VERIFICATION_TOKEN_INVALID","이메일 인증 링크가 올바르지 않거나 만료되었습니다.");}
}