package com.couponwith;

import com.couponwith.coupon.CouponService;
import com.couponwith.coupon.CouponStatus;
import com.couponwith.identity.AuthService;
import com.couponwith.notification.NotificationService;
import com.couponwith.notification.ProfileService;
import com.couponwith.post.PostService;
import com.couponwith.space.SpaceRole;
import com.couponwith.space.SpaceService;
import com.couponwith.space.SpaceType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class StageThreeToFiveIntegrationTest {
    @Autowired AuthService authService; @Autowired SpaceService spaceService; @Autowired PostService postService;
    @Autowired CouponService couponService; @Autowired NotificationService notificationService; @Autowired ProfileService profileService;

    @Test void postsSupportTagsSearchPinCommentsAndNotifications() {
        var pair=pair("post");
        var post=postService.create(pair.owner.user().id(),pair.space.id(),"여행 준비물","여권과 충전기",Set.of("여행","준비물"));
        assertThat(postService.list(pair.member.user().id(),pair.space.id(),"충전기","여행",false)).singleElement();
        var pinned=postService.pin(pair.owner.user().id(),post.post().id(),true);assertThat(pinned.pinned()).isTrue();
        var comment=postService.addComment(pair.member.user().id(),post.post().id(),"확인했습니다.");
        assertThat(postService.get(pair.owner.user().id(),post.post().id()).comments()).extracting(PostService.CommentView::id).contains(comment.id());
        assertThat(notificationService.list(pair.member.user().id())).extracting(NotificationService.NotificationView::type).contains("POST_CREATED");
        assertThat(notificationService.list(pair.owner.user().id())).extracting(NotificationService.NotificationView::type).contains("POST_COMMENT");
    }

    @Test void couponClaimIsExclusiveAndBarcodeRequiresPermission() {
        var pair=pair("coupon");
        var input=new CouponService.CouponInput("아메리카노","Moa Cafe",null, Instant.now().plus(7,ChronoUnit.DAYS),"8801234567893","EAN13");
        var coupon=couponService.create(pair.owner.user().id(),pair.space.id(),input);
        assertThat(couponService.update(pair.owner.user().id(),coupon.id(),new CouponService.CouponInput("아메리카노","Moa Cafe",null,Instant.now().plus(7,ChronoUnit.DAYS),"","")).hasBarcode()).isTrue();
        var claimed=couponService.claim(pair.member.user().id(),coupon.id());assertThat(claimed.status()).isEqualTo(CouponStatus.CLAIMED);
        assertThat(couponService.barcode(pair.member.user().id(),coupon.id()).value()).isEqualTo("8801234567893");
        assertThatThrownBy(()->couponService.claim(pair.owner.user().id(),coupon.id())).hasMessageContaining("이미 선점");
        couponService.release(pair.member.user().id(),coupon.id());couponService.claim(pair.member.user().id(),coupon.id());
        assertThat(couponService.use(pair.member.user().id(),coupon.id()).status()).isEqualTo(CouponStatus.USED);
        assertThat(notificationService.list(pair.owner.user().id())).extracting(NotificationService.NotificationView::type).contains("COUPON_CLAIMED","COUPON_USED");
    }

    @Test void preferencesProfileAndAccountDeletionWork() {
        var user=authService.register("settings-user@example.com","password123!","설정 사용자","Asia/Seoul");
        var preferences=notificationService.updatePreferences(user.user().id(),new NotificationService.PreferenceInput(true,true,false,true,false));
        assertThat(preferences.emailNotifications()).isTrue();assertThat(preferences.couponActivity()).isFalse();
        assertThat(profileService.update(user.user().id(),"변경된 이름","UTC").displayName()).isEqualTo("변경된 이름");
        profileService.delete(user.user().id(),"password123!");
        assertThatThrownBy(()->authService.login("settings-user@example.com","password123!")).hasMessageContaining("올바르지 않습니다");
    }

    private Pair pair(String prefix){var suffix=prefix+System.nanoTime();var owner=authService.register(suffix+"-owner@example.com","password123!","소유자","Asia/Seoul");var member=authService.register(suffix+"-member@example.com","password123!","멤버","Asia/Seoul");var space=spaceService.create(owner.user().id(),SpaceType.FAMILY,"공유 가족","Asia/Seoul","green");var invite=spaceService.invite(owner.user().id(),space.id(),member.user().email(),SpaceRole.MEMBER);spaceService.accept(member.user().id(),invite.oneTimeToken());return new Pair(owner,member,space);}
    private record Pair(AuthService.AuthResult owner,AuthService.AuthResult member,SpaceService.SpaceView space){}
}
