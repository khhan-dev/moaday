package com.couponwith;

import com.couponwith.TestAccounts;
import com.couponwith.audit.AuditService;
import com.couponwith.calendar.CalendarService;
import com.couponwith.calendar.EventRecurrence;
import com.couponwith.calendar.EventResourceService;
import com.couponwith.calendar.EventResourceType;
import com.couponwith.coupon.CouponService;
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
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class StageTenEventResourceIntegrationTest {
    @Autowired AuthService auth;
    @Autowired TestAccounts testAccounts;
    @Autowired SpaceService spaces;
    @Autowired CalendarService calendars;
    @Autowired PostService posts;
    @Autowired CouponService coupons;
    @Autowired EventResourceService resources;
    @Autowired AuditService audits;

    @Test
    void eventLinksPostAttachmentAndCouponWithoutExposingBarcode() {
        var group = group("event-links");
        var calendar = calendars.listCalendars(group.owner.user().id(), group.space.id()).getFirst();
        var start = Instant.now().plus(1, ChronoUnit.DAYS);
        var event = calendars.createEvent(group.owner.user().id(), calendar.id(), new CalendarService.EventInput(
                calendar.id(), "가족 여행", "준비 자료 확인", null, null, false, start, start.plus(2, ChronoUnit.HOURS),
                "Asia/Seoul", EventRecurrence.NONE, null, Set.of(group.member.user().id()), Set.of(30)));
        var post = posts.create(group.owner.user().id(), group.space.id(), "여행 준비", "체크리스트", Set.of("여행"));
        var attachment = posts.upload(group.owner.user().id(), post.post().id(),
                new MockMultipartFile("file", "checklist.txt", "text/plain", "passport".getBytes()));
        var coupon = coupons.create(group.owner.user().id(), group.space.id(), new CouponService.CouponInput(
                "공항 식사권", "Moa Airport", null, start.plus(7, ChronoUnit.DAYS), "SECRET-BARCODE", "CODE128"));

        var saved = resources.replace(group.owner.user().id(), event.id(), List.of(
                new EventResourceService.ResourceReference(EventResourceType.POST, post.post().id()),
                new EventResourceService.ResourceReference(EventResourceType.ATTACHMENT, attachment.id()),
                new EventResourceService.ResourceReference(EventResourceType.COUPON, coupon.id())));

        assertThat(saved).extracting(EventResourceService.ResourceView::type)
                .containsExactlyInAnyOrder(EventResourceType.POST, EventResourceType.ATTACHMENT, EventResourceType.COUPON);
        assertThat(resources.listEventResources(group.member.user().id(), event.id())).hasSize(3)
                .filteredOn(item -> item.type() == EventResourceType.COUPON).singleElement().satisfies(item -> {
                    assertThat(item.title()).isEqualTo("공항 식사권");
                    assertThat(item.status()).isEqualTo("AVAILABLE");
                    assertThat(item.toString()).doesNotContain("SECRET-BARCODE");
                });
        assertThat(resources.listLinkable(group.member.user().id(), group.space.id()))
                .extracting(EventResourceService.ResourceView::resourceId)
                .contains(post.post().id(), attachment.id(), coupon.id());
        assertThat(audits.listSpace(group.owner.user().id(), group.space.id()))
                .extracting(AuditService.AuditView::action).contains("EVENT_RESOURCES_UPDATED");
    }

    @Test
    void resourcesMustBelongToEventSpaceAndOnlyEventManagersCanChangeThem() {
        var group = group("event-link-permission");
        var other = spaces.create(group.owner.user().id(), SpaceType.FRIENDS, "다른 친구", "Asia/Seoul", "sky");
        var outsidePost = posts.create(group.owner.user().id(), other.id(), "외부 문서", "연결 불가", Set.of());
        var calendar = calendars.listCalendars(group.owner.user().id(), group.space.id()).getFirst();
        var start = Instant.now().plus(2, ChronoUnit.DAYS);
        var event = calendars.createEvent(group.owner.user().id(), calendar.id(), new CalendarService.EventInput(
                calendar.id(), "보호 일정", null, null, null, false, start, start.plus(1, ChronoUnit.HOURS),
                "Asia/Seoul", EventRecurrence.NONE, null, Set.of(group.member.user().id()), Set.of()));
        var reference = new EventResourceService.ResourceReference(EventResourceType.POST, outsidePost.post().id());

        assertThatThrownBy(() -> resources.replace(group.owner.user().id(), event.id(), List.of(reference)))
                .hasMessageContaining("같은 공간");
        assertThatThrownBy(() -> resources.replace(group.member.user().id(), event.id(), List.of()))
                .hasMessageContaining("권한");
    }

    private Group group(String prefix) {
        var suffix = prefix + System.nanoTime();
        var owner = testAccounts.register(suffix + "-owner@example.com", "password123!", "소유자", "Asia/Seoul");
        var member = testAccounts.register(suffix + "-member@example.com", "password123!", "멤버", "Asia/Seoul");
        var space = spaces.create(owner.user().id(), SpaceType.FAMILY, "연결 가족", "Asia/Seoul", "green");
        var invitation = spaces.invite(owner.user().id(), space.id(), member.user().email(), SpaceRole.MEMBER);
        spaces.accept(member.user().id(), invitation.oneTimeToken());
        return new Group(owner, member, space);
    }

    private record Group(AuthService.AuthResult owner, AuthService.AuthResult member, SpaceService.SpaceView space) {}
}
