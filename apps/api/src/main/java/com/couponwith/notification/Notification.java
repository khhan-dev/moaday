package com.couponwith.notification;

import jakarta.persistence.*;import java.time.Instant;import java.util.UUID;
@Entity @Table(name="notifications")
public class Notification {
    @Id private UUID id;@Column(name="user_id",nullable=false) private UUID userId;@Column(name="space_id") private UUID spaceId;@Column(nullable=false) private String type;@Column(nullable=false) private String title;@Column(nullable=false,length=1000) private String message;@Column(length=500) private String link;@Column(name="read_at") private Instant readAt;@Column(name="created_at",nullable=false) private Instant createdAt;
    protected Notification(){} public Notification(UUID id,UUID userId,UUID spaceId,String type,String title,String message,String link){this.id=id;this.userId=userId;this.spaceId=spaceId;this.type=type;this.title=title;this.message=message;this.link=link;this.createdAt=Instant.now();}
    public void read(){if(readAt==null)readAt=Instant.now();} public UUID getId(){return id;}public UUID getUserId(){return userId;}public UUID getSpaceId(){return spaceId;}public String getType(){return type;}public String getTitle(){return title;}public String getMessage(){return message;}public String getLink(){return link;}public Instant getReadAt(){return readAt;}public Instant getCreatedAt(){return createdAt;}
}
