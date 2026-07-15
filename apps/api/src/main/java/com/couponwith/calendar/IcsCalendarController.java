package com.couponwith.calendar;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class IcsCalendarController {
    private static final MediaType TEXT_CALENDAR = MediaType.parseMediaType("text/calendar;charset=UTF-8");
    private final IcsCalendarService service;

    public IcsCalendarController(IcsCalendarService service) { this.service = service; }

    @GetMapping(value = "/spaces/{spaceId}/calendar.ics", produces = "text/calendar;charset=UTF-8")
    ResponseEntity<String> export(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID spaceId) {
        return ResponseEntity.ok().contentType(TEXT_CALENDAR)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("moaday-calendar.ics", StandardCharsets.UTF_8).build().toString())
                .body(service.exportSpace(userId(jwt), spaceId));
    }

    @PostMapping(value = "/calendars/{calendarId}/imports/ics", consumes = "text/calendar")
    IcsCalendarService.ImportResult importIcs(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID calendarId,
                                              @RequestBody String ics) {
        return service.importCalendar(userId(jwt), calendarId, ics);
    }

    private UUID userId(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
}
