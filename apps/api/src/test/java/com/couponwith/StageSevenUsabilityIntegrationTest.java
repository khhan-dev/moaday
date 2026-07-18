package com.couponwith;

import com.couponwith.TestAccounts;
import com.couponwith.calendar.CalendarService;
import com.couponwith.calendar.EventRecurrence;
import com.couponwith.coupon.CouponService;
import com.couponwith.discovery.DiscoveryService;
import com.couponwith.identity.AuthService;
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

@SpringBootTest
@Transactional
class StageSevenUsabilityIntegrationTest {
    @Autowired AuthService auth;
    @Autowired TestAccounts testAccounts;
    @Autowired SpaceService spaces;
    @Autowired CalendarService calendars;
    @Autowired PostService posts;
    @Autowired CouponService coupons;
    @Autowired DiscoveryService discovery;

    @Test
    void dashboardAndSearchCombineAccessibleSpaces() {
        var suffix = String.valueOf(System.nanoTime());
        var owner = testAccounts.register("discover-owner-" + suffix + "@example.com", "password123!", "소유자", "Asia/Seoul");
        var member = testAccounts.register("discover-member-" + suffix + "@example.com", "password123!", "멤버", "Asia/Seoul");
        var family = spaces.create(owner.user().id(), SpaceType.FAMILY, "검색 가족", "Asia/Seoul", "green");
        var invitation = spaces.invite(owner.user().id(), family.id(), member.user().email(), SpaceRole.MEMBER);
        spaces.accept(member.user().id(), invitation.oneTimeToken());

        posts.create(owner.user().id(), family.id(), "제주도 준비물", "바람막이와 충전기를 준비합니다.", Set.of("여행"));
        coupons.create(owner.user().id(), family.id(), new CouponService.CouponInput("제주 카페 쿠폰", "Moa Cafe",
                null, Instant.now().plus(3, ChronoUnit.DAYS), "8801234567893", "EAN13"));
        var calendar = calendars.listCalendars(owner.user().id(), family.id()).getFirst();
        var start = Instant.now().plus(1, ChronoUnit.DAYS);
        calendars.createEvent(owner.user().id(), calendar.id(), new CalendarService.EventInput(calendar.id(),
                "제주도 가족 여행", null, "제주 공항", null, false, start, start.plus(2, ChronoUnit.HOURS),
                "Asia/Seoul", EventRecurrence.NONE, null, Set.of(member.user().id()), Set.of(30)));

        assertThat(discovery.search(member.user().id(), "제주")).extracting(DiscoveryService.SearchResult::type)
                .contains("EVENT", "POST", "COUPON");
        var dashboard = discovery.dashboard(member.user().id());
        assertThat(dashboard.spaceCount()).isGreaterThanOrEqualTo(2);
        assertThat(dashboard.upcomingEvents()).extracting(DiscoveryService.DashboardEvent::title).contains("제주도 가족 여행");
        assertThat(dashboard.expiringCoupons()).extracting(DiscoveryService.DashboardCoupon::title).contains("제주 카페 쿠폰");
        assertThat(dashboard.recentPosts()).extracting(DiscoveryService.DashboardPost::title).contains("제주도 준비물");
    }
}
