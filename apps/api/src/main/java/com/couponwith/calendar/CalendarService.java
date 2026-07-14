package com.couponwith.calendar;

import com.couponwith.common.ApiException;
import com.couponwith.identity.UserRepository;
import com.couponwith.notification.NotificationService;
import com.couponwith.space.Space;
import com.couponwith.space.SpaceMember;
import com.couponwith.space.SpaceMemberRepository;
import com.couponwith.space.SpaceRepository;
import com.couponwith.space.SpaceRole;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class CalendarService {
    private static final Duration MAX_RANGE = Duration.ofDays(370);

    private final SharedCalendarRepository calendars;
    private final CalendarEventRepository events;
    private final EventAttendeeRepository attendees;
    private final EventReminderRepository reminders;
    private final SpaceRepository spaces;
    private final SpaceMemberRepository members;
    private final UserRepository users;
    private final NotificationService notifications;

    public CalendarService(SharedCalendarRepository calendars, CalendarEventRepository events,
                           EventAttendeeRepository attendees, EventReminderRepository reminders,
                           SpaceRepository spaces, SpaceMemberRepository members, UserRepository users, NotificationService notifications) {
        this.calendars = calendars;
        this.events = events;
        this.attendees = attendees;
        this.reminders = reminders;
        this.spaces = spaces;
        this.members = members;
        this.users = users;
        this.notifications = notifications;
    }

    @Transactional
    public List<CalendarView> listCalendars(UUID actorId, UUID spaceId) {
        requireMembership(spaceId, actorId);
        var found = calendars.findBySpaceIdOrderByCreatedAt(spaceId);
        if (found.isEmpty()) {
            var space = requireSpace(spaceId);
            found = List.of(calendars.save(new SharedCalendar(UUID.randomUUID(), spaceId, "기본 캘린더",
                    space.getColor(), actorId)));
        }
        return found.stream().map(CalendarView::from).toList();
    }

    @Transactional
    public CalendarView createCalendar(UUID actorId, UUID spaceId, String name, String color) {
        requireManager(spaceId, actorId);
        var normalizedName = name.trim();
        if (calendars.existsBySpaceIdAndNameIgnoreCase(spaceId, normalizedName)) {
            throw new ApiException(HttpStatus.CONFLICT, "CALENDAR_NAME_EXISTS", "같은 이름의 캘린더가 이미 있습니다.");
        }
        return CalendarView.from(calendars.save(new SharedCalendar(UUID.randomUUID(), spaceId, normalizedName,
                color.trim(), actorId)));
    }

    @Transactional
    public CalendarView updateCalendar(UUID actorId, UUID calendarId, String name, String color) {
        var calendar = requireCalendar(calendarId);
        requireManager(calendar.getSpaceId(), actorId);
        var normalizedName = name.trim();
        if (!calendar.getName().equalsIgnoreCase(normalizedName)
                && calendars.existsBySpaceIdAndNameIgnoreCase(calendar.getSpaceId(), normalizedName)) {
            throw new ApiException(HttpStatus.CONFLICT, "CALENDAR_NAME_EXISTS", "같은 이름의 캘린더가 이미 있습니다.");
        }
        calendar.update(normalizedName, color.trim());
        return CalendarView.from(calendar);
    }

    @Transactional
    public void deleteCalendar(UUID actorId, UUID calendarId) {
        var calendar = requireCalendar(calendarId);
        requireManager(calendar.getSpaceId(), actorId);
        if (events.existsByCalendarId(calendarId)) {
            throw new ApiException(HttpStatus.CONFLICT, "CALENDAR_NOT_EMPTY", "일정이 있는 캘린더는 삭제할 수 없습니다.");
        }
        calendars.delete(calendar);
    }

    @Transactional(readOnly = true)
    public List<EventOccurrenceView> listEvents(UUID actorId, UUID spaceId, Instant from, Instant to) {
        requireMembership(spaceId, actorId);
        validateRange(from, to);
        var result = new ArrayList<EventOccurrenceView>();
        for (var event : events.findBySpaceIdAndStartsAtLessThanOrderByStartsAt(spaceId, to)) {
            var details = details(event, actorId);
            expand(event, from, to).forEach(occurrence -> result.add(EventOccurrenceView.from(
                    details, occurrence.start(), occurrence.end(), occurrence.id())));
        }
        result.sort(Comparator.comparing(EventOccurrenceView::startsAt).thenComparing(EventOccurrenceView::title));
        return result;
    }

    @Transactional(readOnly = true)
    public EventView getEvent(UUID actorId, UUID eventId) {
        var event = requireEvent(eventId);
        requireMembership(event.getSpaceId(), actorId);
        return details(event, actorId);
    }

    @Transactional
    public EventView createEvent(UUID actorId, UUID calendarId, EventInput input) {
        var calendar = requireCalendar(calendarId);
        requireEditor(calendar.getSpaceId(), actorId);
        validateInput(input, calendar.getSpaceId());
        var event = events.save(new CalendarEvent(UUID.randomUUID(), calendarId, calendar.getSpaceId(),
                input.title().trim(), clean(input.description()), clean(input.location()), clean(input.externalUrl()),
                input.allDay(), input.startsAt(), input.endsAt(), input.timezone(), input.recurrence(),
                input.recurrenceUntil(), actorId));
        replaceAttendees(event, actorId, input.attendeeUserIds(), true);
        replaceReminders(event.getId(), input.reminderMinutes());
        if (input.attendeeUserIds() != null) input.attendeeUserIds().stream().filter(id -> !id.equals(actorId)).forEach(id -> notifications.notifyUser(id, event.getSpaceId(), "EVENT_INVITATION", "새 일정 초대", event.getTitle(), "/events/" + event.getId()));
        return details(event, actorId);
    }

    @Transactional
    public EventView updateEvent(UUID actorId, UUID eventId, EventInput input) {
        var event = requireEvent(eventId);
        requireEventMutation(event, actorId);
        var calendar = requireCalendar(input.calendarId());
        if (!calendar.getSpaceId().equals(event.getSpaceId())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "CALENDAR_SPACE_MISMATCH", "같은 공간의 캘린더로만 이동할 수 있습니다.");
        }
        validateInput(input, event.getSpaceId());
        event.update(calendar.getId(), input.title().trim(), clean(input.description()), clean(input.location()),
                clean(input.externalUrl()), input.allDay(), input.startsAt(), input.endsAt(), input.timezone(),
                input.recurrence(), input.recurrenceUntil());
        replaceAttendees(event, actorId, input.attendeeUserIds(), false);
        replaceReminders(event.getId(), input.reminderMinutes());
        return details(event, actorId);
    }

    @Transactional
    public void deleteEvent(UUID actorId, UUID eventId) {
        var event = requireEvent(eventId);
        requireEventMutation(event, actorId);
        events.delete(event);
    }

    @Transactional
    public EventView respond(UUID actorId, UUID eventId, AttendanceStatus response) {
        if (response == AttendanceStatus.PENDING) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_ATTENDANCE_RESPONSE", "참석, 불참 또는 미정으로 응답해 주세요.");
        }
        var event = requireEvent(eventId);
        requireMembership(event.getSpaceId(), actorId);
        var attendee = attendees.findById(new EventAttendeeId(eventId, actorId))
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "NOT_AN_ATTENDEE", "이 일정의 참석자로 지정되지 않았습니다."));
        attendee.respond(response);
        if (!event.getCreatedBy().equals(actorId)) notifications.notifyUser(event.getCreatedBy(), event.getSpaceId(), "EVENT_ATTENDANCE", "참석 응답", "일정 참석 응답이 변경되었습니다.", "/events/" + event.getId());
        return details(event, actorId);
    }

    private void validateRange(Instant from, Instant to) {
        if (from == null || to == null || !to.isAfter(from)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_EVENT_RANGE", "올바른 조회 시작일과 종료일을 입력해 주세요.");
        }
        if (Duration.between(from, to).compareTo(MAX_RANGE) > 0) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "EVENT_RANGE_TOO_LARGE", "일정은 한 번에 최대 370일까지 조회할 수 있습니다.");
        }
    }

    private void validateInput(EventInput input, UUID spaceId) {
        if (input.startsAt() == null || input.endsAt() == null || !input.endsAt().isAfter(input.startsAt())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_EVENT_TIME", "종료 시각은 시작 시각보다 늦어야 합니다.");
        }
        try { ZoneId.of(input.timezone()); }
        catch (Exception ignored) { throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_TIMEZONE", "올바른 시간대를 입력해 주세요."); }
        if (input.externalUrl() != null && !input.externalUrl().isBlank()) {
            try {
                var scheme = URI.create(input.externalUrl().trim()).getScheme();
                if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) throw new IllegalArgumentException();
            } catch (IllegalArgumentException ignored) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_EVENT_URL", "일정 링크는 http 또는 https 주소만 사용할 수 있습니다.");
            }
        }
        if (input.recurrence() != EventRecurrence.NONE && input.recurrenceUntil() != null
                && input.recurrenceUntil().isBefore(input.startsAt())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_RECURRENCE_END", "반복 종료일은 일정 시작일 이후여야 합니다.");
        }
        if (input.recurrence() == EventRecurrence.NONE && input.recurrenceUntil() != null) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "RECURRENCE_END_NOT_ALLOWED", "반복하지 않는 일정에는 반복 종료일을 지정할 수 없습니다.");
        }
        if (input.attendeeUserIds() != null && input.attendeeUserIds().size() > 100) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "TOO_MANY_ATTENDEES", "참석자는 최대 100명까지 지정할 수 있습니다.");
        }
        if (input.reminderMinutes() != null && (input.reminderMinutes().size() > 5
                || input.reminderMinutes().stream().anyMatch(value -> value == null || value < 0 || value > 10080))) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_REMINDER", "알림은 0분부터 7일 전까지 최대 5개를 설정할 수 있습니다.");
        }
        if (input.attendeeUserIds() != null) {
            input.attendeeUserIds().forEach(userId -> requireMembership(spaceId, userId));
        }
    }

    private void replaceAttendees(CalendarEvent event, UUID actorId, Set<UUID> requested, boolean creating) {
        var desired = new LinkedHashSet<>(requested == null ? Set.<UUID>of() : requested);
        desired.add(event.getCreatedBy());
        var existing = attendees.findByEventIdOrderByUserId(event.getId()).stream()
                .collect(java.util.stream.Collectors.toMap(EventAttendee::getUserId, item -> item));
        attendees.deleteByEventId(event.getId());
        for (var userId : desired) {
            var previous = existing.get(userId);
            var status = previous == null ? (userId.equals(event.getCreatedBy()) ? AttendanceStatus.ACCEPTED : AttendanceStatus.PENDING)
                    : previous.getResponse();
            if (creating && userId.equals(actorId)) status = AttendanceStatus.ACCEPTED;
            attendees.save(new EventAttendee(event.getId(), userId, status));
        }
    }

    private void replaceReminders(UUID eventId, Set<Integer> values) {
        reminders.deleteByEventId(eventId);
        if (values == null) return;
        values.stream().distinct().sorted().forEach(value -> reminders.save(new EventReminder(UUID.randomUUID(), eventId, value)));
    }

    private EventView details(CalendarEvent event, UUID actorId) {
        var calendar = requireCalendar(event.getCalendarId());
        var attendeeViews = attendees.findByEventIdOrderByUserId(event.getId()).stream().map(attendee -> {
            var user = users.findById(attendee.getUserId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "참석자를 찾을 수 없습니다."));
            return new AttendeeView(user.getId(), user.getDisplayName(), user.getEmail(), attendee.getResponse(),
                    user.getId().equals(actorId));
        }).toList();
        var reminderValues = reminders.findByEventIdOrderByMinutesBefore(event.getId()).stream()
                .map(EventReminder::getMinutesBefore).toList();
        return new EventView(event.getId(), event.getCalendarId(), calendar.getName(), calendar.getColor(),
                event.getSpaceId(), event.getTitle(), event.getDescription(), event.getLocation(), event.getExternalUrl(),
                event.isAllDay(), event.getStartsAt(), event.getEndsAt(), event.getTimezone(), event.getRecurrence(),
                event.getRecurrenceUntil(), event.getCreatedBy(), event.getVersion(), attendeeViews, reminderValues,
                canMutate(event, actorId));
    }

    private List<Occurrence> expand(CalendarEvent event, Instant from, Instant to) {
        var duration = Duration.between(event.getStartsAt(), event.getEndsAt());
        if (event.getRecurrence() == EventRecurrence.NONE) {
            return event.getEndsAt().isAfter(from) && event.getStartsAt().isBefore(to)
                    ? List.of(new Occurrence(event.getId().toString(), event.getStartsAt(), event.getEndsAt())) : List.of();
        }
        var zone = ZoneId.of(event.getTimezone());
        var base = event.getStartsAt().atZone(zone);
        var fromZoned = from.atZone(zone);
        long index = firstIndex(base, fromZoned, event.getRecurrence());
        var result = new ArrayList<Occurrence>();
        for (int guard = 0; guard < 5000; guard++, index++) {
            var start = add(base, event.getRecurrence(), index).toInstant();
            if (!start.isBefore(to)) break;
            if (event.getRecurrenceUntil() != null && start.isAfter(event.getRecurrenceUntil())) break;
            var end = start.plus(duration);
            if (end.isAfter(from)) result.add(new Occurrence(event.getId() + ":" + start.toEpochMilli(), start, end));
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

    private void requireEventMutation(CalendarEvent event, UUID actorId) {
        requireMembership(event.getSpaceId(), actorId);
        if (!canMutate(event, actorId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "EVENT_MUTATION_NOT_ALLOWED", "이 일정을 수정하거나 삭제할 권한이 없습니다.");
        }
    }

    private boolean canMutate(CalendarEvent event, UUID actorId) {
        var member = requireMembership(event.getSpaceId(), actorId);
        return event.getCreatedBy().equals(actorId) || member.getRole() == SpaceRole.OWNER || member.getRole() == SpaceRole.ADMIN;
    }

    private SpaceMember requireEditor(UUID spaceId, UUID actorId) {
        var member = requireMembership(spaceId, actorId);
        if (member.getRole() == SpaceRole.VIEWER) {
            throw new ApiException(HttpStatus.FORBIDDEN, "EVENT_CREATE_NOT_ALLOWED", "열람자는 일정을 만들 수 없습니다.");
        }
        return member;
    }

    private SpaceMember requireManager(UUID spaceId, UUID actorId) {
        var member = requireMembership(spaceId, actorId);
        if (member.getRole() != SpaceRole.OWNER && member.getRole() != SpaceRole.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "CALENDAR_MANAGEMENT_NOT_ALLOWED", "캘린더를 관리할 권한이 없습니다.");
        }
        return member;
    }

    private SpaceMember requireMembership(UUID spaceId, UUID userId) {
        return members.findBySpaceIdAndUserIdAndStatus(spaceId, userId, "ACTIVE")
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SPACE_NOT_FOUND", "공간을 찾을 수 없습니다."));
    }

    private Space requireSpace(UUID spaceId) {
        return spaces.findById(spaceId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SPACE_NOT_FOUND", "공간을 찾을 수 없습니다."));
    }

    private SharedCalendar requireCalendar(UUID calendarId) {
        return calendars.findById(calendarId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CALENDAR_NOT_FOUND", "캘린더를 찾을 수 없습니다."));
    }

    private CalendarEvent requireEvent(UUID eventId) {
        return events.findById(eventId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "일정을 찾을 수 없습니다."));
    }

    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private record Occurrence(String id, Instant start, Instant end) {}

    public record EventInput(UUID calendarId, String title, String description, String location, String externalUrl,
                             boolean allDay, Instant startsAt, Instant endsAt, String timezone,
                             EventRecurrence recurrence, Instant recurrenceUntil, Set<UUID> attendeeUserIds,
                             Set<Integer> reminderMinutes) {}
    public record CalendarView(UUID id, UUID spaceId, String name, String color) {
        static CalendarView from(SharedCalendar calendar) {
            return new CalendarView(calendar.getId(), calendar.getSpaceId(), calendar.getName(), calendar.getColor());
        }
    }
    public record AttendeeView(UUID userId, String displayName, String email, AttendanceStatus response, boolean currentUser) {}
    public record EventView(UUID id, UUID calendarId, String calendarName, String calendarColor, UUID spaceId,
                            String title, String description, String location, String externalUrl, boolean allDay,
                            Instant startsAt, Instant endsAt, String timezone, EventRecurrence recurrence,
                            Instant recurrenceUntil, UUID createdBy, long version, List<AttendeeView> attendees,
                            List<Integer> reminderMinutes, boolean canEdit) {}
    public record EventOccurrenceView(String occurrenceId, UUID eventId, UUID calendarId, String calendarName,
                                      String calendarColor, UUID spaceId, String title, String description,
                                      String location, boolean allDay, Instant startsAt, Instant endsAt,
                                      EventRecurrence recurrence, Instant recurrenceUntil, List<AttendeeView> attendees,
                                      List<Integer> reminderMinutes, boolean canEdit) {
        static EventOccurrenceView from(EventView event, Instant start, Instant end, String occurrenceId) {
            return new EventOccurrenceView(occurrenceId, event.id(), event.calendarId(), event.calendarName(),
                    event.calendarColor(), event.spaceId(), event.title(), event.description(), event.location(),
                    event.allDay(), start, end, event.recurrence(), event.recurrenceUntil(), event.attendees(),
                    event.reminderMinutes(), event.canEdit());
        }
    }
}
