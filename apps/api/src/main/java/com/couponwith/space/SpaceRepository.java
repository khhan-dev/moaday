package com.couponwith.space;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SpaceRepository extends JpaRepository<Space, UUID> { List<Space> findByOwnerUserId(UUID ownerUserId); }
