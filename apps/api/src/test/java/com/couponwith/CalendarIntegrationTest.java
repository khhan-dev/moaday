package com.couponwith;

import com.couponwith.TestAccounts;
import com.couponwith.calendar.AttendanceStatus;
import com.couponwith.calendar.CalendarService;
import com.couponwith.calendar.EventRecurrence;
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
class CalendarIntegrationTest {
    @Autowired AuthService authService;
    @Autowired TestAccounts testAccounts;
    @Autowired SpaceService spaceService;
    @Autowired CalendarService calendarService;

    @Test
    void recurringEventCrudAttendanceAndReminderFlow() {
        var owner = testAccounts.register("calendar-owner@example.com", "password123!", "일정 주인", "Asia/Seoul");
        var member = testAccounts.register("calendar-member@example.com", "password123!", "일정 멤버", "Asia/Seoul");
        var family = spaceService.create(owner.user().id(), SpaceType.FAMILY, "일정 가족", "Asia/Seoul", "orange");
        var invitation = spaceService.invite(owner.user().id(), family.id(), member.user().email(), SpaceRole.MEMBER);
        spaceService.accept(member.user().id(), invitation.oneTimeToken());
        var calendar = calendarService.listCalendars(owner.user().id(), family.id()).getFirst();

        var start = Instant.now().truncatedTo(ChronoUnit.MINUTES).plus(1, ChronoUnit.DAYS);
        var created = calendarService.createEvent(owner.user().id(), calendar.id(), new CalendarService.EventInput(
                calendar.id(), "가족 운동", "함께 걷기", "한강", null, false, start, start.plus(1, ChronoUnit.HOURS),
                "Asia/Seoul", EventRecurrence.DAILY, start.plus(2, ChronoUnit.DAYS),
                Set.of(member.user().id()), Set.of(10, 30)));

        assertThat(created.reminderMinutes()).containsExactly(10, 30);
        assertThat(created.attendees()).hasSize(2);
        assertThat(calendarService.listEvents(owner.user().id(), family.id(), start.minusSeconds(1), start.plus(4, ChronoUnit.DAYS)))
                .hasSize(3)
                .extracting(CalendarService.EventOccurrenceView::title)
                .containsOnly("가족 운동");

        var responded = calendarService.respond(member.user().id(), created.id(), AttendanceStatus.ACCEPTED);
        assertThat(responded.attendees()).filteredOn(CalendarService.AttendeeView::currentUser).singleElement()
                .extracting(CalendarService.AttendeeView::response).isEqualTo(AttendanceStatus.ACCEPTED);

        var updated = calendarService.updateEvent(owner.user().id(), created.id(), new CalendarService.EventInput(
                calendar.id(), "가족 산책", null, null, null, false, start, start.plus(2, ChronoUnit.HOURS),
                "Asia/Seoul", EventRecurrence.NONE, null, Set.of(member.user().id()), Set.of(60)));
        assertThat(updated.title()).isEqualTo("가족 산책");
        assertThat(updated.reminderMinutes()).containsExactly(60);

        calendarService.deleteEvent(owner.user().id(), created.id());
        assertThat(calendarService.listEvents(owner.user().id(), family.id(), start.minusSeconds(1), start.plus(1, ChronoUnit.DAYS))).isEmpty();
    }

    @Test
    void viewerCanReadAndRespondButCannotCreateEvents() {
        var owner = testAccounts.register("viewer-owner@example.com", "password123!", "소유자", "Asia/Seoul");
        var viewer = testAccounts.register("calendar-viewer@example.com", "password123!", "열람자", "Asia/Seoul");
        var friends = spaceService.create(owner.user().id(), SpaceType.FRIENDS, "친구 일정", "Asia/Seoul", "green");
        var invitation = spaceService.invite(owner.user().id(), friends.id(), viewer.user().email(), SpaceRole.VIEWER);
        spaceService.accept(viewer.user().id(), invitation.oneTimeToken());
        var calendar = calendarService.listCalendars(owner.user().id(), friends.id()).getFirst();
        var start = Instant.now().plus(1, ChronoUnit.HOURS);
        var input = new CalendarService.EventInput(calendar.id(), "영화", null, null, null, false, start,
                start.plus(2, ChronoUnit.HOURS), "Asia/Seoul", EventRecurrence.NONE, null,
                Set.of(viewer.user().id()), Set.of());
        var event = calendarService.createEvent(owner.user().id(), calendar.id(), input);

        assertThat(calendarService.listEvents(viewer.user().id(), friends.id(), start.minusSeconds(1), start.plus(3, ChronoUnit.HOURS))).hasSize(1);
        assertThat(calendarService.respond(viewer.user().id(), event.id(), AttendanceStatus.MAYBE).attendees())
                .filteredOn(CalendarService.AttendeeView::currentUser).singleElement()
                .extracting(CalendarService.AttendeeView::response).isEqualTo(AttendanceStatus.MAYBE);
        assertThatThrownBy(() -> calendarService.createEvent(viewer.user().id(), calendar.id(), input))
                .hasMessageContaining("열람자는 일정을 만들 수 없습니다");
        assertThatThrownBy(() -> calendarService.updateEvent(viewer.user().id(), event.id(), input))
                .hasMessageContaining("수정하거나 삭제할 권한");
    }
}
