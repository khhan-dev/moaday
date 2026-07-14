package com.couponwith.calendar;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class CalendarController {
    private final CalendarService service;

    public CalendarController(CalendarService service) { this.service = service; }

    @GetMapping("/spaces/{spaceId}/calendars")
    List<CalendarService.CalendarView> listCalendars(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID spaceId) {
        return service.listCalendars(userId(jwt), spaceId);
    }

    @PostMapping("/spaces/{spaceId}/calendars")
    @ResponseStatus(HttpStatus.CREATED)
    CalendarService.CalendarView createCalendar(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID spaceId,
                                                @Valid @RequestBody CalendarRequest request) {
        return service.createCalendar(userId(jwt), spaceId, request.name(), request.color());
    }

    @PatchMapping("/calendars/{calendarId}")
    CalendarService.CalendarView updateCalendar(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID calendarId,
                                                @Valid @RequestBody CalendarRequest request) {
        return service.updateCalendar(userId(jwt), calendarId, request.name(), request.color());
    }

    @DeleteMapping("/calendars/{calendarId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteCalendar(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID calendarId) {
        service.deleteCalendar(userId(jwt), calendarId);
    }

    @GetMapping("/spaces/{spaceId}/events")
    List<CalendarService.EventOccurrenceView> listEvents(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID spaceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return service.listEvents(userId(jwt), spaceId, from, to);
    }

    @GetMapping("/events/{eventId}")
    CalendarService.EventView getEvent(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID eventId) {
        return service.getEvent(userId(jwt), eventId);
    }

    @PostMapping("/calendars/{calendarId}/events")
    @ResponseStatus(HttpStatus.CREATED)
    CalendarService.EventView createEvent(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID calendarId,
                                          @Valid @RequestBody EventRequest request) {
        return service.createEvent(userId(jwt), calendarId, request.toInput(calendarId));
    }

    @PatchMapping("/events/{eventId}")
    CalendarService.EventView updateEvent(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID eventId,
                                          @Valid @RequestBody EventRequest request) {
        return service.updateEvent(userId(jwt), eventId, request.toInput(request.calendarId()));
    }

    @DeleteMapping("/events/{eventId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteEvent(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID eventId) {
        service.deleteEvent(userId(jwt), eventId);
    }

    @PostMapping("/events/{eventId}/attendance")
    CalendarService.EventView respond(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID eventId,
                                      @Valid @RequestBody AttendanceRequest request) {
        return service.respond(userId(jwt), eventId, request.response());
    }

    private UUID userId(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }

    record CalendarRequest(@NotBlank @Size(max = 60) String name,
                           @NotBlank @Size(max = 30) String color) {}

    record EventRequest(UUID calendarId, @NotBlank @Size(max = 120) String title,
                        @Size(max = 4000) String description, @Size(max = 200) String location,
                        @Size(max = 1000) String externalUrl, boolean allDay,
                        @NotNull Instant startsAt, @NotNull Instant endsAt,
                        @NotBlank @Size(max = 64) String timezone, @NotNull EventRecurrence recurrence,
                        Instant recurrenceUntil, @Size(max = 100) Set<UUID> attendeeUserIds,
                        @Size(max = 5) Set<Integer> reminderMinutes) {
        CalendarService.EventInput toInput(UUID targetCalendarId) {
            return new CalendarService.EventInput(targetCalendarId, title, description, location, externalUrl, allDay,
                    startsAt, endsAt, timezone, recurrence, recurrenceUntil, attendeeUserIds, reminderMinutes);
        }
    }

    record AttendanceRequest(@NotNull AttendanceStatus response) {}
}
