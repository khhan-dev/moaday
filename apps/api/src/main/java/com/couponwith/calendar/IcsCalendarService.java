package com.couponwith.calendar;

import com.couponwith.audit.AuditService;
import com.couponwith.common.ApiException;
import com.couponwith.space.SpaceMemberRepository;
import com.couponwith.space.SpaceRepository;
import com.couponwith.space.SpaceRole;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class IcsCalendarService {
    private static final int MAX_ICS_CHARS = 2_000_000;
    private static final int MAX_IMPORT_EVENTS = 200;
    private static final DateTimeFormatter UTC_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter LOCAL_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    private final SharedCalendarRepository calendars;
    private final CalendarEventRepository events;
    private final EventAttendeeRepository attendees;
    private final EventOccurrenceExceptionRepository exceptions;
    private final SpaceMemberRepository members;
    private final SpaceRepository spaces;
    private final AuditService audits;

    public IcsCalendarService(SharedCalendarRepository calendars, CalendarEventRepository events,
                              EventAttendeeRepository attendees, EventOccurrenceExceptionRepository exceptions,
                              SpaceMemberRepository members, SpaceRepository spaces, AuditService audits) {
        this.calendars = calendars; this.events = events; this.attendees = attendees; this.exceptions = exceptions;
        this.members = members; this.spaces = spaces; this.audits = audits;
    }

    @Transactional(readOnly = true)
    public String exportSpace(UUID actorId, UUID spaceId) {
        requireMembership(spaceId, actorId);
        var space = spaces.findById(spaceId).orElseThrow(() -> notFound("공간"));
        var output = new StringBuilder("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nCALSCALE:GREGORIAN\r\n");
        line(output, "PRODID:-//MoaDay//Calendar 1.0//KO");
        line(output, "X-WR-CALNAME:" + escape(space.getName()));
        line(output, "X-WR-TIMEZONE:" + space.getTimezone());
        for (var event : events.findBySpaceIdOrderByStartsAt(spaceId)) {
            var eventExceptions = exceptions.findByEventIdOrderByOriginalStartsAt(event.getId());
            appendEvent(output, event, null);
            var cancelled = eventExceptions.stream().filter(item -> item.getAction() == OccurrenceExceptionAction.CANCELLED).toList();
            if (!cancelled.isEmpty()) {
                // EXDATE must be part of the master VEVENT, so insert it immediately before its END marker.
                var endIndex = output.lastIndexOf("END:VEVENT\r\n");
                var values = cancelled.stream().map(item -> event.isAllDay()
                        ? DATE_FORMAT.format(item.getOriginalStartsAt().atZone(ZoneId.of(event.getTimezone())).toLocalDate())
                        : UTC_FORMAT.format(item.getOriginalStartsAt())).reduce((a, b) -> a + "," + b).orElse("");
                output.insert(endIndex, (event.isAllDay() ? "EXDATE;VALUE=DATE:" : "EXDATE:") + values + "\r\n");
            }
            eventExceptions.stream().filter(item -> item.getAction() == OccurrenceExceptionAction.OVERRIDE)
                    .forEach(item -> appendEvent(output, event, item));
        }
        output.append("END:VCALENDAR\r\n");
        return output.toString();
    }

    @Transactional
    public ImportResult importCalendar(UUID actorId, UUID calendarId, String ics) {
        var calendar = calendars.findById(calendarId).orElseThrow(() -> notFound("캘린더"));
        requireEditor(calendar.getSpaceId(), actorId);
        if (ics == null || ics.isBlank() || ics.length() > MAX_ICS_CHARS) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_ICS_FILE", "2MB 이하의 올바른 ICS 파일을 선택해 주세요.");
        }
        var timezone = spaces.findById(calendar.getSpaceId()).orElseThrow(() -> notFound("공간")).getTimezone();
        var blocks = eventBlocks(ics);
        if (blocks.isEmpty()) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "ICS_EVENT_NOT_FOUND", "ICS 파일에 일정이 없습니다.");
        if (blocks.size() > MAX_IMPORT_EVENTS) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "TOO_MANY_ICS_EVENTS", "한 번에 최대 200개 일정을 가져올 수 있습니다.");
        int imported = 0; int skipped = 0; var errors = new ArrayList<String>();
        for (int index = 0; index < blocks.size(); index++) {
            try {
                var properties = properties(blocks.get(index));
                var uid = normalizedUid(value(properties, "UID", UUID.randomUUID() + "@import.moaday"));
                if (events.existsByUid(uid)) { skipped++; continue; }
                var title = unescape(value(properties, "SUMMARY", "가져온 일정")).trim();
                if (title.isBlank()) title = "가져온 일정";
                if (title.length() > 120) title = title.substring(0, 120);
                var startProperty = required(properties, "DTSTART");
                var start = parseDate(startProperty, timezone);
                var allDay = isAllDay(startProperty);
                var endProperty = properties.get("DTEND");
                var end = endProperty == null ? start.plusSeconds(allDay ? 86400 : 3600) : parseDate(endProperty, timezone);
                if (!end.isAfter(start)) throw new IllegalArgumentException("종료 시각이 시작 시각보다 빠릅니다.");
                var eventZone = zone(startProperty, timezone).getId();
                var recurrence = recurrence(properties.get("RRULE"));
                var recurrenceUntil = recurrenceUntil(properties.get("RRULE"), eventZone);
                var id = UUID.randomUUID();
                var event = events.save(new CalendarEvent(id, calendarId, calendar.getSpaceId(), uid, title,
                        limited(unescape(value(properties, "DESCRIPTION", null)), 4000),
                        limited(unescape(value(properties, "LOCATION", null)), 200), null, allDay, start, end,
                        eventZone, recurrence, recurrenceUntil, actorId));
                attendees.save(new EventAttendee(event.getId(), actorId, AttendanceStatus.ACCEPTED));
                imported++;
            } catch (Exception error) {
                skipped++;
                if (errors.size() < 20) errors.add((index + 1) + "번째 일정: " + cleanMessage(error));
            }
        }
        audits.record(calendar.getSpaceId(), actorId, "CALENDAR_ICS_IMPORTED", "CALENDAR", calendarId,
                "ICS 일정 " + imported + "개 가져오기", skipped == 0 ? null : skipped + "개 건너뜀");
        return new ImportResult(imported, skipped, errors);
    }

    private void appendEvent(StringBuilder output, CalendarEvent event, EventOccurrenceException exception) {
        output.append("BEGIN:VEVENT\r\n");
        line(output, "UID:" + escape(event.getUid()));
        line(output, "DTSTAMP:" + UTC_FORMAT.format(Instant.now()));
        if (exception != null) line(output, "RECURRENCE-ID:" + UTC_FORMAT.format(exception.getOriginalStartsAt()));
        var title = exception == null ? event.getTitle() : exception.getTitle();
        var description = exception == null ? event.getDescription() : exception.getDescription();
        var location = exception == null ? event.getLocation() : exception.getLocation();
        var allDay = exception == null ? event.isAllDay() : exception.getAllDay();
        var startsAt = exception == null ? event.getStartsAt() : exception.getStartsAt();
        var endsAt = exception == null ? event.getEndsAt() : exception.getEndsAt();
        var timezone = exception == null ? event.getTimezone() : exception.getTimezone();
        line(output, "SUMMARY:" + escape(title));
        if (description != null) line(output, "DESCRIPTION:" + escape(description));
        if (location != null) line(output, "LOCATION:" + escape(location));
        if (allDay) {
            var startDate = startsAt.atZone(ZoneId.of(timezone)).toLocalDate();
            var endDate = endsAt.atZone(ZoneId.of(timezone)).toLocalDate();
            if (!endDate.isAfter(startDate)) endDate = startDate.plusDays(1);
            line(output, "DTSTART;VALUE=DATE:" + DATE_FORMAT.format(startDate));
            line(output, "DTEND;VALUE=DATE:" + DATE_FORMAT.format(endDate));
        } else {
            line(output, "DTSTART:" + UTC_FORMAT.format(startsAt));
            line(output, "DTEND:" + UTC_FORMAT.format(endsAt));
        }
        if (exception == null && event.getRecurrence() != EventRecurrence.NONE) {
            var rule = "RRULE:FREQ=" + event.getRecurrence().name();
            if (event.getRecurrenceUntil() != null) rule += ";UNTIL=" + UTC_FORMAT.format(event.getRecurrenceUntil());
            line(output, rule);
        }
        output.append("END:VEVENT\r\n");
    }

    private List<String> eventBlocks(String ics) {
        var unfolded = ics.replace("\r\n", "\n").replace('\r', '\n').replaceAll("\n[ \\t]", "");
        var result = new ArrayList<String>(); int cursor = 0;
        while ((cursor = unfolded.indexOf("BEGIN:VEVENT", cursor)) >= 0) {
            var end = unfolded.indexOf("END:VEVENT", cursor);
            if (end < 0) break;
            result.add(unfolded.substring(cursor + "BEGIN:VEVENT".length(), end));
            cursor = end + "END:VEVENT".length();
        }
        return result;
    }

    private Map<String, Property> properties(String block) {
        var result = new LinkedHashMap<String, Property>();
        for (var raw : block.split("\n")) {
            var line = raw.trim(); var separator = line.indexOf(':');
            if (separator <= 0) continue;
            var keyPart = line.substring(0, separator); var semicolon = keyPart.indexOf(';');
            var name = (semicolon < 0 ? keyPart : keyPart.substring(0, semicolon)).toUpperCase(Locale.ROOT);
            result.putIfAbsent(name, new Property(keyPart, line.substring(separator + 1)));
        }
        return result;
    }

    private Instant parseDate(Property property, String fallbackTimezone) {
        var value = property.value().trim();
        if (isAllDay(property)) return LocalDate.parse(value.substring(0, 8), DATE_FORMAT).atStartOfDay(zone(property, fallbackTimezone)).toInstant();
        if (value.endsWith("Z")) return Instant.from(UTC_FORMAT.parse(value));
        return LocalDateTime.parse(value, LOCAL_FORMAT).atZone(zone(property, fallbackTimezone)).toInstant();
    }

    private boolean isAllDay(Property property) { return property.key().toUpperCase(Locale.ROOT).contains("VALUE=DATE") || property.value().trim().matches("\\d{8}"); }
    private ZoneId zone(Property property, String fallback) {
        for (var part : property.key().split(";")) if (part.toUpperCase(Locale.ROOT).startsWith("TZID=")) return ZoneId.of(part.substring(5));
        return ZoneId.of(fallback);
    }
    private EventRecurrence recurrence(Property property) {
        if (property == null) return EventRecurrence.NONE;
        for (var part : property.value().split(";")) if (part.toUpperCase(Locale.ROOT).startsWith("FREQ=")) {
            try { return EventRecurrence.valueOf(part.substring(5).toUpperCase(Locale.ROOT)); }
            catch (Exception ignored) { return EventRecurrence.NONE; }
        }
        return EventRecurrence.NONE;
    }
    private Instant recurrenceUntil(Property property, String timezone) {
        if (property == null) return null;
        for (var part : property.value().split(";")) if (part.toUpperCase(Locale.ROOT).startsWith("UNTIL="))
            return parseDate(new Property("UNTIL", part.substring(6)), timezone);
        return null;
    }

    private String normalizedUid(String raw) {
        var value = raw == null || raw.isBlank() ? UUID.randomUUID() + "@import.moaday" : raw.trim();
        return value.length() <= 100 ? value : UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)) + "@import.moaday";
    }
    private String value(Map<String, Property> values, String key, String fallback) { return values.containsKey(key) ? values.get(key).value() : fallback; }
    private Property required(Map<String, Property> values, String key) { var result = values.get(key); if (result == null) throw new IllegalArgumentException(key + " 값이 없습니다."); return result; }
    private String limited(String value, int max) { if (value == null || value.isBlank()) return null; var clean = value.trim(); return clean.substring(0, Math.min(clean.length(), max)); }
    private String cleanMessage(Exception error) { return error.getMessage() == null || error.getMessage().isBlank() ? "형식을 읽을 수 없습니다." : error.getMessage(); }
    private String escape(String value) { return value == null ? "" : value.replace("\\", "\\\\").replace("\n", "\\n").replace(",", "\\,").replace(";", "\\;"); }
    private String unescape(String value) { return value == null ? null : value.replace("\\n", "\n").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\"); }
    private void line(StringBuilder output, String value) { output.append(value).append("\r\n"); }

    private void requireMembership(UUID spaceId, UUID actorId) { members.findBySpaceIdAndUserIdAndStatus(spaceId, actorId, "ACTIVE").orElseThrow(() -> notFound("공간")); }
    private void requireEditor(UUID spaceId, UUID actorId) { var member = members.findBySpaceIdAndUserIdAndStatus(spaceId, actorId, "ACTIVE").orElseThrow(() -> notFound("공간")); if (member.getRole() == SpaceRole.VIEWER) throw new ApiException(HttpStatus.FORBIDDEN, "CALENDAR_IMPORT_NOT_ALLOWED", "열람자는 일정을 가져올 수 없습니다."); }
    private ApiException notFound(String label) { return new ApiException(HttpStatus.NOT_FOUND, "CALENDAR_RESOURCE_NOT_FOUND", label + "을(를) 찾을 수 없습니다."); }
    private record Property(String key, String value) {}
    public record ImportResult(int imported, int skipped, List<String> errors) {}
}
