package com.couponwith.identity;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {
    List<EmailVerificationToken> findByUserIdAndConsumedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from EmailVerificationToken token where token.userId = :userId and token.consumedAt is null and token.revokedAt is null order by token.createdAt desc")
    List<EmailVerificationToken> findActiveByUserIdForUpdate(@Param("userId") UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from EmailVerificationToken token where token.id = :id and token.userId = :userId and token.tokenHash = :tokenHash")
    Optional<EmailVerificationToken> findByIdAndUserIdAndTokenHashForUpdate(@Param("id") UUID id,
                                                                              @Param("userId") UUID userId,
                                                                              @Param("tokenHash") String tokenHash);

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);
}