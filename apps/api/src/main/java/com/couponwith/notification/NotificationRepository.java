package com.couponwith.notification;
import org.springframework.data.jpa.repository.JpaRepository;import java.util.List;import java.util.UUID;
public interface NotificationRepository extends JpaRepository<Notification,UUID>{List<Notification> findTop100ByUserIdOrderByCreatedAtDesc(UUID userId);long countByUserIdAndReadAtIsNull(UUID userId);}
