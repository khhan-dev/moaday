package com.couponwith.notification;
import org.springframework.data.jpa.repository.JpaRepository;import java.util.UUID;
public interface UserPreferenceRepository extends JpaRepository<UserPreference,UUID>{}
