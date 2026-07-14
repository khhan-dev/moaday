package com.couponwith.calendar;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EventReminderRepository extends JpaRepository<EventReminder, UUID> {
    List<EventReminder> findByEventIdOrderByMinutesBefore(UUID eventId);
    void deleteByEventId(UUID eventId);
}
