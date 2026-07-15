package com.couponwith.mail;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from EmailOutbox item where item.status in :statuses and item.nextAttemptAt <= :now order by item.createdAt")
    List<EmailOutbox> findDue(@Param("statuses") Collection<EmailOutboxStatus> statuses,
                              @Param("now") Instant now, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from EmailOutbox item where item.status = :status and item.lastAttemptAt <= :staleBefore")
    List<EmailOutbox> findStale(@Param("status") EmailOutboxStatus status,
                                @Param("staleBefore") Instant staleBefore);

    List<EmailOutbox> findTop100BySpaceIdOrderByCreatedAtDesc(UUID spaceId);
    List<EmailOutbox> findByInvitationIdAndStatusIn(UUID invitationId, Collection<EmailOutboxStatus> statuses);
}
