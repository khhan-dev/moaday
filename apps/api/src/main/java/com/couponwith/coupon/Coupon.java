package com.couponwith.coupon;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.Duration;
import java.util.UUID;

@Entity @Table(name="coupons")
public class Coupon {
    @Id private UUID id;
    @Column(name="space_id",nullable=false) private UUID spaceId;
    @Column(name="owner_id",nullable=false) private UUID ownerId;
    @Column(nullable=false) private String title;
    @Column(nullable=false) private String brand;
    @Column(length=2000) private String description;
    @Column(name="expires_at",nullable=false) private Instant expiresAt;
    @Column(name="barcode_value",length=500) private String barcodeValue;
    @Column(name="barcode_format") private String barcodeFormat;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private CouponStatus status;
    @Column(name="claimed_by") private UUID claimedBy;
    @Column(name="claimed_at") private Instant claimedAt;
    @Column(name="used_by") private UUID usedBy;
    @Column(name="used_at") private Instant usedAt;
    @Version @Column(nullable=false) private long version;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    protected Coupon(){}
    public Coupon(UUID id,UUID spaceId,UUID ownerId,String title,String brand,String description,Instant expiresAt,String barcodeValue,String barcodeFormat){this.id=id;this.spaceId=spaceId;this.ownerId=ownerId;this.status=CouponStatus.AVAILABLE;this.version=0;this.createdAt=Instant.now();update(title,brand,description,expiresAt,barcodeValue,barcodeFormat);}
    public void update(String title,String brand,String description,Instant expiresAt,String barcodeValue,String barcodeFormat){this.title=title;this.brand=brand;this.description=description;this.expiresAt=expiresAt;this.barcodeValue=barcodeValue;this.barcodeFormat=barcodeFormat;this.updatedAt=Instant.now();}
    public void claim(UUID userId){this.status=CouponStatus.CLAIMED;this.claimedBy=userId;this.claimedAt=Instant.now();this.updatedAt=Instant.now();}
    public void release(){this.status=CouponStatus.AVAILABLE;this.claimedBy=null;this.claimedAt=null;this.updatedAt=Instant.now();}
    public void use(UUID userId){this.status=CouponStatus.USED;this.usedBy=userId;this.usedAt=Instant.now();this.updatedAt=Instant.now();}
    public boolean releaseClaimIfTimedOut(Instant now,Duration timeout){if(status!=CouponStatus.CLAIMED||claimedAt==null||claimedAt.plus(timeout).isAfter(now)||!expiresAt.isAfter(now))return false;release();this.updatedAt=now;return true;}
    public void correct(CouponStatus target,UUID actorId,Instant now){this.status=target;if(target==CouponStatus.AVAILABLE){this.claimedBy=null;this.claimedAt=null;this.usedBy=null;this.usedAt=null;}else if(target==CouponStatus.USED){this.claimedBy=null;this.claimedAt=null;this.usedBy=actorId;this.usedAt=now;}else if(target==CouponStatus.EXPIRED){this.claimedBy=null;this.claimedAt=null;this.usedBy=null;this.usedAt=null;}this.updatedAt=now;}
    public boolean expireIfNeeded(){return expireIfNeeded(Instant.now());}
    public boolean expireIfNeeded(Instant now){if((status==CouponStatus.AVAILABLE||status==CouponStatus.CLAIMED)&&!expiresAt.isAfter(now)){status=CouponStatus.EXPIRED;updatedAt=now;return true;}return false;}
    public UUID getId(){return id;} public UUID getSpaceId(){return spaceId;} public UUID getOwnerId(){return ownerId;} public String getTitle(){return title;} public String getBrand(){return brand;} public String getDescription(){return description;}
    public Instant getExpiresAt(){return expiresAt;} public String getBarcodeValue(){return barcodeValue;} public String getBarcodeFormat(){return barcodeFormat;} public CouponStatus getStatus(){return status;} public UUID getClaimedBy(){return claimedBy;} public Instant getClaimedAt(){return claimedAt;} public UUID getUsedBy(){return usedBy;} public Instant getUsedAt(){return usedAt;} public long getVersion(){return version;} public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
}
