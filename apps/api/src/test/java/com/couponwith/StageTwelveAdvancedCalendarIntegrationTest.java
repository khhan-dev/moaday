package com.couponwith;

import com.couponwith.TestAccounts;
import com.couponwith.calendar.CalendarService;
import com.couponwith.calendar.EventRecurrence;
import com.couponwith.calendar.IcsCalendarService;
import com.couponwith.calendar.OccurrenceExceptionAction;
import com.couponwith.identity.AuthService;
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
class StageTwelveAdvancedCalendarIntegrationTest {
    @Autowired AuthService auth;
    @Autowired TestAccounts testAccounts;
    @Autowired SpaceService spaces;
    @Autowired CalendarService calendars;
    @Autowired IcsCalendarService ics;

    @Test
    void recurringOccurrencesCanBeOverriddenCancelledAndRestored() {
        var group = group("occurrence");
        var calendar = calendars.listCalendars(group.owner.user().id(), group.space.id()).getFirst();
        var start = Instant.parse("2026-07-20T01:00:00Z");
        var event = calendars.createEvent(group.owner.user().id(), calendar.id(), new CalendarService.EventInput(
                calendar.id(), "아침 운동", "기본 설명", "공원", null, false, start, start.plus(1, ChronoUnit.HOURS),
                "Asia/Seoul", EventRecurrence.DAILY, start.plus(2, ChronoUnit.DAYS), Set.of(group.member.user().id()), Set.of(30)));
        var second = start.plus(1, ChronoUnit.DAYS);
        var third = start.plus(2, ChronoUnit.DAYS);

        calendars.overrideOccurrence(group.owner.user().id(), event.id(), second, new CalendarService.OccurrenceInput(
                "도쿄에서 운동", "한 회차만 변경", "요요기 공원", false,
                second.plus(2, ChronoUnit.HOURS), second.plus(3, ChronoUnit.HOURS), "Asia/Tokyo"));
        calendars.cancelOccurrence(group.owner.user().id(), event.id(), third);

        var occurrences = calendars.listEvents(group.member.user().id(), group.space.id(), start.minusSeconds(1), start.plus(4, ChronoUnit.DAYS));
        assertThat(occurrences).hasSize(2);
        assertThat(occurrences).filteredOn(item -> item.originalStartsAt().equals(second)).singleElement().satisfies(item -> {
            assertThat(item.title()).isEqualTo("도쿄에서 운동");
            assertThat(item.timezone()).isEqualTo("Asia/Tokyo");
            assertThat(item.exceptionAction()).isEqualTo(OccurrenceExceptionAction.OVERRIDE);
        });
        assertThatThrownBy(() -> calendars.cancelOccurrence(group.member.user().id(), event.id(), second))
                .hasMessageContaining("권한");

        var exported = ics.exportSpace(group.owner.user().id(), group.space.id());
        assertThat(exported).contains("RRULE:FREQ=DAILY", "EXDATE:20260722T010000Z", "RECURRENCE-ID:20260721T010000Z", "SUMMARY:도쿄에서 운동");

        calendars.restoreOccurrence(group.owner.user().id(), event.id(), second);
        assertThat(calendars.listEvents(group.owner.user().id(), group.space.id(), start.minusSeconds(1), start.plus(4, ChronoUnit.DAYS)))
                .filteredOn(item -> item.originalStartsAt().equals(second)).singleElement()
                .extracting(CalendarService.EventOccurrenceView::title).isEqualTo("아침 운동");
    }

    @Test
    void icsImportPreservesTimezoneRecurrenceAndSkipsDuplicateUid() {
        var group = group("ics");
        var calendar = calendars.listCalendars(group.owner.user().id(), group.space.id()).getFirst();
        var uid = "external-" + System.nanoTime() + "@example.com";
        var content = "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nBEGIN:VEVENT\r\nUID:" + uid
                + "\r\nSUMMARY:뉴욕 주간 회의\r\nDESCRIPTION:ICS 가져오기 점검\r\nLOCATION:Online\r\n"
                + "DTSTART;TZID=America/New_York:20260720T090000\r\nDTEND;TZID=America/New_York:20260720T100000\r\n"
                + "RRULE:FREQ=WEEKLY;UNTIL=20260820T130000Z\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n";

        var imported = ics.importCalendar(group.owner.user().id(), calendar.id(), content);
        var duplicate = ics.importCalendar(group.owner.user().id(), calendar.id(), content);

        assertThat(imported.imported()).isEqualTo(1);
        assertThat(imported.skipped()).isZero();
        assertThat(duplicate.imported()).isZero();
        assertThat(duplicate.skipped()).isEqualTo(1);
        assertThat(calendars.listEvents(group.owner.user().id(), group.space.id(),
                Instant.parse("2026-07-20T00:00:00Z"), Instant.parse("2026-08-21T00:00:00Z")))
                .isNotEmpty().allSatisfy(item -> {
                    assertThat(item.timezone()).isEqualTo("America/New_York");
                    assertThat(item.recurrence()).isEqualTo(EventRecurrence.WEEKLY);
                });
        assertThat(ics.exportSpace(group.owner.user().id(), group.space.id())).contains("UID:" + uid, "RRULE:FREQ=WEEKLY");
    }

    private Group group(String prefix) {
        var suffix = prefix + System.nanoTime();
        var owner = testAccounts.register(suffix + "-owner@example.com", "password123!", "소유자", "Asia/Seoul");
        var member = testAccounts.register(suffix + "-member@example.com", "password123!", "멤버", "Asia/Seoul");
        var space = spaces.create(owner.user().id(), SpaceType.FAMILY, "고급 캘린더", "Asia/Seoul", "green");
        var invitation = spaces.invite(owner.user().id(), space.id(), member.user().email(), SpaceRole.MEMBER);
        spaces.accept(member.user().id(), invitation.oneTimeToken());
        return new Group(owner, member, space);
    }

    private record Group(AuthService.AuthResult owner, AuthService.AuthResult member, SpaceService.SpaceView space) {}
}
