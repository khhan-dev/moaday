package com.couponwith.post;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "post_attachments")
public class PostAttachment {
    @Id private UUID id;
    @Column(name="post_id",nullable=false) private UUID postId;
    @Column(name="original_name",nullable=false) private String originalName;
    @Column(name="content_type",nullable=false) private String contentType;
    @Column(name="size_bytes",nullable=false) private long sizeBytes;
    @Column(name="storage_key",nullable=false,unique=true) private String storageKey;
    @Column(name="uploaded_by",nullable=false) private UUID uploadedBy;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    protected PostAttachment() {}
    public PostAttachment(UUID id,UUID postId,String originalName,String contentType,long sizeBytes,String storageKey,UUID uploadedBy){this.id=id;this.postId=postId;this.originalName=originalName;this.contentType=contentType;this.sizeBytes=sizeBytes;this.storageKey=storageKey;this.uploadedBy=uploadedBy;this.createdAt=Instant.now();}
    public UUID getId(){return id;} public UUID getPostId(){return postId;} public String getOriginalName(){return originalName;} public String getContentType(){return contentType;}
    public long getSizeBytes(){return sizeBytes;} public String getStorageKey(){return storageKey;} public UUID getUploadedBy(){return uploadedBy;}
}
