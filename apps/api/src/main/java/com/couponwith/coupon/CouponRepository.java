package com.couponwith.coupon;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

public interface CouponRepository extends JpaRepository<Coupon,UUID>{
    List<Coupon> findBySpaceIdOrderByExpiresAt(UUID spaceId);
    List<Coupon> findByStatusInAndExpiresAtLessThanEqual(List<CouponStatus> statuses, Instant expiresAt);
    List<Coupon> findByStatusAndClaimedAtLessThanEqual(CouponStatus status, Instant claimedAt);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select c from Coupon c where c.id=:id") Optional<Coupon> findForUpdate(@Param("id") UUID id);
}
