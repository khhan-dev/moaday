package com.couponwith.discovery;

import com.couponwith.calendar.CalendarService;
import com.couponwith.coupon.CouponService;
import com.couponwith.coupon.CouponStatus;
import com.couponwith.notification.NotificationService;
import com.couponwith.post.PostService;
import com.couponwith.space.SpaceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class DiscoveryService {
    private final SpaceService spaces;
    private final CalendarService calendars;
    private final PostService posts;
    private final CouponService coupons;
    private final NotificationService notifications;

    public DiscoveryService(SpaceService spaces, CalendarService calendars, PostService posts,
                            CouponService coupons, NotificationService notifications) {
        this.spaces = spaces;
        this.calendars = calendars;
        this.posts = posts;
        this.coupons = coupons;
        this.notifications = notifications;
    }

    @Transactional
    public DashboardView dashboard(UUID userId) {
        var now = Instant.now();
        var memberships = spaces.list(userId);
        var upcoming = new ArrayList<DashboardEvent>();
        var expiring = new ArrayList<DashboardCoupon>();
        var recentPosts = new ArrayList<DashboardPost>();
        for (var space : memberships) {
            calendars.listEvents(userId, space.id(), now, now.plus(14, ChronoUnit.DAYS)).stream()
                    .map(event -> new DashboardEvent(event.eventId(), event.title(), event.location(), event.startsAt(),
                            event.allDay(), space.id(), space.name(), event.attendees().stream()
                            .filter(CalendarService.AttendeeView::currentUser).map(item -> item.response().name()).findFirst().orElse("")))
                    .forEach(upcoming::add);
            coupons.list(userId, space.id(), null, null).stream()
                    .filter(coupon -> (coupon.status() == CouponStatus.AVAILABLE || coupon.status() == CouponStatus.CLAIMED)
                            && !coupon.expiresAt().isAfter(now.plus(7, ChronoUnit.DAYS)))
                    .map(coupon -> new DashboardCoupon(coupon.id(), coupon.title(), coupon.brand(), coupon.expiresAt(),
                            coupon.status().name(), space.id(), space.name()))
                    .forEach(expiring::add);
            posts.list(userId, space.id(), null, null, false).stream()
                    .map(post -> new DashboardPost(post.id(), post.title(), excerpt(post.content()), post.updatedAt(),
                            post.commentCount(), post.attachments().size(), space.id(), space.name()))
                    .forEach(recentPosts::add);
        }
        upcoming.sort(Comparator.comparing(DashboardEvent::startsAt));
        expiring.sort(Comparator.comparing(DashboardCoupon::expiresAt));
        recentPosts.sort(Comparator.comparing(DashboardPost::updatedAt).reversed());
        return new DashboardView(memberships.size(), notifications.unreadCount(userId),
                upcoming.size(), expiring.size(), upcoming.stream().limit(5).toList(),
                expiring.stream().limit(4).toList(), recentPosts.stream().limit(4).toList());
    }

    @Transactional
    public List<SearchResult> search(UUID userId, String query) {
        var normalized = query.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() < 2) return List.of();
        var now = Instant.now();
        var results = new ArrayList<SearchResult>();
        for (var space : spaces.list(userId)) {
            calendars.listEvents(userId, space.id(), now.minus(7, ChronoUnit.DAYS), now.plus(360, ChronoUnit.DAYS)).stream()
                    .filter(event -> contains(event.title(), normalized) || contains(event.description(), normalized)
                            || contains(event.location(), normalized))
                    .limit(10)
                    .map(event -> new SearchResult("EVENT", event.eventId(), space.id(), space.name(), event.title(),
                            firstNonBlank(event.location(), event.description(), "일정"), event.startsAt(), "calendar"))
                    .forEach(results::add);
            posts.list(userId, space.id(), normalized, null, false).stream().limit(10)
                    .map(post -> new SearchResult("POST", post.id(), space.id(), space.name(), post.title(),
                            excerpt(post.content()), post.updatedAt(), "posts"))
                    .forEach(results::add);
            coupons.list(userId, space.id(), null, normalized).stream().limit(10)
                    .map(coupon -> new SearchResult("COUPON", coupon.id(), space.id(), space.name(), coupon.title(),
                            coupon.brand() + " · " + coupon.status().name(), coupon.expiresAt(), "coupons"))
                    .forEach(results::add);
        }
        var unique = new LinkedHashMap<String, SearchResult>();
        results.stream().sorted(Comparator.comparing(SearchResult::occurredAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .forEach(result -> unique.putIfAbsent(result.type() + ":" + result.id(), result));
        return unique.values().stream().limit(30).toList();
    }

    private boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private String excerpt(String value) {
        if (value == null || value.isBlank()) return "내용 없음";
        var clean = value.trim().replaceAll("\\s+", " ");
        return clean.substring(0, Math.min(clean.length(), 140));
    }

    private String firstNonBlank(String first, String second, String fallback) {
        if (first != null && !first.isBlank()) return first;
        if (second != null && !second.isBlank()) return excerpt(second);
        return fallback;
    }

    public record DashboardView(int spaceCount, long unreadNotificationCount, int upcomingEventCount,
                                int expiringCouponCount, List<DashboardEvent> upcomingEvents,
                                List<DashboardCoupon> expiringCoupons, List<DashboardPost> recentPosts) {}
    public record DashboardEvent(UUID id, String title, String location, Instant startsAt, boolean allDay,
                                 UUID spaceId, String spaceName, String attendance) {}
    public record DashboardCoupon(UUID id, String title, String brand, Instant expiresAt, String status,
                                  UUID spaceId, String spaceName) {}
    public record DashboardPost(UUID id, String title, String excerpt, Instant updatedAt, long commentCount,
                                int attachmentCount, UUID spaceId, String spaceName) {}
    public record SearchResult(String type, UUID id, UUID spaceId, String spaceName, String title,
                               String summary, Instant occurredAt, String targetView) {}
}
