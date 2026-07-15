package com.couponwith.coupon;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CouponImageRepository extends JpaRepository<CouponImage, UUID> {
    Optional<CouponImage> findByCouponId(UUID couponId);
}
