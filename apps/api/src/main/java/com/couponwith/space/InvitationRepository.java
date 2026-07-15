package com.couponwith.space;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvitationRepository extends JpaRepository<Invitation, UUID> {
    boolean existsBySpaceIdAndEmailIgnoreCaseAndAcceptedAtIsNullAndRevokedAtIsNullAndDeclinedAtIsNullAndExpiresAtAfter(
            UUID spaceId, String email, Instant now);
    List<Invitation> findBySpaceIdOrderByCreatedAtDesc(UUID spaceId);
    List<Invitation> findByEmailIgnoreCaseAndAcceptedAtIsNullAndRevokedAtIsNullAndDeclinedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
            String email, Instant now);
    Optional<Invitation> findByTokenHash(String tokenHash);
}
