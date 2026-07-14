package com.couponwith.post;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "posts")
public class SharedPost {
    @Id private UUID id;
    @Column(name = "space_id", nullable = false) private UUID spaceId;
    @Column(name = "author_id", nullable = false) private UUID authorId;
    @Column(nullable = false) private String title;
    @Column(nullable = false, length = 10000) private String content;
    @Column(nullable = false) private boolean pinned;
    @Column(nullable = false) private String status;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected SharedPost() {}
    public SharedPost(UUID id, UUID spaceId, UUID authorId, String title, String content) {
        this.id = id; this.spaceId = spaceId; this.authorId = authorId; this.pinned = false; this.status = "ACTIVE";
        this.createdAt = Instant.now(); update(title, content);
    }
    public void update(String title, String content) { this.title = title; this.content = content; this.updatedAt = Instant.now(); }
    public void setPinned(boolean pinned) { this.pinned = pinned; this.updatedAt = Instant.now(); }
    public void delete() { this.status = "DELETED"; this.pinned = false; this.updatedAt = Instant.now(); }
    public UUID getId() { return id; } public UUID getSpaceId() { return spaceId; } public UUID getAuthorId() { return authorId; }
    public String getTitle() { return title; } public String getContent() { return content; } public boolean isPinned() { return pinned; }
    public String getStatus() { return status; } public Instant getCreatedAt() { return createdAt; } public Instant getUpdatedAt() { return updatedAt; }
}
