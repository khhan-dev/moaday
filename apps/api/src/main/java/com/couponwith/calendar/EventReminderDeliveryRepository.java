package com.couponwith.calendar;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.UUID;

public interface EventReminderDeliveryRepository extends JpaRepository<EventReminderDelivery, UUID> {
    boolean existsByReminderIdAndOccurrenceStartsAtAndUserId(UUID reminderId, Instant occurrenceStartsAt, UUID userId);
}
