package com.couponwith.automation;

import com.couponwith.audit.AuditService;
import com.couponwith.calendar.AttendanceStatus;
import com.couponwith.calendar.CalendarEvent;
import com.couponwith.calendar.CalendarEventRepository;
import com.couponwith.calendar.EventAttendeeRepository;
import com.couponwith.calendar.EventRecurrence;
import com.couponwith.calendar.EventReminderDelivery;
import com.couponwith.calendar.EventReminderDeliveryRepository;
import com.couponwith.calendar.EventReminderRepository;
import com.couponwith.coupon.CouponRepository;
import com.couponwith.coupon.CouponStatus;
import com.couponwith.notification.NotificationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ScheduledAutomationService {
    private final CouponRepository coupons;
    private final CalendarEventRepository events;
    private final EventReminderRepository reminders;
    private final EventAttendeeRepository attendees;
    private final EventReminderDeliveryRepository deliveries;
    private final NotificationService notifications;
    private final long reminderLookbackMinutes;
    private final AuditService audits;
    private final Duration couponClaimTimeout;

    public ScheduledAutomationService(CouponRepository coupons, CalendarEventRepository events,
                                      EventReminderRepository reminders, EventAttendeeRepository attendees,
                                      EventReminderDeliveryRepository deliveries, NotificationService notifications,
                                      AuditService audits,
                                      @Value("${moaday.automation.reminder-lookback-minutes:10}") long reminderLookbackMinutes,
                                      @Value("${moaday.coupons.claim-minutes:15}") long couponClaimMinutes) {
        this.coupons = coupons;
        this.events = events;
        this.reminders = reminders;
        this.attendees = attendees;
        this.deliveries = deliveries;
        this.notifications = notifications;
        this.audits = audits;
        this.reminderLookbackMinutes = Math.max(1, reminderLookbackMinutes);
        this.couponClaimTimeout = Duration.ofMinutes(Math.max(1, couponClaimMinutes));
    }

    @Scheduled(fixedDelayString = "${moaday.automation.interval-ms:60000}",
            initialDelayString = "${moaday.automation.initial-delay-ms:30000}")
    @Transactional
    public void expireCoupons() {
        expireCouponsAt(Instant.now());
    }

    @Scheduled(fixedDelayString = "${moaday.automation.interval-ms:60000}",
            initialDelayString = "${moaday.automation.initial-delay-ms:30000}")
    @Transactional
    public void sendEventReminders() {
        sendEventRemindersAt(Instant.now());
    }

    @Scheduled(fixedDelayString = "${moaday.automation.interval-ms:60000}",
            initialDelayString = "${moaday.automation.initial-delay-ms:30000}")
    @Transactional
    public void releaseCouponClaims() {
        releaseCouponClaimsAt(Instant.now());
    }

    public int releaseCouponClaimsAt(Instant now) {
        var released = 0;
        for (var coupon : coupons.findByStatusAndClaimedAtLessThanEqual(
                CouponStatus.CLAIMED, now.minus(couponClaimTimeout))) {
            var claimant = coupon.getClaimedBy();
            if (!coupon.releaseClaimIfTimedOut(now, couponClaimTimeout)) continue;
            released++;
            audits.recordAt(coupon.getSpaceId(), null, "COUPON_AUTO_RELEASED", "COUPON", coupon.getId(),
                    coupon.getTitle() + " 쿠폰 선점 시간 만료", null, now);
            if (claimant != null) notifications.notifyUser(claimant, coupon.getSpaceId(), "COUPON_AUTO_RELEASED",
                    "쿠폰 선점 자동 해제", coupon.getTitle() + " 쿠폰의 15분 선점 시간이 끝났습니다.",
                    "/coupons/" + coupon.getId());
        }
        return released;
    }

    public int expireCouponsAt(Instant now) {
        var expired = 0;
        for (var coupon : coupons.findByStatusInAndExpiresAtLessThanEqual(
                List.of(CouponStatus.AVAILABLE, CouponStatus.CLAIMED), now)) {
            var claimant = coupon.getClaimedBy();
            if (!coupon.expireIfNeeded(now)) continue;
            expired++;
            audits.recordAt(coupon.getSpaceId(), null, "COUPON_EXPIRED", "COUPON", coupon.getId(),
                    coupon.getTitle() + " 쿠폰 만료", null, now);
            notifications.notifyUser(coupon.getOwnerId(), coupon.getSpaceId(), "COUPON_EXPIRED",
                    "쿠폰 만료", coupon.getTitle() + " 쿠폰이 만료되었습니다.", "/coupons/" + coupon.getId());
            if (claimant != null && !claimant.equals(coupon.getOwnerId())) {
                notifications.notifyUser(claimant, coupon.getSpaceId(), "COUPON_EXPIRED",
                        "선점 쿠폰 만료", coupon.getTitle() + " 쿠폰이 만료되었습니다.", "/coupons/" + coupon.getId());
            }
        }
        return expired;
    }

    public int sendEventRemindersAt(Instant now) {
        var delivered = 0;
        var windowStart = now.minus(reminderLookbackMinutes, ChronoUnit.MINUTES);
        for (var event : events.findAll()) {
            for (var reminder : reminders.findByEventIdOrderByMinutesBefore(event.getId())) {
                var occurrenceFrom = windowStart.plus(reminder.getMinutesBefore(), ChronoUnit.MINUTES);
                var occurrenceTo = now.plus(reminder.getMinutesBefore(), ChronoUnit.MINUTES).plusSeconds(1);
                for (var occurrence : occurrenceStarts(event, occurrenceFrom, occurrenceTo)) {
                    for (var attendee : attendees.findByEventIdOrderByUserId(event.getId())) {
                        if (attendee.getResponse() == AttendanceStatus.DECLINED) continue;
                        if (deliveries.existsByReminderIdAndOccurrenceStartsAtAndUserId(
                                reminder.getId(), occurrence, attendee.getUserId())) continue;
                        deliveries.save(new EventReminderDelivery(UUID.randomUUID(), reminder.getId(), occurrence,
                                attendee.getUserId(), now));
                        notifications.notifyUser(attendee.getUserId(), event.getSpaceId(), "EVENT_REMINDER",
                                "일정 알림", reminderMessage(event, occurrence, reminder.getMinutesBefore()),
                                "/events/" + event.getId());
                        delivered++;
                    }
                }
            }
        }
        return delivered;
    }

    private String reminderMessage(CalendarEvent event, Instant occurrence, int minutesBefore) {
        var prefix = minutesBefore == 0 ? "지금 시작합니다." : minutesBefore + "분 후 시작합니다.";
        return event.getTitle() + " 일정이 " + prefix + " (" + occurrence.atZone(ZoneId.of(event.getTimezone())) + ")";
    }

    private List<Instant> occurrenceStarts(CalendarEvent event, Instant from, Instant to) {
        if (event.getRecurrence() == EventRecurrence.NONE) {
            return !event.getStartsAt().isBefore(from) && event.getStartsAt().isBefore(to)
                    ? List.of(event.getStartsAt()) : List.of();
        }
        var zone = ZoneId.of(event.getTimezone());
        var base = event.getStartsAt().atZone(zone);
        var fromZoned = from.atZone(zone);
        var index = firstIndex(base, fromZoned, event.getRecurrence());
        var result = new ArrayList<Instant>();
        for (int guard = 0; guard < 5000; guard++, index++) {
            var start = add(base, event.getRecurrence(), index).toInstant();
            if (!start.isBefore(to)) break;
            if (event.getRecurrenceUntil() != null && start.isAfter(event.getRecurrenceUntil())) break;
            if (!start.isBefore(from)) result.add(start);
        }
        return result;
    }

    private long firstIndex(ZonedDateTime base, ZonedDateTime from, EventRecurrence recurrence) {
        long approximate = switch (recurrence) {
            case DAILY -> ChronoUnit.DAYS.between(base.toLocalDate(), from.toLocalDate());
            case WEEKLY -> ChronoUnit.WEEKS.between(base.toLocalDate(), from.toLocalDate());
            case MONTHLY -> ChronoUnit.MONTHS.between(base.toLocalDate().withDayOfMonth(1), from.toLocalDate().withDayOfMonth(1));
            case YEARLY -> ChronoUnit.YEARS.between(base.toLocalDate().withDayOfYear(1), from.toLocalDate().withDayOfYear(1));
            case NONE -> 0;
        };
        return Math.max(0, approximate - 1);
    }

    private ZonedDateTime add(ZonedDateTime base, EventRecurrence recurrence, long index) {
        return switch (recurrence) {
            case DAILY -> base.plusDays(index);
            case WEEKLY -> base.plusWeeks(index);
            case MONTHLY -> base.plusMonths(index);
            case YEARLY -> base.plusYears(index);
            case NONE -> base;
        };
    }
}
