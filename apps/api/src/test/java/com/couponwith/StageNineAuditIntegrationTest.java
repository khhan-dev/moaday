package com.couponwith;

import com.couponwith.audit.AuditService;
import com.couponwith.automation.ScheduledAutomationService;
import com.couponwith.coupon.CouponRepository;
import com.couponwith.coupon.CouponService;
import com.couponwith.coupon.CouponStatus;
import com.couponwith.identity.AuthService;
import com.couponwith.post.PostService;
import com.couponwith.space.SpaceRole;
import com.couponwith.space.SpaceService;
import com.couponwith.space.SpaceType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class StageNineAuditIntegrationTest {
    @Autowired AuthService auth;
    @Autowired SpaceService spaces;
    @Autowired CouponService coupons;
    @Autowired CouponRepository couponRepository;
    @Autowired ScheduledAutomationService automation;
    @Autowired AuditService audits;
    @Autowired PostService posts;

    @Test
    void couponClaimsAutoReleaseAndEverySensitiveActionIsRecorded() {
        var group=group("coupon-audit");
        var coupon=coupons.create(group.owner.user().id(),group.space.id(),couponInput());
        var claimed=coupons.claim(group.member.user().id(),coupon.id());
        assertThat(claimed.claimExpiresAt()).isEqualTo(claimed.claimedAt().plus(15,ChronoUnit.MINUTES));
        coupons.barcode(group.member.user().id(),coupon.id());

        assertThat(automation.releaseCouponClaimsAt(claimed.claimedAt().plus(16,ChronoUnit.MINUTES))).isEqualTo(1);
        assertThat(couponRepository.findById(coupon.id()).orElseThrow().getStatus()).isEqualTo(CouponStatus.AVAILABLE);

        coupons.claim(group.member.user().id(),coupon.id());
        coupons.use(group.member.user().id(),coupon.id());
        assertThatThrownBy(()->coupons.barcode(group.owner.user().id(),coupon.id())).hasMessageContaining("선점한 뒤");
        assertThat(coupons.correct(group.owner.user().id(),coupon.id(),CouponStatus.AVAILABLE,"사용 완료를 잘못 눌러 복구").status()).isEqualTo(CouponStatus.AVAILABLE);

        assertThat(coupons.history(group.member.user().id(),coupon.id())).extracting(AuditService.AuditView::action)
                .contains("COUPON_CREATED","COUPON_CLAIMED","COUPON_REVEALED","COUPON_AUTO_RELEASED","COUPON_USED","COUPON_CORRECTED");
    }

    @Test
    void roleChangesAndFileReadsAppearInManagerAuditLog() {
        var group=group("space-audit");
        var post=posts.create(group.owner.user().id(),group.space.id(),"여행 문서","확인용", Set.of());
        var attachment=posts.upload(group.owner.user().id(),post.post().id(),new MockMultipartFile("file","memo.txt","text/plain","안전한 메모".getBytes()));
        posts.download(group.member.user().id(),attachment.id());
        spaces.changeMemberRole(group.owner.user().id(),group.space.id(),group.member.user().id(),SpaceRole.ADMIN);

        assertThat(audits.listSpace(group.owner.user().id(),group.space.id())).extracting(AuditService.AuditView::action)
                .contains("FILE_DOWNLOADED","MEMBER_ROLE_CHANGED");
    }

    private CouponService.CouponInput couponInput(){return new CouponService.CouponInput("감사 쿠폰","Moa Cafe",null, Instant.now().plus(3,ChronoUnit.DAYS),"8801234567893","EAN13");}
    private Group group(String prefix){var suffix=prefix+System.nanoTime();var owner=auth.register(suffix+"-owner@example.com","password123!","소유자","Asia/Seoul");var member=auth.register(suffix+"-member@example.com","password123!","멤버","Asia/Seoul");var space=spaces.create(owner.user().id(),SpaceType.FAMILY,"감사 가족","Asia/Seoul","green");var invitation=spaces.invite(owner.user().id(),space.id(),member.user().email(),SpaceRole.MEMBER);spaces.accept(member.user().id(),invitation.oneTimeToken());return new Group(owner,member,space);}
    private record Group(AuthService.AuthResult owner,AuthService.AuthResult member,SpaceService.SpaceView space){}
}
