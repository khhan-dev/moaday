package com.couponwith.calendar;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EventAttendeeRepository extends JpaRepository<EventAttendee, EventAttendeeId> {
    List<EventAttendee> findByEventIdOrderByUserId(UUID eventId);
    void deleteByEventId(UUID eventId);
}
