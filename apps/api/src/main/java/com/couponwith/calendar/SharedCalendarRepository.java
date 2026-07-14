package com.couponwith.calendar;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SharedCalendarRepository extends JpaRepository<SharedCalendar, UUID> {
    List<SharedCalendar> findBySpaceIdOrderByCreatedAt(UUID spaceId);
    boolean existsBySpaceIdAndNameIgnoreCase(UUID spaceId, String name);
}
