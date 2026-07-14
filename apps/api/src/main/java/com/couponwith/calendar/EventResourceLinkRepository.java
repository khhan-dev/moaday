package com.couponwith.calendar;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EventResourceLinkRepository extends JpaRepository<EventResourceLink, UUID> {
    List<EventResourceLink> findByEventIdOrderByCreatedAt(UUID eventId);
    void deleteByEventId(UUID eventId);
}
