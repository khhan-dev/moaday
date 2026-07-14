package com.couponwith;

import com.couponwith.automation.ScheduledAutomationService;
import com.couponwith.calendar.CalendarService;
import com.couponwith.calendar.EventRecurrence;
import com.couponwith.coupon.CouponRepository;
import com.couponwith.coupon.CouponService;
import com.couponwith.coupon.CouponStatus;
import com.couponwith.identity.AuthService;
import com.couponwith.notification.NotificationService;
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
class StageSixHardeningIntegrationTest {
    @Autowired AuthService auth;
    @Autowired SpaceService spaces;
    @Autowired PostService posts;
    @Autowired CouponService coupons;
    @Autowired CouponRepository couponRepository;
    @Autowired CalendarService calendars;
    @Autowired NotificationService notifications;
    @Autowired ScheduledAutomationService automation;

    @Test
    void roleMatrixBlocksUnauthorizedMutationsAndRemovedMemberAccess() {
        var group = group("roles");
        var memberPost = posts.create(group.member.user().id(), group.space.id(), "멤버 글", "내용", Set.of());

        assertThat(posts.list(group.viewer.user().id(), group.space.id(), null, null, false)).hasSize(1);
        assertThatThrownBy(() -> posts.create(group.viewer.user().id(), group.space.id(), "차단", "내용", Set.of()))
                .hasMessageContaining("열람자는 글이나 댓글을 작성할 수 없습니다");
        assertThatThrownBy(() -> posts.pin(group.member.user().id(), memberPost.post().id(), true))
                .hasMessageContaining("고정할 권한");
        assertThat(posts.pin(group.admin.user().id(), memberPost.post().id(), true).pinned()).isTrue();
        assertThatThrownBy(() -> spaces.changeMemberRole(group.admin.user().id(), group.space.id(),
                group.member.user().id(), SpaceRole.ADMIN)).hasMessageContaining("관리자 지정은 소유자만");

        var coupon = coupons.create(group.owner.user().id(), group.space.id(), couponInput(Instant.now().plus(1, ChronoUnit.DAYS)));
        assertThatThrownBy(() -> coupons.claim(group.viewer.user().id(), coupon.id())).hasMessageContaining("열람자는 쿠폰");

        spaces.removeMember(group.owner.user().id(), group.space.id(), group.viewer.user().id());
        assertThatThrownBy(() -> posts.list(group.viewer.user().id(), group.space.id(), null, null, false))
                .hasMessageContaining("공간을 찾을 수 없습니다");
    }

    @Test
    void attachmentsAndStructuredInputsRejectDangerousContent() {
        var group = group("security");
        var post = posts.create(group.owner.user().id(), group.space.id(), "파일", "안전한 파일만", Set.of());
        var html = new MockMultipartFile("file", "note.txt", "text/plain", "<html><script>alert(1)</script>".getBytes());
        var command = new MockMultipartFile("file", "run.ps1", "text/plain", "Write-Host unsafe".getBytes());

        assertThatThrownBy(() -> posts.upload(group.owner.user().id(), post.post().id(), html))
                .hasMessageContaining("파일 내용이 허용되지 않는 형식");
        assertThatThrownBy(() -> posts.upload(group.owner.user().id(), post.post().id(), command))
                .hasMessageContaining("웹 페이지로 열릴 수 있는 파일");
        assertThatThrownBy(() -> coupons.create(group.owner.user().id(), group.space.id(),
                new CouponService.CouponInput("쿠폰", "브랜드", null, Instant.now().plus(1, ChronoUnit.DAYS), "123", "QR")))
                .hasMessageContaining("CODE128 또는 EAN13");
        assertThatThrownBy(() -> coupons.create(group.owner.user().id(), group.space.id(),
                new CouponService.CouponInput("쿠폰", "브랜드", null, Instant.now().plus(1, ChronoUnit.DAYS), "8801234567890", "EAN13")))
                .hasMessageContaining("체크 숫자");

        var calendar = calendars.listCalendars(group.owner.user().id(), group.space.id()).getFirst();
        var start = Instant.now().plus(1, ChronoUnit.HOURS);
        var invalidLink = new CalendarService.EventInput(calendar.id(), "위험 링크", null, null, "javascript:alert(1)",
                false, start, start.plus(1, ChronoUnit.HOURS), "Asia/Seoul", EventRecurrence.NONE, null,
                Set.of(), Set.of());
        assertThatThrownBy(() -> calendars.createEvent(group.owner.user().id(), calendar.id(), invalidLink))
                .hasMessageContaining("http 또는 https");
    }

    @Test
    void scheduledExpiryAndReminderDeliveryAreAutomaticAndIdempotent() {
        var group = group("automation");
        var now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        var coupon = coupons.create(group.owner.user().id(), group.space.id(), couponInput(now.plus(5, ChronoUnit.MINUTES)));

        assertThat(automation.expireCouponsAt(now.plus(6, ChronoUnit.MINUTES))).isEqualTo(1);
        assertThat(couponRepository.findById(coupon.id()).orElseThrow().getStatus()).isEqualTo(CouponStatus.EXPIRED);
        assertThat(notifications.list(group.owner.user().id())).extracting(NotificationService.NotificationView::type)
                .contains("COUPON_EXPIRED");

        var calendar = calendars.listCalendars(group.owner.user().id(), group.space.id()).getFirst();
        var start = now.plus(5, ChronoUnit.MINUTES);
        calendars.createEvent(group.owner.user().id(), calendar.id(), new CalendarService.EventInput(calendar.id(),
                "자동 리마인더", null, null, null, false, start, start.plus(1, ChronoUnit.HOURS), "Asia/Seoul",
                EventRecurrence.NONE, null, Set.of(group.member.user().id()), Set.of(5)));

        assertThat(automation.sendEventRemindersAt(now)).isEqualTo(2);
        assertThat(automation.sendEventRemindersAt(now)).isZero();
        assertThat(notifications.list(group.member.user().id())).extracting(NotificationService.NotificationView::type)
                .contains("EVENT_REMINDER");
    }

    private CouponService.CouponInput couponInput(Instant expiresAt) {
        return new CouponService.CouponInput("자동화 쿠폰", "Moa Cafe", null, expiresAt, "8801234567893", "EAN13");
    }

    private Group group(String prefix) {
        var suffix = prefix + System.nanoTime();
        var owner = auth.register(suffix + "-owner@example.com", "password123!", "소유자", "Asia/Seoul");
        var admin = auth.register(suffix + "-admin@example.com", "password123!", "관리자", "Asia/Seoul");
        var member = auth.register(suffix + "-member@example.com", "password123!", "멤버", "Asia/Seoul");
        var viewer = auth.register(suffix + "-viewer@example.com", "password123!", "열람자", "Asia/Seoul");
        var space = spaces.create(owner.user().id(), SpaceType.FAMILY, "권한 점검", "Asia/Seoul", "green");
        invite(owner, admin, space, SpaceRole.ADMIN);
        invite(owner, member, space, SpaceRole.MEMBER);
        invite(owner, viewer, space, SpaceRole.VIEWER);
        return new Group(owner, admin, member, viewer, space);
    }

    private void invite(AuthService.AuthResult owner, AuthService.AuthResult target, SpaceService.SpaceView space, SpaceRole role) {
        var invitation = spaces.invite(owner.user().id(), space.id(), target.user().email(), role);
        spaces.accept(target.user().id(), invitation.oneTimeToken());
    }

    private record Group(AuthService.AuthResult owner, AuthService.AuthResult admin,
                         AuthService.AuthResult member, AuthService.AuthResult viewer,
                         SpaceService.SpaceView space) {}
}
