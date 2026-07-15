package com.couponwith.calendar;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CalendarEventRepository extends JpaRepository<CalendarEvent, UUID> {
    List<CalendarEvent> findBySpaceIdOrderByStartsAt(UUID spaceId);
    boolean existsByCalendarId(UUID calendarId);
    boolean existsByUid(String uid);
}
