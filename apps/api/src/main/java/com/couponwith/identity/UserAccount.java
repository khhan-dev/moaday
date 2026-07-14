package com.couponwith.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserAccount {
    @Id
    private UUID id;
    @Column(nullable = false, unique = true)
    private String email;
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;
    @Column(name = "display_name", nullable = false)
    private String displayName;
    @Column(nullable = false)
    private String timezone;
    @Column(nullable = false)
    private String status;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected UserAccount() {}

    public UserAccount(UUID id, String email, String passwordHash, String displayName, String timezone) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.timezone = timezone;
        this.status = "ACTIVE";
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getDisplayName() { return displayName; }
    public String getTimezone() { return timezone; }
    public boolean isActive() { return "ACTIVE".equals(status); }
    public void updateProfile(String displayName, String timezone) { this.displayName = displayName; this.timezone = timezone; }
    public void deleteAccount(String disabledPasswordHash) { this.email = "deleted+" + id + "@moaday.local"; this.passwordHash = disabledPasswordHash; this.displayName = "탈퇴한 사용자"; this.status = "DELETED"; }
}
