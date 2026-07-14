package com.couponwith.calendar;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface CalendarEventRepository extends JpaRepository<CalendarEvent, UUID> {
    List<CalendarEvent> findBySpaceIdAndStartsAtLessThanOrderByStartsAt(UUID spaceId, Instant to);
    boolean existsByCalendarId(UUID calendarId);
}
