package com.couponwith.calendar;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventOccurrenceExceptionRepository extends JpaRepository<EventOccurrenceException, UUID> {
    List<EventOccurrenceException> findByEventIdOrderByOriginalStartsAt(UUID eventId);
    Optional<EventOccurrenceException> findByEventIdAndOriginalStartsAt(UUID eventId, Instant originalStartsAt);
    void deleteByEventIdAndOriginalStartsAt(UUID eventId, Instant originalStartsAt);
    void deleteByEventId(UUID eventId);
}
