package com.couponwith.coupon;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "coupon_images")
public class CouponImage {
    @Id private UUID id;
    @Column(name = "coupon_id", nullable = false, unique = true) private UUID couponId;
    @Column(name = "original_name", nullable = false) private String originalName;
    @Column(name = "content_type", nullable = false) private String contentType;
    @Column(name = "size_bytes", nullable = false) private long sizeBytes;
    @Column(name = "storage_key", nullable = false, unique = true) private String storageKey;
    @Column(name = "uploaded_by", nullable = false) private UUID uploadedBy;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected CouponImage() {}

    public CouponImage(UUID id, UUID couponId, String originalName, String contentType, long sizeBytes,
                       String storageKey, UUID uploadedBy) {
        this.id = id;
        this.couponId = couponId;
        this.createdAt = Instant.now();
        replace(originalName, contentType, sizeBytes, storageKey, uploadedBy);
    }

    public void replace(String originalName, String contentType, long sizeBytes, String storageKey, UUID uploadedBy) {
        this.originalName = originalName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.storageKey = storageKey;
        this.uploadedBy = uploadedBy;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getCouponId() { return couponId; }
    public String getOriginalName() { return originalName; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public String getStorageKey() { return storageKey; }
    public UUID getUploadedBy() { return uploadedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
